package nz.eloque.openmaps.data.fuel

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.format.char
import kotlinx.datetime.toInstant
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import nz.eloque.openmaps.core.model.Attribution
import nz.eloque.openmaps.core.model.Fuel
import nz.eloque.openmaps.core.model.FuelPrice
import nz.eloque.openmaps.core.model.FuelPriceSource
import nz.eloque.openmaps.core.model.GeoBounds
import nz.eloque.openmaps.core.model.LatLng
import nz.eloque.openmaps.core.model.PricedStation
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

@Serializable
internal data class PtSearchDto(
    @SerialName("resultado") val rows: List<PtRowDto> = emptyList(),
)

@Serializable
internal data class PtRowDto(
    @SerialName("Id") val id: Long,
    @SerialName("Marca") val brand: String? = null,
    @SerialName("Preco") val price: String? = null,
    @SerialName("Combustivel") val fuel: String? = null,
    @SerialName("DataAtualizacao") val updated: String? = null,
    @SerialName("Morada") val street: String? = null,
    @SerialName("Localidade") val town: String? = null,
    @SerialName("CodPostal") val postcode: String? = null,
    @SerialName("Latitude") val latitude: Double? = null,
    @SerialName("Longitude") val longitude: Double? = null,
)

/** Mainland Portugal's station prices from the DGEG, fetched per district. */
internal class PortugalPrices(
    private val client: HttpClient,
    private val now: () -> Instant = { Clock.System.now() },
) : FuelPriceSource {
    override val attribution = Attribution("DGEG", "https://precoscombustiveis.dgeg.gov.pt")

    override val coverage = DISTRICTS.values.toList()

    private val cache = mutableMapOf<Int, Pair<Instant, List<PricedStation>>>()

    override suspend fun stations(
        center: LatLng,
        area: GeoBounds,
    ): List<PricedStation> =
        coroutineScope {
            DISTRICTS
                .filterValues { it.intersects(area) }
                .entries
                .sortedBy { (_, bounds) -> LatLng((bounds.south + bounds.north) / 2, (bounds.west + bounds.east) / 2).distanceTo(center) }
                .take(MAX_DISTRICTS)
                .map { (id, _) -> async { district(id) } }
                .awaitAll()
                .flatten()
                .filter { it.location in area }
        }

    private suspend fun district(id: Int): List<PricedStation> {
        cache[id]?.let { (fetched, stations) -> if (now() - fetched < CACHE_TIME) return stations }
        val answer: PtSearchDto =
            client
                .get("$PORTUGAL_API/PesquisarPostos") {
                    parameter("idsTiposComb", PT_FUEL_IDS)
                    parameter("idDistrito", id)
                    parameter("qtdPorPagina", MAX_ROWS)
                    parameter("pagina", 1)
                }.body()
        return answer.toStations(attribution).also { cache[id] = now() to it }
    }
}

internal fun PtSearchDto.toStations(source: Attribution): List<PricedStation> =
    rows.groupBy { it.id }.values.mapNotNull { rows ->
        val first = rows.first()
        val location = LatLng(first.latitude ?: return@mapNotNull null, first.longitude ?: return@mapNotNull null)
        val prices =
            rows.mapNotNull { row ->
                val fuel = PT_FUELS[row.fuel] ?: return@mapNotNull null
                val price =
                    row.price?.substringBefore(' ')?.replace(',', '.')?.toDoubleOrNull()?.takeIf { it > 0 } ?: return@mapNotNull null
                FuelPrice(fuel, price, "EUR", row.updated?.let(::lisbonTime))
            }
        if (prices.isEmpty()) return@mapNotNull null
        PricedStation(
            location = location,
            name = null,
            brand = first.brand?.trim()?.takeIf { it.isNotBlank() }?.titleCase(),
            address =
                listOfNotNull(
                    first.street?.takeIf { it.isNotBlank() },
                    listOfNotNull(first.postcode, first.town).joinToString(" ").ifBlank { null },
                ).joinToString(", "),
            prices = prices,
            source = source,
        )
    }

private val PORTUGUESE_TIME =
    LocalDateTime.Format {
        year()
        char('-')
        monthNumber()
        char('-')
        dayOfMonth()
        char(' ')
        hour()
        char(':')
        minute()
    }

private fun lisbonTime(value: String) = runCatching { PORTUGUESE_TIME.parse(value).toInstant(TimeZone.of("Europe/Lisbon")) }.getOrNull()

private const val PORTUGAL_API = "https://precoscombustiveis.dgeg.gov.pt/api/PrecoComb"
private const val MAX_DISTRICTS = 4
private const val MAX_ROWS = 10000
private val CACHE_TIME = 5.minutes

private val PT_FUELS =
    mapOf(
        "Gasóleo simples" to Fuel.DIESEL,
        "Gasolina simples 95" to Fuel.PETROL_95,
        "Gasolina 98" to Fuel.PETROL_98,
        "GPL Auto" to Fuel.LPG,
        "GNC (gás natural comprimido) - €/kg" to Fuel.CNG,
    )

// Must name the same fuels as PT_FUELS, by the DGEG's ids.
private const val PT_FUEL_IDS = "2101,3201,3400,1120,1143"

// Computed from the DGEG's station positions, by its district ids, with a margin of 0.02 degrees.
private val DISTRICTS =
    mapOf(
        1 to GeoBounds(40.29, -8.77, 41.06, -8.14),
        2 to GeoBounds(37.40, -8.79, 38.31, -7.20),
        3 to GeoBounds(41.33, -8.81, 41.74, -7.90),
        4 to GeoBounds(41.07, -7.36, 41.86, -6.25),
        5 to GeoBounds(39.55, -8.21, 40.39, -6.99),
        6 to GeoBounds(40.00, -8.87, 40.48, -7.78),
        7 to GeoBounds(38.19, -8.60, 38.97, -7.31),
        8 to GeoBounds(36.99, -8.96, 37.46, -7.39),
        9 to GeoBounds(40.26, -7.77, 41.10, -6.81),
        10 to GeoBounds(39.23, -9.39, 40.04, -8.13),
        11 to GeoBounds(38.66, -9.49, 39.31, -8.85),
        12 to GeoBounds(38.85, -8.26, 39.53, -7.03),
        13 to GeoBounds(41.01, -8.80, 41.47, -7.93),
        14 to GeoBounds(38.84, -8.97, 39.80, -7.85),
        15 to GeoBounds(37.78, -9.25, 38.77, -8.20),
        16 to GeoBounds(41.62, -8.88, 42.13, -8.17),
        17 to GeoBounds(41.14, -7.98, 41.91, -7.24),
        18 to GeoBounds(40.33, -8.33, 41.18, -7.38),
    )
