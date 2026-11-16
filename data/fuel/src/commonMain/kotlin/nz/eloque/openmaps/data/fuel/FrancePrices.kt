package nz.eloque.openmaps.data.fuel

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
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

@Serializable
internal data class FrRecordsDto(
    val results: List<FrStationDto> = emptyList(),
)

@Serializable
internal data class FrStationDto(
    val geom: FrPointDto? = null,
    val adresse: String? = null,
    val ville: String? = null,
    val cp: String? = null,
    @SerialName("gazole_prix") val diesel: Double? = null,
    @SerialName("gazole_maj") val dieselUpdated: String? = null,
    @SerialName("sp95_prix") val sp95: Double? = null,
    @SerialName("sp95_maj") val sp95Updated: String? = null,
    @SerialName("e10_prix") val e10: Double? = null,
    @SerialName("e10_maj") val e10Updated: String? = null,
    @SerialName("sp98_prix") val sp98: Double? = null,
    @SerialName("sp98_maj") val sp98Updated: String? = null,
    @SerialName("e85_prix") val e85: Double? = null,
    @SerialName("e85_maj") val e85Updated: String? = null,
    @SerialName("gplc_prix") val lpg: Double? = null,
    @SerialName("gplc_maj") val lpgUpdated: String? = null,
)

@Serializable
internal data class FrPointDto(
    val lat: Double,
    val lon: Double,
)

/** The French government's instant price feed, open under the Licence Ouverte 2.0. */
internal class FrancePrices(
    private val client: HttpClient,
) : FuelPriceSource {
    override val attribution = Attribution("prix-carburants.gouv.fr", "https://www.prix-carburants.gouv.fr")

    override val coverage = listOf(GeoBounds(41.3, -5.2, 51.1, 9.6))

    override suspend fun stations(
        center: LatLng,
        area: GeoBounds,
    ): List<PricedStation> {
        val records: FrRecordsDto =
            client
                .get(FRANCE_RECORDS) {
                    parameter("where", "in_bbox(geom, ${area.south}, ${area.west}, ${area.north}, ${area.east})")
                    parameter("order_by", "distance(geom, geom'POINT(${center.longitude} ${center.latitude})')")
                    parameter("select", FIELDS)
                    parameter("limit", MAX_RESULTS)
                }.body()
        return records.results.mapNotNull { it.toStation(attribution) }
    }
}

internal fun FrStationDto.toStation(source: Attribution): PricedStation? {
    val point = geom ?: return null
    val prices =
        listOfNotNull(
            price(Fuel.DIESEL, diesel, dieselUpdated),
            price(Fuel.PETROL_95, sp95, sp95Updated),
            price(Fuel.E10, e10, e10Updated),
            price(Fuel.PETROL_98, sp98, sp98Updated),
            price(Fuel.E85, e85, e85Updated),
            price(Fuel.LPG, lpg, lpgUpdated),
        )
    if (prices.isEmpty()) return null
    return PricedStation(
        location = LatLng(point.lat, point.lon),
        name = null,
        brand = null,
        address =
            listOfNotNull(
                adresse?.titleCase(),
                listOfNotNull(cp, ville?.titleCase()).joinToString(" ").ifBlank {
                    null
                },
            ).joinToString(", "),
        prices = prices,
        source = source,
    )
}

private fun price(
    fuel: Fuel,
    value: Double?,
    updated: String?,
) = value?.let { FuelPrice(fuel, it, "EUR", updated?.let(::parisTime)) }

// The feed marks French local time as UTC.
private fun parisTime(value: String) =
    runCatching { LocalDateTime.parse(value.take(LOCAL_DATE_TIME_LENGTH)).toInstant(TimeZone.of("Europe/Paris")) }.getOrNull()

private fun String.titleCase() = lowercase().split(" ").joinToString(" ") { word -> word.replaceFirstChar { it.uppercaseChar() } }

private const val FRANCE_RECORDS =
    "https://data.economie.gouv.fr/api/explore/v2.1/catalog/datasets/prix-des-carburants-en-france-flux-instantane-v2/records"
private const val FIELDS =
    "geom,adresse,ville,cp,gazole_prix,gazole_maj,sp95_prix,sp95_maj,e10_prix,e10_maj,sp98_prix,sp98_maj,e85_prix,e85_maj,gplc_prix,gplc_maj"
private const val MAX_RESULTS = 100
private const val LOCAL_DATE_TIME_LENGTH = 19
