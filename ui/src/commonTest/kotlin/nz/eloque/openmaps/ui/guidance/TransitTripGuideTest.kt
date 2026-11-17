package nz.eloque.openmaps.ui.guidance

import nz.eloque.openmaps.core.model.EventTime
import nz.eloque.openmaps.core.model.Itinerary
import nz.eloque.openmaps.core.model.LatLng
import nz.eloque.openmaps.core.model.Leg
import nz.eloque.openmaps.core.model.Line
import nz.eloque.openmaps.core.model.TransportMode
import nz.eloque.openmaps.core.model.TripProgress
import nz.eloque.openmaps.core.model.TripStage
import nz.eloque.openmaps.core.model.Waypoint
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

class TransitTripGuideTest {
    private fun at(time: String) = Instant.parse("2026-11-16T$time:00Z")

    private fun event(
        time: String,
        actual: String = time,
    ) = EventTime(at(time), at(actual), isRealTime = time != actual)

    private fun stop(
        name: String,
        location: LatLng,
        arrival: String? = null,
        departure: String? = null,
        platform: String? = null,
    ) = Waypoint(name, location, "stop-$name", platform, arrival?.let { event(it) }, departure?.let { event(it) }, false, null)

    private val a = LatLng(49.0, 8.40)
    private val b = LatLng(49.0, 8.42)
    private val c = LatLng(49.0, 8.44)
    private val d = LatLng(49.0, 8.46)

    private fun tram(
        line: String,
        from: Waypoint,
        to: Waypoint,
        start: String,
        end: String,
        tripId: String,
    ) = Leg.Transit(
        from,
        to,
        event(start),
        event(end),
        listOf(from.location, to.location),
        Line(line, null, TransportMode.TRAM, null, null, null),
        "Durlach",
        tripId,
        emptyList(),
        emptyList(),
        false,
        null,
    )

    private val first = tram("1", stop("A", a, departure = "12:00"), stop("B", b, arrival = "12:10"), "12:00", "12:10", "t1")
    private val transfer =
        Leg.Street(TransportMode.WALK, stop("B", b), stop("C", c), event("12:10"), event("12:14"), listOf(b, c), 300.0)
    private val second = tram("2", stop("C", c, departure = "12:18"), stop("D", d, arrival = "12:30"), "12:18", "12:30", "t2")
    private val itinerary = Itinerary("route", at("12:00"), at("12:30"), 1, listOf(first, transfer, second))

    @Test
    fun refreshesTheVehicleWaitedForMoreOftenWhenItLeavesSoon() {
        val waiting = TripProgress(0, TripStage.WAIT)
        assertEquals(listOf(0 to 5.minutes), refreshTargets(itinerary, waiting, at("11:40")))
        assertEquals(listOf(0 to 1.minutes), refreshTargets(itinerary, waiting, at("11:55")))
    }

    @Test
    fun refreshesTheConnectionOnlyOnceTheTransferIsClose() {
        val riding = TripProgress(0, TripStage.RIDE, stopsLeft = 1)
        assertEquals(listOf(0 to 5.minutes), refreshTargets(itinerary, riding, at("11:59")))
        assertEquals(listOf(0 to 1.minutes, 2 to 5.minutes), refreshTargets(itinerary, riding, at("12:05")))
        assertEquals(listOf(0 to 1.minutes, 2 to 1.minutes), refreshTargets(itinerary, riding, at("12:09")))
    }

    @Test
    fun connectionIsAtRiskOnlyWhenTheFirstVehicleArrivesTooLate() {
        val riding = TripProgress(0, TripStage.RIDE, stopsLeft = 1)
        assertFalse(itinerary.connectionAtRisk(riding, at("12:05")))
        val late = itinerary.withLeg(0, first.copy(end = event("12:10", "12:16")))
        assertTrue(late.connectionAtRisk(riding, at("12:05")))
    }

    @Test
    fun takesDelaysAndPlatformsFromTheLiveRunAndMovesTheWalkAfter() {
        val run =
            tram(
                "1",
                stop("Origin", LatLng(49.0, 8.38), departure = "11:55"),
                Waypoint("End", d, "stop-End", null, event("12:20", "12:22"), null, false, null),
                "11:55",
                "12:20",
                "t1",
            ).copy(
                intermediateStops =
                    listOf(
                        Waypoint("A", a, "stop-A", "3", event("12:00", "12:03"), event("12:00", "12:03"), false, null),
                        Waypoint("B", b, "stop-B", null, event("12:10", "12:13"), event("12:10", "12:13"), false, null),
                    ),
            )
        val updated = first.updatedFrom(run)
        assertEquals(at("12:03"), updated.start.actual)
        assertEquals(at("12:13"), updated.end.actual)
        assertEquals("3", updated.from.platform)
        val shifted = itinerary.withLeg(0, updated)
        assertEquals(at("12:17"), shifted.legs[1].end.actual)
        assertEquals(at("12:30"), shifted.end)
    }
}
