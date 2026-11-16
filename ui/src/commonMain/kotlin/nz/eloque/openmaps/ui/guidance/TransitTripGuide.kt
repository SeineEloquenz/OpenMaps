package nz.eloque.openmaps.ui.guidance

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import nz.eloque.openmaps.core.model.Announcement
import nz.eloque.openmaps.core.model.EventTime
import nz.eloque.openmaps.core.model.Itinerary
import nz.eloque.openmaps.core.model.LatLng
import nz.eloque.openmaps.core.model.Leg
import nz.eloque.openmaps.core.model.LocationUpdates
import nz.eloque.openmaps.core.model.TransitRepository
import nz.eloque.openmaps.core.model.TripGuidance
import nz.eloque.openmaps.core.model.TripGuide
import nz.eloque.openmaps.core.model.TripProgress
import nz.eloque.openmaps.core.model.TripStage
import nz.eloque.openmaps.core.model.Waypoint
import nz.eloque.openmaps.core.model.progress
import nz.eloque.openmaps.ui.components.clockTime
import nz.eloque.openmaps.ui.resources.Res
import nz.eloque.openmaps.ui.resources.guide_announce_arrived
import nz.eloque.openmaps.ui.resources.guide_announce_at_risk
import nz.eloque.openmaps.ui.resources.guide_announce_board
import nz.eloque.openmaps.ui.resources.guide_announce_cancelled
import nz.eloque.openmaps.ui.resources.guide_announce_get_off
import nz.eloque.openmaps.ui.resources.guide_announce_hurry
import nz.eloque.openmaps.ui.resources.guide_announce_platform
import org.jetbrains.compose.resources.getString
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

/** Follows a public transport trip by position and timetable, refreshing only the vehicles that matter soon. */
class TransitTripGuide(
    private val transit: TransitRepository,
    private val location: LocationUpdates,
    private val clock: () -> Instant = { Clock.System.now() },
) : TripGuide {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val _state = MutableStateFlow<TripGuidance?>(null)
    override val state: StateFlow<TripGuidance?> = _state.asStateFlow()
    private val _announcements = MutableSharedFlow<Announcement>(extraBufferCapacity = 8)
    override val announcements: Flow<Announcement> = _announcements.asSharedFlow()
    private var job: Job? = null

    override fun start(
        itinerary: Itinerary,
        simulate: Boolean,
    ) {
        stop()
        job = scope.launch { guide(itinerary, simulate) }
    }

    override fun stop() {
        job?.cancel()
        job = null
        _state.value = null
    }

    private suspend fun guide(
        planned: Itinerary,
        simulate: Boolean,
    ) = coroutineScope {
        val began = clock()
        val simulatedStart = planned.start - SIMULATION_LEAD

        fun now() = if (simulate) simulatedStart + (clock() - began) * SIMULATION_SPEEDUP else clock()

        val fix = MutableStateFlow<LatLng?>(null)
        if (!simulate) {
            launch {
                try {
                    location.updates().collect { fix.value = it.location }
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    fix.value = null
                }
            }
        }

        var itinerary = planned
        var progress: TripProgress? = null
        var atRisk = false
        val refreshed = mutableMapOf<Int, Instant>()
        val announced = mutableSetOf<String>()
        while (true) {
            val time = now()
            val position = if (simulate) itinerary.simulatedPosition(time) else fix.value
            if (!simulate) {
                progress?.let { current ->
                    for ((index, interval) in refreshTargets(itinerary, current, time)) {
                        if (time - (refreshed[index] ?: Instant.DISTANT_PAST) < interval) continue
                        refreshed[index] = time
                        itinerary = refresh(itinerary, index)
                    }
                }
            }
            val next = itinerary.progress(position, time, progress)
            val risk = itinerary.connectionAtRisk(next, time)
            announce(itinerary, progress, next, time, risk && !atRisk, announced)
            progress = next
            atRisk = risk
            _state.value = TripGuidance(itinerary, next, position, risk, simulate)
            delay(if (simulate) SIMULATED_TICK else TICK)
        }
    }

    private suspend fun refresh(
        itinerary: Itinerary,
        index: Int,
    ): Itinerary {
        val leg = itinerary.legs[index] as? Leg.Transit ?: return itinerary
        val tripId = leg.tripId ?: return itinerary
        val run =
            try {
                transit.trip(tripId).legs.filterIsInstance<Leg.Transit>().firstOrNull()
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                null
            } ?: return itinerary
        val updated = leg.updatedFrom(run)
        if (updated.from.platform != leg.from.platform && updated.from.platform != null) {
            emit(getString(Res.string.guide_announce_platform, updated.line.label, updated.from.platform!!), urgent = true)
        }
        if (updated.isCancelled && !leg.isCancelled) {
            emit(getString(Res.string.guide_announce_cancelled, updated.line.label), urgent = true)
        }
        return itinerary.withLeg(index, updated)
    }

    private suspend fun announce(
        itinerary: Itinerary,
        before: TripProgress?,
        now: TripProgress,
        time: Instant,
        newRisk: Boolean,
        announced: MutableSet<String>,
    ) {
        suspend fun once(
            key: String,
            urgent: Boolean = false,
            text: suspend () -> String,
        ) {
            if (announced.add(key)) emit(text(), urgent)
        }
        val leg = itinerary.legs.getOrNull(now.legIndex)
        when (now.stage) {
            TripStage.WAIT ->
                (leg as? Leg.Transit)?.let {
                    once("board-${now.legIndex}") {
                        getString(Res.string.guide_announce_board, it.line.label, it.headsign.orEmpty(), clockTime(it.start.actual))
                    }
                }
            TripStage.RIDE ->
                if (now.stopsLeft == 1) {
                    (leg as? Leg.Transit)?.let {
                        once("exit-${now.legIndex}", urgent = true) { getString(Res.string.guide_announce_get_off, it.to.name) }
                    }
                }
            TripStage.WALK -> {
                val vehicle = itinerary.legs.getOrNull(now.legIndex + 1) as? Leg.Transit
                val left = vehicle?.start?.actual?.minus(time)
                if (vehicle != null && left != null && left <= HURRY && (now.metersLeft ?: 0.0) > HURRY_METERS) {
                    once("hurry-${now.legIndex}", urgent = true) {
                        getString(Res.string.guide_announce_hurry, vehicle.line.label, left.inWholeMinutes.coerceAtLeast(1).toInt())
                    }
                }
            }
            TripStage.ARRIVED -> if (before?.stage != TripStage.ARRIVED) once("arrived") { getString(Res.string.guide_announce_arrived) }
        }
        if (newRisk) emit(getString(Res.string.guide_announce_at_risk), urgent = true)
    }

    private fun emit(
        text: String,
        urgent: Boolean,
    ) {
        _announcements.tryEmit(Announcement(text, language = null, urgent = urgent))
    }
}

/**
 * Transit legs worth refreshing now with how often: the one being waited for or ridden, and the next once a transfer
 * comes close. Every minute within ten minutes of the moment that matters, every five minutes before.
 */
internal fun refreshTargets(
    itinerary: Itinerary,
    progress: TripProgress,
    now: Instant,
): List<Pair<Int, Duration>> {
    fun interval(at: Instant) = if (at - now <= CLOSE) 1.minutes else 5.minutes
    val legs = itinerary.legs
    val transits = (progress.legIndex until legs.size).filter { legs[it] is Leg.Transit }
    val first = transits.firstOrNull() ?: return emptyList()
    val firstLeg = legs[first] as Leg.Transit
    val riding = first == progress.legIndex && progress.stage == TripStage.RIDE
    val targets = mutableListOf(first to interval(if (riding) firstLeg.end.actual else firstLeg.start.actual))
    val second = transits.getOrNull(1)
    if (riding && second != null && firstLeg.end.actual - now <= CLOSE) {
        targets += second to interval((legs[second] as Leg.Transit).start.actual)
    }
    return targets
}

/** True when the next vehicle leaves before the user can reach it, or is cancelled. */
internal fun Itinerary.connectionAtRisk(
    progress: TripProgress,
    now: Instant,
): Boolean {
    val nextIndex = (progress.legIndex + 1 until legs.size).firstOrNull { legs[it] is Leg.Transit } ?: return false
    val next = legs[nextIndex] as Leg.Transit
    if (next.isCancelled) return true
    val reach =
        when (progress.stage) {
            TripStage.WALK -> now + ((progress.metersLeft ?: 0.0) / WALKING_METERS_PER_SECOND).seconds
            TripStage.RIDE, TripStage.WAIT -> {
                val ride = legs[progress.legIndex]
                val walks = (progress.legIndex + 1 until nextIndex).sumOf { (legs[it].end.actual - legs[it].start.actual).inWholeSeconds }
                ride.end.actual + walks.seconds
            }
            TripStage.ARRIVED -> return false
        }
    return reach > next.start.actual
}

/** This leg with times, platforms and cancellation from the vehicle's live [run]. */
internal fun Leg.Transit.updatedFrom(run: Leg.Transit): Leg.Transit {
    val stops = listOf(run.from) + run.intermediateStops + run.to
    val boarding = stops.indexOfFirst { it.matches(from) }
    val exit = stops.indexOfLast { it.matches(to) }
    if (boarding < 0 || exit <= boarding) return copy(isCancelled = run.isCancelled || isCancelled)
    val newFrom = stops[boarding]
    val newTo = stops[exit]
    return copy(
        from =
            from.copy(
                departure = newFrom.departure ?: from.departure,
                platform = newFrom.platform ?: from.platform,
                isCancelled = newFrom.isCancelled,
            ),
        to = to.copy(arrival = newTo.arrival ?: to.arrival, platform = newTo.platform ?: to.platform, isCancelled = newTo.isCancelled),
        start = newFrom.departure ?: start,
        end = newTo.arrival ?: end,
        intermediateStops = stops.subList(boarding + 1, exit),
        isCancelled = run.isCancelled || newFrom.isCancelled || newTo.isCancelled,
    )
}

private fun Waypoint.matches(other: Waypoint) = if (stopId != null && other.stopId != null) stopId == other.stopId else name == other.name

/** This itinerary with leg [index] replaced, moving the walks after it by its new delay up to the next vehicle. */
internal fun Itinerary.withLeg(
    index: Int,
    leg: Leg.Transit,
): Itinerary {
    val shift = leg.end.actual - legs[index].end.actual
    val nextTransit = (index + 1 until legs.size).firstOrNull { legs[it] is Leg.Transit } ?: legs.size
    val updated =
        legs.mapIndexed { i, old ->
            when {
                i == index -> leg
                i in index + 1 until nextTransit && old is Leg.Street ->
                    old.copy(
                        start = old.start.shifted(shift),
                        end = old.end.shifted(shift),
                    )
                else -> old
            }
        }
    return copy(legs = updated, end = if (nextTransit == legs.size) end + shift else end)
}

private fun EventTime.shifted(by: Duration) = copy(actual = actual + by)

/** Where a simulated user would be at [time] when every leg runs on time. */
internal fun Itinerary.simulatedPosition(time: Instant): LatLng {
    val leg = legs.firstOrNull { time < it.end.actual } ?: return legs.last().to.location
    if (time <= leg.start.actual) return leg.from.location
    val fraction = (time - leg.start.actual) / (leg.end.actual - leg.start.actual)
    return leg.geometry.pointAlong(fraction) ?: leg.from.location
}

private fun List<LatLng>.pointAlong(fraction: Double): LatLng? {
    if (size < 2) return firstOrNull()
    val lengths = zipWithNext { a, b -> a.distanceTo(b) }
    var left = lengths.sum() * fraction.coerceIn(0.0, 1.0)
    for ((i, length) in lengths.withIndex()) {
        if (left <= length) {
            val t = if (length == 0.0) 0.0 else left / length
            return LatLng(
                this[i].latitude + (this[i + 1].latitude - this[i].latitude) * t,
                this[i].longitude + (this[i + 1].longitude - this[i].longitude) * t,
            )
        }
        left -= length
    }
    return last()
}

private val TICK = 5.seconds
private val SIMULATED_TICK = 1.seconds
private const val SIMULATION_SPEEDUP = 10
private val SIMULATION_LEAD = 2.minutes
private val CLOSE = 10.minutes
private val HURRY = 2.minutes
private const val HURRY_METERS = 100.0
private const val WALKING_METERS_PER_SECOND = 1.25
