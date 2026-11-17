package nz.eloque.openmaps.core.model

import kotlin.time.Duration
import kotlin.time.Instant

data class EventTime(
    val scheduled: Instant,
    val actual: Instant,
    val isRealTime: Boolean,
) {
    val delay: Duration
        get() = actual - scheduled
}

data class Waypoint(
    val name: String,
    val location: LatLng,
    val stopId: String?,
    val platform: String?,
    val scheduledPlatform: String?,
    val arrival: EventTime?,
    val departure: EventTime?,
    val isCancelled: Boolean,
    val timeZone: String?,
) {
    val platformChanged: Boolean
        get() = platformChanged(platform, scheduledPlatform)
}

data class Line(
    val shortName: String?,
    val longName: String?,
    val mode: TransportMode,
    /** ARGB, or null when the feed has no colour. */
    val color: Long?,
    val textColor: Long?,
    val agency: String?,
) {
    val label: String
        get() = shortName ?: longName ?: ""
}

data class Alert(
    val header: String,
    val description: String?,
)

sealed interface Leg {
    val from: Waypoint
    val to: Waypoint
    val start: EventTime
    val end: EventTime
    val geometry: List<LatLng>
    val mode: TransportMode

    data class Street(
        override val mode: TransportMode,
        override val from: Waypoint,
        override val to: Waypoint,
        override val start: EventTime,
        override val end: EventTime,
        override val geometry: List<LatLng>,
        val distanceMeters: Double?,
        /** Names of the streets covering most of the leg, in travel order. */
        val streets: List<String> = emptyList(),
        /** The shared vehicle ridden on this leg, or null for the user's own feet, bike or car. */
        val rental: SharedRental? = null,
        /** Null when the routing service gave no heights. */
        val elevation: ElevationProfile? = null,
        /** Kinds of road this leg uses that drivers may want to know about beforehand. */
        val features: Set<RouteFeature> = emptySet(),
    ) : Leg

    data class Transit(
        override val from: Waypoint,
        override val to: Waypoint,
        override val start: EventTime,
        override val end: EventTime,
        override val geometry: List<LatLng>,
        val line: Line,
        val headsign: String?,
        val tripId: String?,
        val intermediateStops: List<Waypoint>,
        val alerts: List<Alert>,
        val isCancelled: Boolean,
        val isWheelchairAccessible: Boolean?,
    ) : Leg {
        override val mode: TransportMode
            get() = line.mode
    }
}

enum class RouteFeature {
    TOLLS,
    MOTORWAYS,
    FERRIES,
}

data class Itinerary(
    val id: String,
    val start: Instant,
    val end: Instant,
    val transfers: Int,
    val legs: List<Leg>,
) {
    val duration: Duration
        get() = end - start
}

/** The route to suggest first: the earliest arrival, or with [arriveBy] the latest departure, then fewer transfers, then the shorter ride. */
fun List<Itinerary>.best(arriveBy: Boolean = false): Itinerary? =
    minWithOrNull(
        compareBy<Itinerary> { if (arriveBy) -it.start.toEpochMilliseconds() else it.end.toEpochMilliseconds() }
            .thenBy { it.transfers }
            .thenBy { it.duration },
    )

data class Departure(
    val stopId: String?,
    val stopName: String,
    val stopLocation: LatLng,
    val line: Line,
    val headsign: String?,
    val time: EventTime,
    val platform: String?,
    val scheduledPlatform: String? = null,
    val tripId: String?,
    val isCancelled: Boolean,
    val alerts: List<Alert>,
) {
    val platformChanged: Boolean
        get() = platformChanged(platform, scheduledPlatform)
}

/** True when the stop moved to another platform. Numbered platforms compare by number, as feeds write `2` and `2(U)` alike. */
fun platformChanged(
    platform: String?,
    scheduled: String?,
): Boolean {
    if (platform == null || scheduled == null) return false
    val number = PlatformNumber.find(platform)?.value
    val scheduledNumber = PlatformNumber.find(scheduled)?.value
    if (number != null && scheduledNumber != null) return number.trimStart('0') != scheduledNumber.trimStart('0')
    return platform.bare() != scheduled.bare()
}

private fun String.bare() = replace(Bracketed, "").trim().lowercase()

private val PlatformNumber = Regex("""^\s*\d+""")
private val Bracketed = Regex("""\(.*?\)""")
