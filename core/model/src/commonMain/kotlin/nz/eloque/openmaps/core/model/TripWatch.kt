package nz.eloque.openmaps.core.model

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlin.math.abs
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

/** A stop of a watched ride as last seen. */
data class WatchedStop(
    val name: String,
    val stopId: String?,
    val scheduled: Instant,
    val expected: Instant,
    val platform: String?,
    val cancelled: Boolean,
) {
    val delay: Duration
        get() = expected - scheduled
}

/** One vehicle ride of a watched trip. */
data class WatchedLeg(
    val tripId: String,
    val line: String,
    val from: WatchedStop,
    val to: WatchedStop,
    val cancelled: Boolean,
)

/** A public transport trip checked in the background for delays, cancellations and platform changes. */
data class WatchedTrip(
    val id: String,
    val destination: String,
    val legs: List<WatchedLeg>,
    val checkedAt: Instant,
) {
    val departure: Instant
        get() = legs.first().from.expected

    val arrival: Instant
        get() = legs.last().to.expected
}

interface TripWatchStore {
    val trips: Flow<List<WatchedTrip>>

    suspend fun trip(id: String): WatchedTrip?

    suspend fun save(trip: WatchedTrip)

    suspend fun delete(id: String)
}

sealed interface TripChange {
    val line: String

    data class Cancelled(
        override val line: String,
        val cancelled: Boolean,
    ) : TripChange

    data class Departure(
        override val line: String,
        val stop: String,
        val time: Instant,
        val delay: Duration,
    ) : TripChange

    data class Arrival(
        override val line: String,
        val stop: String,
        val time: Instant,
        val delay: Duration,
    ) : TripChange

    data class Platform(
        override val line: String,
        val stop: String,
        val platform: String,
        val departure: Boolean,
    ) : TripChange
}

/** The rides of this itinerary that can be watched, or null when it has none. */
fun Itinerary.toWatchedTrip(
    destination: String,
    now: Instant,
): WatchedTrip? {
    val legs =
        legs.filterIsInstance<Leg.Transit>().mapNotNull { leg ->
            val tripId = leg.tripId ?: return@mapNotNull null
            WatchedLeg(
                tripId = tripId,
                line = leg.line.label,
                from = leg.from.watched(leg.start),
                to = leg.to.watched(leg.end),
                cancelled = leg.isCancelled,
            )
        }
    if (legs.isEmpty()) return null
    return WatchedTrip(id, destination, legs, now)
}

private fun Waypoint.watched(time: EventTime) = WatchedStop(name, stopId, time.scheduled, time.actual, platform, isCancelled)

/**
 * When to check [this] trip next, or null once it has arrived. Checks come every 5 minutes within an hour of the
 * departure, or of the arrival once under way, every 15 minutes within 4 hours, hourly within a day and daily before.
 */
fun WatchedTrip.nextCheck(now: Instant): Instant? {
    val target = if (departure > now) departure else arrival
    val left = target - now
    if (left < Duration.ZERO) return null
    val interval = CheckIntervals.first { (within, _) -> left < within }.second
    return maxOf(checkedAt + interval, now)
}

private val CheckIntervals =
    listOf(
        1.hours to 5.minutes,
        4.hours to 15.minutes,
        1.days to 1.hours,
        4.days to 1.days,
        Duration.INFINITE to 7.days,
    )

/**
 * Fetches the live state of every ride not yet over and returns the updated trip with what changed since the last
 * check. Rides that fail to load keep their last state.
 */
suspend fun WatchedTrip.check(
    transit: TransitRepository,
    now: Instant,
): Pair<WatchedTrip, List<TripChange>> {
    val changes = mutableListOf<TripChange>()
    val updated =
        legs.map { leg ->
            if (leg.to.expected < now) return@map leg
            val run =
                try {
                    transit.trip(leg.tripId).legs.filterIsInstance<Leg.Transit>().firstOrNull()
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    null
                } ?: return@map leg
            leg.updatedFrom(run).also { changes += changes(leg, it) }
        }
    return copy(legs = updated, checkedAt = now) to changes
}

/** [this] ride with times, platforms and cancellation from the vehicle's live [run]. */
fun WatchedLeg.updatedFrom(run: Leg.Transit): WatchedLeg {
    val stops = listOf(run.from) + run.intermediateStops + run.to
    val boarding = stops.indexOfFirst { from.matches(it) }
    val exit = stops.indexOfLast { to.matches(it) }
    if (boarding < 0 || exit <= boarding) return copy(cancelled = run.isCancelled)
    val newFrom = stops[boarding]
    val newTo = stops[exit]
    return copy(
        from = from.updated(newFrom.departure, newFrom),
        to = to.updated(newTo.arrival, newTo),
        cancelled = run.isCancelled || newFrom.isCancelled || newTo.isCancelled,
    )
}

private fun WatchedStop.matches(stop: Waypoint) = if (stopId != null && stop.stopId != null) stopId == stop.stopId else name == stop.name

private fun WatchedStop.updated(
    time: EventTime?,
    stop: Waypoint,
) = copy(expected = time?.actual ?: expected, platform = stop.platform ?: platform, cancelled = stop.isCancelled)

/**
 * What changed on a ride between two live states. Platforms compare live against live, since the timetable may
 * number platforms differently from the live feed.
 */
fun changes(
    old: WatchedLeg,
    new: WatchedLeg,
): List<TripChange> {
    if (old.cancelled != new.cancelled) return listOf(TripChange.Cancelled(new.line, new.cancelled))
    return buildList {
        if (significant(old.from.delay, new.from.delay)) {
            add(TripChange.Departure(new.line, new.from.name, new.from.expected, new.from.delay))
        }
        if (significant(old.to.delay, new.to.delay)) {
            add(TripChange.Arrival(new.line, new.to.name, new.to.expected, new.to.delay))
        }
        if (old.from.platform != null && new.from.platform != null && old.from.platform != new.from.platform) {
            add(TripChange.Platform(new.line, new.from.name, new.from.platform, departure = true))
        }
        if (old.to.platform != null && new.to.platform != null && old.to.platform != new.to.platform) {
            add(TripChange.Platform(new.line, new.to.name, new.to.platform, departure = false))
        }
    }
}

private fun significant(
    old: Duration,
    new: Duration,
) = abs((new - old).inWholeSeconds) > SIGNIFICANT_DELAY_CHANGE.inWholeSeconds

private val SIGNIFICANT_DELAY_CHANGE = 2.minutes

/** Checks watched trips in the background and notifies the user of changes until they arrive. */
interface TripWatcher {
    /** Ids of the itineraries being watched. */
    val watched: StateFlow<Set<String>>

    fun watch(trip: WatchedTrip)

    fun stop(id: String)
}
