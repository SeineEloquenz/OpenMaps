package nz.eloque.openmaps.data.fuel

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull
import nz.eloque.openmaps.core.model.Attribution
import nz.eloque.openmaps.core.model.Fuel
import nz.eloque.openmaps.core.model.FuelPrice
import nz.eloque.openmaps.core.model.FuelPriceSource
import nz.eloque.openmaps.core.model.GeoBounds
import nz.eloque.openmaps.core.model.LatLng
import nz.eloque.openmaps.core.model.PricedStation
import kotlin.math.max
import kotlin.math.min

@Serializable
internal data class TkListDto(
    val ok: Boolean = false,
    val stations: List<TkStationDto> = emptyList(),
)

@Serializable
internal data class TkStationDto(
    val name: String? = null,
    val brand: String? = null,
    val street: String? = null,
    val houseNumber: String? = null,
    val postCode: JsonElement? = null,
    val place: String? = null,
    val lat: Double,
    val lng: Double,
    // Numbers, or false when the station does not sell the fuel.
    val diesel: JsonElement? = null,
    val e5: JsonElement? = null,
    val e10: JsonElement? = null,
)

/** German prices from the Markttransparenzstelle via Tankerkönig, which needs an API key. */
internal class TankerkoenigPrices(
    private val client: HttpClient,
    private val key: () -> String?,
) : FuelPriceSource {
    override val attribution = Attribution("Tankerkönig (MTS-K)", "https://creativecommons.tankerkoenig.de")

    override val coverage = listOf(GeoBounds(47.27, 5.87, 55.06, 15.04))

    override val available: Boolean
        get() = key() != null

    override suspend fun stations(
        center: LatLng,
        area: GeoBounds,
    ): List<PricedStation> {
        val apiKey = key() ?: return emptyList()
        val corners = listOf(LatLng(area.south, area.west), LatLng(area.north, area.east))
        val radiusKm = min(MAX_RADIUS_KM, max(MIN_RADIUS_KM, corners.maxOf { it.distanceTo(center) } / 1000))
        val list: TkListDto =
            client
                .get("$TANKERKOENIG_API/list.php") {
                    parameter("lat", center.latitude)
                    parameter("lng", center.longitude)
                    parameter("rad", radiusKm)
                    parameter("sort", "dist")
                    parameter("type", "all")
                    parameter("apikey", apiKey)
                }.body()
        return list.stations.map { it.toStation(attribution) }.filter { it.prices.isNotEmpty() && it.location in area }
    }
}

internal fun TkStationDto.toStation(source: Attribution) =
    PricedStation(
        location = LatLng(lat, lng),
        name = name,
        brand = brand?.takeIf { it.isNotBlank() },
        address =
            listOfNotNull(
                listOfNotNull(street?.titleCase(), houseNumber?.trim()).joinToString(" ").ifBlank { null },
                listOfNotNull((postCode as? JsonPrimitive)?.content, place?.titleCase()).joinToString(" ").ifBlank { null },
            ).joinToString(", "),
        prices =
            listOfNotNull(
                diesel.price(Fuel.DIESEL),
                e5.price(Fuel.PETROL_95),
                e10.price(Fuel.E10),
            ),
        source = source,
    )

private fun JsonElement?.price(fuel: Fuel) =
    (this as? JsonPrimitive)?.takeIf { !it.isString }?.doubleOrNull?.takeIf { it > 0 }?.let { FuelPrice(fuel, it, "EUR", null) }

private fun String.titleCase() = lowercase().split(" ").joinToString(" ") { word -> word.replaceFirstChar { it.uppercaseChar() } }

private const val TANKERKOENIG_API = "https://creativecommons.tankerkoenig.de/json"
private const val MIN_RADIUS_KM = 1.0
private const val MAX_RADIUS_KM = 25.0
