package nz.eloque.openmaps.core.model

import kotlinx.coroutines.flow.StateFlow

/** Kinds of public transport a route may use, as offered to the user. */
enum class ModeGroup(
    val modes: Set<TransportMode>,
) {
    LONG_DISTANCE_TRAIN(setOf(TransportMode.HIGHSPEED_RAIL, TransportMode.LONG_DISTANCE_RAIL, TransportMode.NIGHT_RAIL)),
    REGIONAL_TRAIN(setOf(TransportMode.REGIONAL_RAIL, TransportMode.SUBURBAN)),
    SUBWAY_TRAM(setOf(TransportMode.SUBWAY, TransportMode.TRAM)),
    BUS(setOf(TransportMode.BUS, TransportMode.COACH)),
    FERRY(setOf(TransportMode.FERRY)),
}

enum class WalkingSpeed { SLOW, NORMAL, FAST }

/** How the user wants to travel, one tab each in directions. */
enum class TravelMode { TRANSIT, CAR, WALK, BIKE }

data class RouteOptions(
    val modes: Set<ModeGroup> = ModeGroup.entries.toSet(),
    /** Null for no limit. */
    val maxTransfers: Int? = null,
    val walkingSpeed: WalkingSpeed = WalkingSpeed.NORMAL,
    val wheelchair: Boolean = false,
    /** Whether shared bikes and scooters may take the user to and from public transport. */
    val sharedVehicles: Boolean = false,
) {
    val isDefault: Boolean
        get() = this == RouteOptions()
}

enum class DistanceUnit { METRIC, IMPERIAL }

interface SettingsStore {
    val routeOptions: StateFlow<RouteOptions>

    fun setRouteOptions(options: RouteOptions)

    /** The directions tab used last. */
    val travelMode: StateFlow<TravelMode>

    fun setTravelMode(mode: TravelMode)

    val distanceUnit: StateFlow<DistanceUnit>

    fun setDistanceUnit(unit: DistanceUnit)

    /** Whether navigation plays a sound when driving clearly above the speed limit. */
    val speedingAlert: StateFlow<Boolean>

    fun setSpeedingAlert(enabled: Boolean)

    /** The fuel whose price gas station results show, null until the user picks one. */
    val preferredFuel: StateFlow<Fuel?>

    fun setPreferredFuel(fuel: Fuel?)

    /** The user's value for the [ServiceSetting] with [id], or null to use its default. */
    fun serviceValue(id: String): StateFlow<String?>

    /** Blank values reset the setting to its default. */
    fun setServiceValue(
        id: String,
        value: String?,
    )
}
