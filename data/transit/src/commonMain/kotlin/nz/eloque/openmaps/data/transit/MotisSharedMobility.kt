package nz.eloque.openmaps.data.transit

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import kotlinx.serialization.Serializable
import nz.eloque.openmaps.core.model.BookingLinks
import nz.eloque.openmaps.core.model.GeoBounds
import nz.eloque.openmaps.core.model.LatLng
import nz.eloque.openmaps.core.model.SharedMobility
import nz.eloque.openmaps.core.model.SharedRental
import nz.eloque.openmaps.core.model.SharedVehicle
import nz.eloque.openmaps.core.model.SharingSnapshot
import nz.eloque.openmaps.core.model.SharingStation
import nz.eloque.openmaps.core.model.VehicleKind

@Serializable
internal data class RentalsResponseDto(
    val providerGroups: List<RentalProviderGroupDto> = emptyList(),
    val stations: List<RentalStationDto> = emptyList(),
    val vehicles: List<RentalVehicleDto> = emptyList(),
)

@Serializable
internal data class RentalProviderGroupDto(
    val id: String,
    val name: String,
)

@Serializable
internal data class RentalStationDto(
    val id: String,
    val providerGroupId: String,
    val name: String = "",
    val lat: Double,
    val lon: Double,
    val isRenting: Boolean = true,
    val numVehiclesAvailable: Int? = null,
    val formFactors: List<String> = emptyList(),
    val vehicleDocksAvailable: Map<String, Int> = emptyMap(),
    val rentalUriAndroid: String? = null,
    val rentalUriWeb: String? = null,
)

@Serializable
internal data class RentalVehicleDto(
    val id: String,
    val providerGroupId: String,
    val lat: Double,
    val lon: Double,
    val formFactor: String = "OTHER",
    val propulsionType: String = "HUMAN",
    val stationId: String = "",
    val isReserved: Boolean = false,
    val isDisabled: Boolean = false,
    val rentalUriAndroid: String? = null,
    val rentalUriWeb: String? = null,
)

/** Shared vehicles from the GBFS feeds a MOTIS server reads. */
internal class MotisSharedMobility(
    private val client: HttpClient,
    private val baseUrl: () -> String,
) : SharedMobility {
    override suspend fun available(area: GeoBounds): SharingSnapshot {
        val response: RentalsResponseDto =
            client
                .get("${baseUrl()}/v1/rentals") {
                    parameter("min", "${area.south},${area.east}")
                    parameter("max", "${area.north},${area.west}")
                    parameter("withProviders", false)
                    parameter("withZones", false)
                }.body()
        val operators = response.providerGroups.associate { it.id to it.name }

        fun operator(groupId: String) = operators[groupId] ?: groupId
        val freeVehicles = response.vehicles.filter { !it.isReserved && !it.isDisabled && it.stationId.isEmpty() }.distinctBy { it.id }
        return SharingSnapshot(
            stations =
                response.stations.filter { it.isRenting }.distinctBy { it.id }.map { station ->
                    SharingStation(
                        id = station.id,
                        name = station.name,
                        location = LatLng(station.lat, station.lon),
                        operator = operator(station.providerGroupId),
                        kinds = station.formFactors.map(::vehicleKind).toSet(),
                        available = station.numVehiclesAvailable,
                        freeDocks = station.vehicleDocksAvailable.values.takeIf { it.isNotEmpty() }?.sum(),
                        booking = booking(station.rentalUriAndroid, station.rentalUriWeb),
                    )
                },
            vehicles =
                freeVehicles.map { vehicle ->
                    SharedVehicle(
                        id = vehicle.id,
                        location = LatLng(vehicle.lat, vehicle.lon),
                        operator = operator(vehicle.providerGroupId),
                        kind = vehicleKind(vehicle.formFactor),
                        electric = vehicle.propulsionType in Electric,
                        booking = booking(vehicle.rentalUriAndroid, vehicle.rentalUriWeb),
                    )
                },
        )
    }
}

internal fun RentalDto.toSharedRental() =
    SharedRental(
        operator = systemName ?: providerGroupId.orEmpty(),
        kind = vehicleKind(formFactor),
        electric = propulsionType in Electric,
        fromStation = fromStationName?.takeIf { it.isNotBlank() },
        toStation = toStationName?.takeIf { it.isNotBlank() },
        booking = booking(rentalUriAndroid, rentalUriWeb),
    )

internal fun vehicleKind(formFactor: String?): VehicleKind =
    when (formFactor) {
        "BICYCLE" -> VehicleKind.BICYCLE
        "CARGO_BICYCLE" -> VehicleKind.CARGO_BICYCLE
        "SCOOTER_STANDING", "SCOOTER_SEATED" -> VehicleKind.SCOOTER
        "MOPED" -> VehicleKind.MOPED
        "CAR" -> VehicleKind.CAR
        else -> VehicleKind.OTHER
    }

/** MOTIS form factors for [kind], for asking routes with shared vehicles. */
internal fun formFactors(kind: VehicleKind): List<String> =
    when (kind) {
        VehicleKind.BICYCLE -> listOf("BICYCLE")
        VehicleKind.CARGO_BICYCLE -> listOf("CARGO_BICYCLE")
        VehicleKind.SCOOTER -> listOf("SCOOTER_STANDING", "SCOOTER_SEATED")
        VehicleKind.MOPED -> listOf("MOPED")
        VehicleKind.CAR -> listOf("CAR")
        VehicleKind.OTHER -> listOf("OTHER")
    }

private fun booking(
    app: String?,
    web: String?,
) = BookingLinks(app?.takeIf { it.isNotBlank() }, web?.takeIf { it.isNotBlank() })

private val Electric = setOf("ELECTRIC", "ELECTRIC_ASSIST", "HYBRID", "PLUG_IN_HYBRID", "HYDROGEN_FUEL_CELL")
