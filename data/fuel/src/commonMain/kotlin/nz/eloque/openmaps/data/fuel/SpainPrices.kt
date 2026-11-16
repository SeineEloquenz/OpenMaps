package nz.eloque.openmaps.data.fuel

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.http.HttpHeaders
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
internal data class EsProvinceDto(
    @SerialName("Fecha") val date: String? = null,
    @SerialName("ListaEESSPrecio") val stations: List<Map<String, String>> = emptyList(),
)

/** The Spanish ministry's station prices, fetched per province because the whole country is 12 MB. */
internal class SpainPrices(
    private val client: HttpClient,
    private val now: () -> Instant = { Clock.System.now() },
) : FuelPriceSource {
    override val attribution =
        Attribution("Ministerio para la Transición Ecológica y el Reto Demográfico", "https://geoportalgasolineras.es")

    override val coverage = PROVINCES.values.toList()

    private val cache = mutableMapOf<String, Pair<Instant, List<PricedStation>>>()

    override suspend fun stations(
        center: LatLng,
        area: GeoBounds,
    ): List<PricedStation> =
        coroutineScope {
            PROVINCES
                .filterValues { it.intersects(area) }
                .entries
                .sortedBy { (_, bounds) -> LatLng((bounds.south + bounds.north) / 2, (bounds.west + bounds.east) / 2).distanceTo(center) }
                .take(MAX_PROVINCES)
                .map { (id, _) -> async { province(id) } }
                .awaitAll()
                .flatten()
                .filter { it.location in area }
        }

    private suspend fun province(id: String): List<PricedStation> {
        cache[id]?.let { (fetched, stations) -> if (now() - fetched < CACHE_TIME) return stations }
        val answer: EsProvinceDto =
            client
                .get("$SPAIN_API/EstacionesTerrestres/FiltroProvincia/$id") { header(HttpHeaders.Accept, "application/json") }
                .body()
        return answer.toStations(attribution).also { cache[id] = now() to it }
    }
}

internal fun EsProvinceDto.toStations(source: Attribution): List<PricedStation> {
    val updated = date?.let(::madridTime)
    return stations.mapNotNull { station ->
        val location =
            LatLng(
                station.number("Latitud") ?: return@mapNotNull null,
                station.number("Longitud (WGS84)") ?: return@mapNotNull null,
            )
        if (PROVINCES.values.none { location in it }) return@mapNotNull null
        val prices =
            ES_FUELS.mapNotNull { (field, fuel) ->
                station.number(field)?.takeIf { it > 0 }?.let { FuelPrice(fuel, it, "EUR", updated) }
            }
        if (prices.isEmpty()) return@mapNotNull null
        PricedStation(
            location = location,
            name = null,
            brand = station["Rótulo"]?.takeIf { it.isNotBlank() }?.titleCase(),
            address =
                listOfNotNull(
                    station["Dirección"]?.takeIf { it.isNotBlank() }?.titleCase(),
                    listOfNotNull(station["C.P."], station["Localidad"]?.titleCase()).joinToString(" ").ifBlank { null },
                ).joinToString(", "),
            prices = prices,
            source = source,
        )
    }
}

private fun Map<String, String>.number(field: String) = this[field]?.replace(',', '.')?.toDoubleOrNull()

private val SPANISH_TIME =
    LocalDateTime.Format {
        dayOfMonth()
        char('/')
        monthNumber()
        char('/')
        year()
        char(' ')
        hour()
        char(':')
        minute()
        char(':')
        second()
    }

private fun madridTime(value: String) = runCatching { SPANISH_TIME.parse(value).toInstant(TimeZone.of("Europe/Madrid")) }.getOrNull()

private const val SPAIN_API = "https://sedeaplicaciones.minetur.gob.es/ServiciosRESTCarburantes/PreciosCarburantes"
private const val MAX_PROVINCES = 4
private val CACHE_TIME = 5.minutes

private val ES_FUELS =
    listOf(
        "Precio Gasoleo A" to Fuel.DIESEL,
        "Precio Gasolina 95 E5" to Fuel.PETROL_95,
        "Precio Gasolina 95 E10" to Fuel.E10,
        "Precio Gasolina 98 E5" to Fuel.PETROL_98,
        "Precio Gasolina 95 E85" to Fuel.E85,
        "Precio Gases licuados del petróleo" to Fuel.LPG,
        "Precio Gas Natural Comprimido" to Fuel.CNG,
    )

// Computed from the ministry's station positions, by its province ids, with a margin of 0.02 degrees.
private val PROVINCES =
    mapOf(
        "01" to GeoBounds(42.47, -3.14, 43.17, -2.29),
        "02" to GeoBounds(38.13, -2.77, 39.39, -0.92),
        "03" to GeoBounds(37.84, -1.07, 38.88, 0.21),
        "04" to GeoBounds(36.69, -3.02, 37.88, -1.64),
        "05" to GeoBounds(40.12, -5.36, 41.15, -4.31),
        "06" to GeoBounds(38.01, -7.19, 39.38, -4.93),
        "07" to GeoBounds(38.69, 1.28, 40.03, 4.29),
        "08" to GeoBounds(41.18, 1.38, 42.27, 2.78),
        "09" to GeoBounds(41.55, -4.27, 43.19, -2.68),
        "10" to GeoBounds(39.11, -7.32, 40.40, -5.18),
        "11" to GeoBounds(36.00, -6.45, 36.95, -5.14),
        "12" to GeoBounds(39.72, -0.72, 40.68, 0.50),
        "13" to GeoBounds(38.41, -4.99, 39.48, -2.67),
        "14" to GeoBounds(37.21, -5.46, 38.59, -4.08),
        "15" to GeoBounds(42.53, -9.29, 43.75, -7.83),
        "16" to GeoBounds(39.31, -3.10, 40.45, -1.25),
        "17" to GeoBounds(41.66, 1.90, 42.48, 3.30),
        "18" to GeoBounds(36.68, -4.30, 38.00, -2.31),
        "19" to GeoBounds(40.28, -3.47, 41.23, -1.66),
        "20" to GeoBounds(42.93, -2.56, 43.38, -1.73),
        "21" to GeoBounds(37.00, -7.43, 38.16, -6.20),
        "22" to GeoBounds(41.39, -0.81, 42.79, 0.75),
        "23" to GeoBounds(37.39, -4.28, 38.43, -2.54),
        "24" to GeoBounds(42.06, -6.94, 43.03, -4.96),
        "25" to GeoBounds(41.35, 0.37, 42.85, 1.87),
        "26" to GeoBounds(41.96, -3.03, 42.60, -1.71),
        "27" to GeoBounds(42.45, -7.95, 43.71, -7.00),
        "28" to GeoBounds(40.00, -4.50, 41.15, -3.09),
        "29" to GeoBounds(36.31, -5.36, 37.29, -3.86),
        "30" to GeoBounds(37.37, -1.99, 38.64, -0.69),
        "31" to GeoBounds(41.91, -2.39, 43.33, -0.92),
        "32" to GeoBounds(41.81, -8.27, 42.53, -6.96),
        "33" to GeoBounds(43.02, -7.17, 43.63, -4.54),
        "34" to GeoBounds(41.77, -4.99, 42.91, -4.06),
        "35" to GeoBounds(27.73, -15.82, 29.17, -13.44),
        "36" to GeoBounds(41.89, -8.90, 42.81, -7.92),
        "37" to GeoBounds(40.32, -6.84, 41.26, -5.18),
        "38" to GeoBounds(27.69, -18.03, 28.85, -16.19),
        "39" to GeoBounds(42.79, -4.63, 43.51, -3.13),
        "40" to GeoBounds(40.69, -4.72, 41.46, -3.36),
        "41" to GeoBounds(36.83, -6.34, 38.11, -4.66),
        "42" to GeoBounds(41.15, -3.21, 42.05, -1.91),
        "43" to GeoBounds(40.51, 0.26, 41.56, 1.64),
        "44" to GeoBounds(40.04, -1.67, 41.24, 0.23),
        "45" to GeoBounds(39.33, -5.35, 40.26, -2.97),
        "46" to GeoBounds(38.72, -1.46, 40.15, -0.05),
        "47" to GeoBounds(41.16, -5.35, 42.18, -4.09),
        "48" to GeoBounds(42.97, -3.38, 43.44, -2.48),
        "49" to GeoBounds(41.15, -6.86, 42.11, -5.30),
        "50" to GeoBounds(41.10, -2.14, 42.51, 0.27),
        "51" to GeoBounds(35.86, -5.36, 35.91, -5.28),
        "52" to GeoBounds(35.25, -2.97, 35.32, -2.91),
    )
