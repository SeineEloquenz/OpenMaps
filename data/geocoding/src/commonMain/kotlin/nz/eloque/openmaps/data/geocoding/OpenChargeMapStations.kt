package nz.eloque.openmaps.data.geocoding

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import nz.eloque.openmaps.core.model.Attribution
import nz.eloque.openmaps.core.model.Charger
import nz.eloque.openmaps.core.model.Connector
import nz.eloque.openmaps.core.model.GeoBounds
import nz.eloque.openmaps.core.model.LatLng
import nz.eloque.openmaps.core.model.PlaceDetails
import nz.eloque.openmaps.core.model.SearchResult
import kotlin.math.roundToInt

@Serializable
internal data class OcmStationDto(
    @SerialName("AddressInfo") val address: OcmAddressDto,
    @SerialName("OperatorInfo") val operator: OcmOperatorDto? = null,
    @SerialName("UsageCost") val usageCost: String? = null,
    @SerialName("NumberOfPoints") val points: Int? = null,
    @SerialName("Connections") val connections: List<OcmConnectionDto> = emptyList(),
    @SerialName("StatusType") val status: OcmStatusDto? = null,
)

@Serializable
internal data class OcmAddressDto(
    @SerialName("Title") val title: String? = null,
    @SerialName("AddressLine1") val street: String? = null,
    @SerialName("Town") val town: String? = null,
    @SerialName("Postcode") val postcode: String? = null,
    @SerialName("Latitude") val latitude: Double,
    @SerialName("Longitude") val longitude: Double,
)

@Serializable
internal data class OcmOperatorDto(
    @SerialName("Title") val title: String? = null,
    @SerialName("WebsiteURL") val website: String? = null,
)

@Serializable
internal data class OcmConnectionDto(
    @SerialName("ConnectionTypeID") val typeId: Int? = null,
    @SerialName("PowerKW") val powerKw: Double? = null,
    @SerialName("Quantity") val quantity: Int? = null,
)

@Serializable
internal data class OcmStatusDto(
    @SerialName("IsOperational") val operational: Boolean? = null,
)

/** Stations from Open Charge Map, which needs an API key. */
internal class OpenChargeMapStations(
    private val client: HttpClient,
    private val key: () -> String?,
) : ChargingStations {
    override val attribution = Attribution("Open Charge Map", "https://openchargemap.org")

    override val available: Boolean
        get() = key() != null

    override suspend fun stations(
        center: LatLng,
        bounds: GeoBounds,
    ): List<Pair<SearchResult.Place, PlaceDetails>> {
        val apiKey = key() ?: return emptyList()
        val stations: List<OcmStationDto> =
            client
                .get("$OPEN_CHARGE_MAP_API/v3/poi") {
                    header("X-API-Key", apiKey)
                    parameter("output", "json")
                    parameter("boundingbox", "(${bounds.north},${bounds.west}),(${bounds.south},${bounds.east})")
                    parameter("maxresults", MAX_RESULTS)
                    parameter("compact", false)
                    parameter("verbose", false)
                }.body()
        return stations
            .filter { it.status?.operational != false }
            .map { it.toPlace() to it.toDetails() }
            .sortedBy { (place, _) -> place.location.distanceTo(center) }
    }
}

const val OPEN_CHARGE_MAP_API = "https://api.openchargemap.io"
private const val MAX_RESULTS = 50

internal fun OcmStationDto.toPlace() =
    SearchResult.Place(
        osmId = null,
        name = address.title?.takeIf { it.isNotBlank() } ?: operatorName.orEmpty(),
        location = LatLng(address.latitude, address.longitude),
        area = address.town,
        category = "charging_station",
    )

internal fun OcmStationDto.toDetails(): PlaceDetails {
    val free = usageCost?.contains("free", ignoreCase = true)?.takeIf { it }
    return PlaceDetails(
        address =
            listOfNotNull(
                address.street,
                listOfNotNull(address.postcode, address.town).joinToString(" ").ifBlank {
                    null
                },
            ).joinToString(", ").ifBlank { null },
        openingHours = null,
        website = operator?.website,
        phone = null,
        wheelchair = null,
        operator = operatorName,
        free = free,
        chargers =
            connections
                .mapNotNull { connection -> ConnectorIds[connection.typeId]?.let { it to connection } }
                .groupBy({ it.first }, { it.second })
                .map { (connector, list) ->
                    Charger(connector, list.mapNotNull { it.powerKw }.maxOrNull()?.roundToInt(), list.sumOf { it.quantity ?: 1 })
                },
    )
}

/** The operator's name, without placeholders such as `(Business Owner at Location)`. */
private val OcmStationDto.operatorName: String?
    get() = operator?.title?.takeUnless { it.isBlank() || it.startsWith("(") }

/** Open Charge Map connection type ids. */
private val ConnectorIds =
    mapOf(
        1 to Connector.TYPE_1,
        2 to Connector.CHADEMO,
        25 to Connector.TYPE_2,
        1036 to Connector.TYPE_2,
        32 to Connector.CCS1,
        33 to Connector.CCS,
        27 to Connector.TESLA,
        30 to Connector.TESLA,
        28 to Connector.SCHUKO,
        16 to Connector.CEE,
    )
