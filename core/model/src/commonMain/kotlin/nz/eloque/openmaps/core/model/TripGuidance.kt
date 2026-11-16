package nz.eloque.openmaps.core.model

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

enum class TripStage { WALK, WAIT, RIDE, ARRIVED }

/** Where the user is along an itinerary. */
data class TripProgress(
    val legIndex: Int,
    val stage: TripStage,
    /** Stops until the one to get off at, counting it. 1 means the next stop. Zero unless riding. */
    val stopsLeft: Int = 0,
    /** Metres left on a street leg, null when unknown. */
    val metersLeft: Double? = null,
)

data class TripGuidance(
    val itinerary: Itinerary,
    val progress: TripProgress,
    val position: LatLng?,
    /** True when delays leave too little time to reach the next vehicle. */
    val connectionAtRisk: Boolean,
    val simulated: Boolean,
)

/** Guidance along a public transport itinerary, one trip at a time. */
interface TripGuide {
    /** Null while no trip is guided. */
    val state: StateFlow<TripGuidance?>

    val announcements: Flow<Announcement>

    fun start(
        itinerary: Itinerary,
        simulate: Boolean = false,
    )

    fun stop()
}

/**
 * Where the user is in this itinerary at [now], from [position] when known and from the timetable otherwise.
 * Never moves back before [after], so a jumpy fix cannot undo a finished leg.
 */
fun Itinerary.progress(
    position: LatLng?,
    now: Instant,
    after: TripProgress? = null,
): TripProgress {
    var index = after?.legIndex ?: 0
    while (index < legs.size && legs[index].isDone(position, now)) index++
    val leg = legs.getOrNull(index) ?: return TripProgress(legs.size, TripStage.ARRIVED)
    return when (leg) {
        is Leg.Street ->
            TripProgress(index, TripStage.WALK, metersLeft = position?.let { leg.geometry.metersLeftFrom(it) })
        is Leg.Transit -> {
            val wasRiding = after?.legIndex == index && after.stage == TripStage.RIDE
            val departed = now >= leg.start.actual || position?.let { it.distanceTo(leg.from.location) > LEFT_STOP_METERS } == true
            if (wasRiding || departed) {
                TripProgress(index, TripStage.RIDE, stopsLeft = leg.stopsLeft(position, now))
            } else {
                TripProgress(index, TripStage.WAIT)
            }
        }
    }
}

private fun Leg.isDone(
    position: LatLng?,
    now: Instant,
): Boolean =
    when (this) {
        is Leg.Street ->
            if (position != null) position.distanceTo(to.location) < REACHED_METERS else now >= end.actual
        is Leg.Transit ->
            now >= end.actual + if (position == null || position.distanceTo(to.location) < AT_STOP_METERS) 0.minutes else LATE_ARRIVAL
    }

/** Stops left to the exit, by the stop the user is nearest to when on the line, otherwise by the timetable. */
private fun Leg.Transit.stopsLeft(
    position: LatLng?,
    now: Instant,
): Int {
    val stops = intermediateStops + to
    val nearest =
        position
            ?.takeIf { geometry.isEmpty() || geometry.distanceFrom(it) < ON_LINE_METERS }
            ?.let { here -> stops.indices.minByOrNull { stops[it].location.distanceTo(here) } }
    val passedByTime = stops.count { (it.arrival ?: it.departure)?.actual?.let { time -> time <= now } == true }
    val passed =
        if (nearest == null) {
            passedByTime
        } else {
            val next = stops.getOrNull(nearest + 1)
            val beyond = next != null && position!!.distanceTo(next.location) < stops[nearest].location.distanceTo(next.location)
            nearest + if (beyond) 1 else 0
        }
    return (stops.size - passed).coerceIn(1, stops.size)
}

/** Metres from the point of this line nearest to [point] to its end. */
fun List<LatLng>.metersLeftFrom(point: LatLng): Double {
    if (size < 2) return firstOrNull()?.distanceTo(point) ?: 0.0
    val nearest = indices.minBy { this[it].distanceTo(point) }
    return (nearest until lastIndex).sumOf { this[it].distanceTo(this[it + 1]) }
}

/** Metres from [point] to the nearest vertex of this line. */
fun List<LatLng>.distanceFrom(point: LatLng): Double = minOfOrNull { it.distanceTo(point) } ?: Double.MAX_VALUE

private const val REACHED_METERS = 40.0
private const val LEFT_STOP_METERS = 250.0
private const val AT_STOP_METERS = 300.0
private const val ON_LINE_METERS = 400.0
private val LATE_ARRIVAL = 3.minutes
