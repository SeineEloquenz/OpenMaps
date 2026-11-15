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

    /** Base URL of a self-hosted MOTIS API, or null for Transitous. */
    val transitUrl: StateFlow<String?>

    fun setTransitUrl(url: String?)

    /** Base URL of a self-hosted Nominatim, or null for the public one. */
    val placeDetailsUrl: StateFlow<String?>

    fun setPlaceDetailsUrl(url: String?)

    /** Base URL of a self-hosted Photon, or null for the public one. */
    val categorySearchUrl: StateFlow<String?>

    fun setCategorySearchUrl(url: String?)

    /** The user's own Open Charge Map API key, or null to use OpenStreetMap for charging stations. */
    val openChargeMapKey: StateFlow<String?>

    fun setOpenChargeMapKey(key: String?)
}
