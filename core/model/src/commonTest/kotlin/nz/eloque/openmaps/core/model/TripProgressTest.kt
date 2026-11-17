package nz.eloque.openmaps.core.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Instant

class TripProgressTest {
    private fun at(time: String) = Instant.parse("2026-11-16T$time:00Z")

    private fun event(time: String) = EventTime(at(time), at(time), isRealTime = false)

    private fun stop(
        name: String,
        location: LatLng,
        arrival: String?,
        departure: String?,
    ) = Waypoint(name, location, "stop-$name", null, arrival?.let(::event), departure?.let(::event), false, null)

    private val home = LatLng(49.000, 8.400)
    private val a = LatLng(49.000, 8.405)
    private val b = LatLng(49.000, 8.420)
    private val c = LatLng(49.000, 8.435)
    private val d = LatLng(49.000, 8.450)
    private val work = LatLng(49.003, 8.450)

    private val walkToTram =
        Leg.Street(
            TransportMode.WALK,
            stop("home", home, null, "12:00"),
            stop("A", a, "12:05", null),
            event("12:00"),
            event("12:05"),
            listOf(home, a),
            370.0,
        )
    private val tram =
        Leg.Transit(
            from = stop("A", a, null, "12:08"),
            to = stop("D", d, "12:20", null),
            start = event("12:08"),
            end = event("12:20"),
            geometry = listOf(a, b, c, d),
            line = Line("1", null, TransportMode.TRAM, null, null, null),
            headsign = "Durlach",
            tripId = "trip-1",
            intermediateStops = listOf(stop("B", b, "12:12", "12:12"), stop("C", c, "12:16", "12:16")),
            alerts = emptyList(),
            isCancelled = false,
            isWheelchairAccessible = null,
        )
    private val walkToWork =
        Leg.Street(
            TransportMode.WALK,
            stop("D", d, null, "12:20"),
            stop("work", work, "12:25", null),
            event("12:20"),
            event("12:25"),
            listOf(d, work),
            330.0,
        )
    private val itinerary = Itinerary("route", at("12:00"), at("12:25"), 0, listOf(walkToTram, tram, walkToWork))

    @Test
    fun walksToTheStopUntilReachingIt() {
        val progress = itinerary.progress(home, at("12:01"))
        assertEquals(TripStage.WALK, progress.stage)
        assertEquals(0, progress.legIndex)
        assertEquals(370.0, progress.metersLeft!!, 10.0)
    }

    @Test
    fun waitsAtTheStopBeforeDeparture() {
        assertEquals(TripProgress(1, TripStage.WAIT), itinerary.progress(a, at("12:06")))
    }

    @Test
    fun countsStopsLeftFromThePosition() {
        val first = itinerary.progress(a, at("12:09"))
        assertEquals(TripStage.RIDE, first.stage)
        assertEquals(3, first.stopsLeft)
        assertEquals(1, itinerary.progress(LatLng(49.000, 8.440), at("12:17"), first).stopsLeft)
    }

    @Test
    fun countsStopsLeftFromTheTimetableWithoutPosition() {
        assertEquals(2, itinerary.progress(null, at("12:13"), TripProgress(1, TripStage.RIDE, 3)).stopsLeft)
    }

    @Test
    fun movesToTheLastWalkAfterArrivingAndArrivesAtTheDestination() {
        assertEquals(TripStage.WALK, itinerary.progress(d, at("12:21"), TripProgress(1, TripStage.RIDE, 1)).stage)
        assertEquals(TripStage.ARRIVED, itinerary.progress(work, at("12:25"), TripProgress(2, TripStage.WALK)).stage)
    }

    @Test
    fun neverGoesBackToAnEarlierLeg() {
        val riding = TripProgress(1, TripStage.RIDE, 2)
        assertEquals(1, itinerary.progress(home, at("12:14"), riding).legIndex)
    }
}
