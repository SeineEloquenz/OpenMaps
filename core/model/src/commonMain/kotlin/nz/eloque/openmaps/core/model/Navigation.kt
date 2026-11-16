package nz.eloque.openmaps.core.model

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlin.time.Duration

enum class ManeuverType {
    DEPART,
    ARRIVE,
    STRAIGHT,
    SLIGHT_LEFT,
    LEFT,
    SHARP_LEFT,
    SLIGHT_RIGHT,
    RIGHT,
    SHARP_RIGHT,
    U_TURN,
    ROUNDABOUT,
    OTHER,
}

enum class LaneDirection { SHARP_LEFT, LEFT, SLIGHT_LEFT, STRAIGHT, SLIGHT_RIGHT, RIGHT, SHARP_RIGHT, U_TURN }

/** One lane at a maneuver, left to right. */
data class Lane(
    val directions: List<LaneDirection>,
    /** True when the lane leads along the route. */
    val valid: Boolean,
    /** True for the lanes best to take. */
    val recommended: Boolean,
    /** The direction to follow in a valid lane. */
    val chosen: LaneDirection?,
)

/** What to do at the next maneuver, as shown on screen. */
data class NavigationInstruction(
    val text: String,
    val secondary: String?,
    val maneuver: ManeuverType,
    /** The exit to take at a roundabout. */
    val exit: Int?,
    /** Empty where the map has no lane data. */
    val lanes: List<Lane> = emptyList(),
)

enum class NavigationStatus { NAVIGATING, REROUTING, OFF_ROUTE, ARRIVED }

data class NavigationState(
    val mode: TransportMode,
    val route: List<LatLng>,
    /** The user's position matched onto [route], null before the first fix. */
    val position: LatLng?,
    /** Direction of travel in degrees from north. */
    val bearing: Double?,
    val travelledMeters: Double,
    val instruction: NavigationInstruction?,
    val metersToManeuver: Double,
    /** The maneuver after [instruction]. */
    val then: NavigationInstruction?,
    val remainingMeters: Double,
    val remainingTime: Duration,
    val status: NavigationStatus,
    /** In km/h, null where unknown or unlimited. */
    val speedLimit: Int? = null,
    /** In metres per second, from the last fix. */
    val speed: Double? = null,
) {
    val speeding: Boolean
        get() = isSpeeding(speed, speedLimit)
}

/** True when [metersPerSecond] is above [limitKph] by more than 5 km/h or 10 percent, whichever is more. */
fun isSpeeding(
    metersPerSecond: Double?,
    limitKph: Int?,
): Boolean {
    if (metersPerSecond == null || limitKph == null) return false
    val kph = metersPerSecond * 3.6
    return kph > limitKph + maxOf(SPEEDING_MARGIN_KPH, limitKph * SPEEDING_MARGIN_SHARE)
}

private const val SPEEDING_MARGIN_KPH = 5.0
private const val SPEEDING_MARGIN_SHARE = 0.1

/** A direction to speak, in [language] as a BCP 47 tag. [urgent] ones also vibrate. */
data class Announcement(
    val text: String,
    val language: String?,
    val urgent: Boolean = false,
)

/** A position from the device's location provider. */
data class LocationFix(
    val location: LatLng,
    val accuracyMeters: Float?,
    /** In metres per second. */
    val speed: Float?,
    /** In degrees from north. */
    val bearing: Float?,
    val timeMillis: Long,
)

interface LocationUpdates {
    /** Fixes for as long as the flow is collected. */
    fun updates(): Flow<LocationFix>
}

/** Turn-by-turn guidance along a street route, one trip at a time. */
interface Navigator {
    /** Null while no trip is running. */
    val state: StateFlow<NavigationState?>

    val announcements: Flow<Announcement>

    /** False while trips in [mode] cannot start, such as when no routing server is set for it. */
    fun isAvailable(mode: TransportMode): Boolean

    /** Starts a trip, replacing any running one. Throws when no route can be found. [simulate] drives the route by itself. */
    suspend fun start(
        from: LatLng,
        to: LatLng,
        mode: TransportMode,
        options: RouteOptions,
        simulate: Boolean = false,
    )

    fun stop()
}

enum class VoiceStatus {
    READY,

    /** The device has no speech engine. */
    NO_ENGINE,

    /** The speech engine has no voice for the trip's language. */
    NO_LANGUAGE,
}

/** Speaks the navigator's announcements aloud. */
interface VoiceGuidance {
    val muted: StateFlow<Boolean>

    fun setMuted(muted: Boolean)

    val status: StateFlow<VoiceStatus>
}
