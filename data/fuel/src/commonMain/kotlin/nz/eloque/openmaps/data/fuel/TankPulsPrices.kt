package nz.eloque.openmaps.data.fuel

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.Serializable
import nz.eloque.openmaps.core.model.Attribution
import nz.eloque.openmaps.core.model.Fuel
import nz.eloque.openmaps.core.model.FuelPrice
import nz.eloque.openmaps.core.model.FuelPriceSource
import nz.eloque.openmaps.core.model.GeoBounds
import nz.eloque.openmaps.core.model.LatLng
import nz.eloque.openmaps.core.model.PricedStation
import kotlin.time.Instant

@Serializable
internal data class TpStationsDto(
    val items: List<TpStationDto> = emptyList(),
)

@Serializable
internal data class TpStationDto(
    val id: String,
    val name: String? = null,
    val brandName: String? = null,
    val street: String? = null,
    val postcode: String? = null,
    val city: String? = null,
    val lat: Double,
    val lng: Double,
    // Thousandths of a euro, despite the name.
    val priceCents: Int? = null,
    val priceTs: String? = null,
)

/** German prices from the Markttransparenzstelle via TankPuls, which reads without a key within limits per IP address. */
internal class TankPulsPrices(
    private val client: HttpClient,
    private val enabled: () -> Boolean = { true },
) : FuelPriceSource {
    override val attribution = Attribution("MTS-K via Tankerkönig (CC BY 4.0) · TankPuls", "https://www.tankpuls.de")

    override val coverage = listOf(GeoBounds(47.27, 5.87, 55.06, 15.04))

    override val available: Boolean
        get() = enabled()

    override suspend fun stations(
        center: LatLng,
        area: GeoBounds,
    ): List<PricedStation> =
        coroutineScope {
            TP_FUELS
                .map { (type, fuel) -> async { stationsWith(area, type).map { it to fuel } } }
                .awaitAll()
                .flatten()
                .groupBy { (station, _) -> station.id }
                .values
                .map { priced -> priced.first().first.toStation(attribution, priced.mapNotNull { (station, fuel) -> station.price(fuel) }) }
                .filter { it.prices.isNotEmpty() }
        }

    private suspend fun stationsWith(
        area: GeoBounds,
        type: String,
    ): List<TpStationDto> =
        client
            .get("$TANKPULS_API/api/stations") {
                parameter("minLat", area.south)
                parameter("minLng", area.west)
                parameter("maxLat", area.north)
                parameter("maxLng", area.east)
                parameter("fuel", type)
                parameter("limit", MAX_RESULTS)
            }.body<TpStationsDto>()
            .items
}

internal fun TpStationDto.toStation(
    source: Attribution,
    prices: List<FuelPrice>,
) = PricedStation(
    location = LatLng(lat, lng),
    name = name,
    brand = brandName?.takeIf { it.isNotBlank() },
    address =
        listOfNotNull(street, listOfNotNull(postcode, city).joinToString(" ").ifBlank { null }).joinToString(", "),
    prices = prices,
    source = source,
)

private fun TpStationDto.price(fuel: Fuel) =
    priceCents?.takeIf { it > 0 }?.let { FuelPrice(fuel, it / 1000.0, "EUR", priceTs?.let(::timestamp)) }

private fun timestamp(value: String) =
    runCatching { Instant.parse(value.replace(' ', 'T').replace(Regex("([+-]\\d\\d)$"), "$1:00")) }.getOrNull()

private const val TANKPULS_API = "https://api.tankpuls.de"
private const val MAX_RESULTS = 1000
private val TP_FUELS = listOf("diesel" to Fuel.DIESEL, "e5" to Fuel.PETROL_95, "e10" to Fuel.E10)
