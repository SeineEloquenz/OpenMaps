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

interface SettingsStore {
    val routeOptions: StateFlow<RouteOptions>

    fun setRouteOptions(options: RouteOptions)
}
