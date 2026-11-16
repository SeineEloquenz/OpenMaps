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

@Serializable
internal data class AtStationDto(
    val id: Long,
    val name: String? = null,
    val location: AtLocationDto,
    val prices: List<AtPriceDto> = emptyList(),
)

@Serializable
internal data class AtLocationDto(
    val address: String? = null,
    val postalCode: String? = null,
    val city: String? = null,
    val latitude: Double,
    val longitude: Double,
)

@Serializable
internal data class AtPriceDto(
    val fuelType: String,
    val amount: Double? = null,
)

/** E-Control's Spritpreisrechner, which only tells the cheapest open stations near a point, one fuel at a time. */
internal class AustriaPrices(
    private val client: HttpClient,
) : FuelPriceSource {
    override val attribution = Attribution("E-Control Spritpreisrechner", "https://www.spritpreisrechner.at")

    override val coverage = listOf(GeoBounds(46.37, 9.53, 49.02, 17.17))

    override suspend fun stations(
        center: LatLng,
        area: GeoBounds,
    ): List<PricedStation> =
        coroutineScope {
            FUEL_TYPES
                .map { type -> async { cheapest(center, type) } }
                .awaitAll()
                .flatten()
                .groupBy { it.id }
                .values
                .map { it.first().copy(prices = it.flatMap(AtStationDto::prices)).toStation(attribution) }
                .filter { it.prices.isNotEmpty() }
        }

    private suspend fun cheapest(
        center: LatLng,
        type: String,
    ): List<AtStationDto> =
        client
            .get("$AUSTRIA_API/search/gas-stations/by-address") {
                parameter("latitude", center.latitude)
                parameter("longitude", center.longitude)
                parameter("fuelType", type)
                parameter("includeClosed", false)
            }.body()
}

internal fun AtStationDto.toStation(source: Attribution) =
    PricedStation(
        location = LatLng(location.latitude, location.longitude),
        name = name,
        brand = null,
        address =
            listOfNotNull(
                location.address,
                listOfNotNull(location.postalCode, location.city).joinToString(" ").ifBlank {
                    null
                },
            ).joinToString(", "),
        prices =
            prices.mapNotNull { price ->
                price.amount?.let { amount -> AT_FUELS[price.fuelType]?.let { FuelPrice(it, amount, "EUR", null) } }
            },
        source = source,
    )

private const val AUSTRIA_API = "https://api.e-control.at/sprit/1.0"
private val AT_FUELS = mapOf("DIE" to Fuel.DIESEL, "SUP" to Fuel.PETROL_95, "GAS" to Fuel.CNG)
private val FUEL_TYPES = AT_FUELS.keys.toList()
