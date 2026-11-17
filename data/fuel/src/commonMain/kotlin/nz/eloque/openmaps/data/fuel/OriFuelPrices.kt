package nz.eloque.openmaps.data.fuel

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.ClientRequestException
import io.ktor.client.request.get
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import nz.eloque.openmaps.core.model.Attribution
import nz.eloque.openmaps.core.model.Fuel
import nz.eloque.openmaps.core.model.FuelPrice
import nz.eloque.openmaps.core.model.FuelPriceSource
import nz.eloque.openmaps.core.model.GeoBounds
import nz.eloque.openmaps.core.model.LatLng
import nz.eloque.openmaps.core.model.PricedStation
import kotlin.math.floor
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

@Serializable
internal data class OfpIndexDto(
    @SerialName("cell_degrees") val cellDegrees: Double,
    val sources: List<OfpSourceDto> = emptyList(),
)

@Serializable
internal data class OfpSourceDto(
    val id: String,
    val attribution: String,
    @SerialName("attribution_url") val attributionUrl: String,
    /** South, west, north, east. */
    val bounds: List<Double>,
)

@Serializable
internal data class OfpCellDto(
    val stations: List<OfpStationDto> = emptyList(),
)

@Serializable
internal data class OfpStationDto(
    val source: String,
    val lat: Double,
    val lon: Double,
    val name: String? = null,
    val brand: String? = null,
    val address: String? = null,
    val prices: List<OfpPriceDto> = emptyList(),
)

@Serializable
internal data class OfpPriceDto(
    val fuel: String,
    val price: Double,
    val currency: String,
    val updated: Instant? = null,
)

/** Prices from an open-fuel-prices server, which collects the countries that publish whole-country files. */
internal class OriFuelPrices(
    private val client: HttpClient,
    private val baseUrl: () -> String?,
    private val now: () -> Instant = { Clock.System.now() },
) : FuelPriceSource {
    override val attribution = Attribution("open-fuel-prices", "https://github.com/SeineEloquenz/open-fuel-prices")

    // The server says which countries it covers, so areas are checked against its index instead.
    override val coverage = listOf(GeoBounds(-90.0, -180.0, 90.0, 180.0))

    override val available get() = !baseUrl().isNullOrBlank()

    private val lock = Mutex()
    private var index: Triple<String, Instant, OfpIndexDto>? = null
    private val cells = mutableMapOf<String, Pair<Instant, List<OfpStationDto>>>()

    override suspend fun stations(
        center: LatLng,
        area: GeoBounds,
    ): List<PricedStation> {
        val base = baseUrl()?.takeIf { it.isNotBlank() }?.trimEnd('/') ?: return emptyList()
        val index = index(base)
        val sources =
            index.sources
                .filter { it.bounds.size == 4 && GeoBounds(it.bounds[0], it.bounds[1], it.bounds[2], it.bounds[3]).intersects(area) }
                .associateBy { it.id }
        if (sources.isEmpty()) return emptyList()
        return coroutineScope {
            cellsCovering(area, index.cellDegrees)
                .sortedBy { (x, y) -> cellCenter(x, y, index.cellDegrees).distanceTo(center) }
                .take(MAX_CELLS)
                .map { (x, y) -> async { cell(base, x, y) } }
                .awaitAll()
                .flatten()
        }.mapNotNull { it.toStation(sources) }
            .filter { it.location in area }
            .sortedBy { it.location.distanceTo(center) }
    }

    private suspend fun index(base: String): OfpIndexDto {
        lock.withLock { index?.let { (url, fetched, value) -> if (url == base && now() - fetched < INDEX_TIME) return value } }
        val fresh: OfpIndexDto = client.get("$base/v1/sources.json").body()
        lock.withLock { index = Triple(base, now(), fresh) }
        return fresh
    }

    private suspend fun cell(
        base: String,
        x: Int,
        y: Int,
    ): List<OfpStationDto> {
        val url = "$base/v1/cells/$x/$y.json"
        lock.withLock { cells[url]?.let { (fetched, stations) -> if (now() - fetched < CELL_TIME) return stations } }
        val stations =
            try {
                client.get(url).body<OfpCellDto>().stations
            } catch (e: ClientRequestException) {
                if (e.response.status != HttpStatusCode.NotFound) throw e
                emptyList()
            }
        lock.withLock {
            if (cells.size >= MAX_CACHED_CELLS) cells.clear()
            cells[url] = now() to stations
        }
        return stations
    }
}

private fun OfpStationDto.toStation(sources: Map<String, OfpSourceDto>): PricedStation? {
    val source = sources[source] ?: return null
    val prices =
        prices.mapNotNull { price ->
            OFP_FUELS[price.fuel]?.let { FuelPrice(it, price.price, price.currency, price.updated) }
        }
    if (prices.isEmpty()) return null
    return PricedStation(
        location = LatLng(lat, lon),
        name = name,
        brand = brand,
        address = address,
        prices = prices,
        source = Attribution(source.attribution, source.attributionUrl),
    )
}

/** Cells as the server numbers them, counted from 180° west and 90° south. */
internal fun cellsCovering(
    area: GeoBounds,
    degrees: Double,
): List<Pair<Int, Int>> {
    fun index(value: Double) = floor(value / degrees).toInt()
    val xs = index(area.west + 180)..index(area.east + 180)
    val ys = index(area.south + 90)..index(area.north + 90)
    return xs.flatMap { x -> ys.map { y -> x to y } }
}

private fun cellCenter(
    x: Int,
    y: Int,
    degrees: Double,
) = LatLng((y + 0.5) * degrees - 90, (x + 0.5) * degrees - 180)

private val OFP_FUELS =
    mapOf(
        "diesel" to Fuel.DIESEL,
        "petrol_95" to Fuel.PETROL_95,
        "e10" to Fuel.E10,
        "petrol_98" to Fuel.PETROL_98,
        "e85" to Fuel.E85,
        "lpg" to Fuel.LPG,
        "cng" to Fuel.CNG,
        "regular" to Fuel.REGULAR,
        "premium" to Fuel.PREMIUM,
    )

private const val MAX_CELLS = 9
private const val MAX_CACHED_CELLS = 64
private val INDEX_TIME = 1.hours
private val CELL_TIME = 5.minutes
