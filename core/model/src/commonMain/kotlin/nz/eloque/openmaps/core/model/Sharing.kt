package nz.eloque.openmaps.core.model

enum class VehicleKind { BICYCLE, CARGO_BICYCLE, SCOOTER, MOPED, CAR, OTHER }

/** Where to book a shared vehicle: in the operator's app, or on its website when the app is not installed. */
data class BookingLinks(
    val app: String?,
    val web: String?,
)

/** A place where shared vehicles are picked up and, for most operators, returned. */
data class SharingStation(
    val id: String,
    val name: String,
    val location: LatLng,
    val operator: String,
    val kinds: Set<VehicleKind>,
    /** Vehicles ready to rent, or null when the operator does not say. */
    val available: Int?,
    /** Free places to return a vehicle, or null when the station has no docks or the operator does not say. */
    val freeDocks: Int?,
    val booking: BookingLinks,
)

/** A shared vehicle parked freely, not at a station. */
data class SharedVehicle(
    val id: String,
    val location: LatLng,
    val operator: String,
    val kind: VehicleKind,
    val electric: Boolean,
    val booking: BookingLinks,
)

data class SharingSnapshot(
    val stations: List<SharingStation>,
    val vehicles: List<SharedVehicle>,
)

/** The shared vehicle used on a leg of a route. */
data class SharedRental(
    val operator: String,
    val kind: VehicleKind,
    val electric: Boolean,
    val fromStation: String?,
    val toStation: String?,
    val booking: BookingLinks,
)

interface SharedMobility {
    /** Stations and freely parked vehicles available in [area] now. */
    suspend fun available(area: GeoBounds): SharingSnapshot
}
