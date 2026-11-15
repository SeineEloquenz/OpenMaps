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

/** What to do at the next maneuver, as shown on screen. */
data class NavigationInstruction(
    val text: String,
    val secondary: String?,
    val maneuver: ManeuverType,
    /** The exit to take at a roundabout. */
    val exit: Int?,
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
)

/** A direction to speak, in [language] as a BCP 47 tag. */
data class Announcement(
    val text: String,
    val language: String?,
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
