package nz.eloque.openmaps.core.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

class TripWatchTest {
    private val noon = Instant.parse("2026-11-17T12:00:00Z")

    private fun stop(
        name: String,
        at: Instant,
        delayMinutes: Int = 0,
        platform: String? = "1",
    ) = WatchedStop(name, "id-$name", at, at + delayMinutes.minutes, platform, cancelled = false)

    private fun leg(
        departureDelay: Int = 0,
        arrivalDelay: Int = 0,
        fromPlatform: String? = "1",
        cancelled: Boolean = false,
    ) = WatchedLeg(
        "trip",
        "S5",
        stop("A", noon, departureDelay, fromPlatform),
        stop("B", noon + 30.minutes, arrivalDelay),
        cancelled,
    )

    private fun trip(checkedAt: Instant) = WatchedTrip("it", "B", listOf(leg()), checkedAt)

    @Test
    fun checksMoreOftenAsDepartureNears() {
        assertEquals(noon - 55.minutes, trip(noon - 60.minutes).nextCheck(noon - 59.minutes))
        assertEquals(noon - 105.minutes, trip(noon - 120.minutes).nextCheck(noon - 110.minutes))
        assertEquals(noon - 9.hours, trip(noon - 10.hours).nextCheck(noon - 10.hours))
        assertEquals(noon - 2.days, trip(noon - 3.days).nextCheck(noon - 3.days))
    }

    @Test
    fun keepsCheckingUntilArrivalThenStops() {
        assertEquals(noon + 15.minutes, trip(noon + 10.minutes).nextCheck(noon + 10.minutes))
        assertNull(trip(noon).nextCheck(noon + 31.minutes))
    }

    @Test
    fun overdueChecksRunNow() {
        assertEquals(noon - 10.minutes, trip(noon - 3.hours).nextCheck(noon - 10.minutes))
    }

    @Test
    fun smallDelayChangesAreIgnored() {
        assertTrue(changes(leg(), leg(departureDelay = 2)).isEmpty())
        val change = changes(leg(departureDelay = 2), leg(departureDelay = 5)).single() as TripChange.Departure
        assertEquals(5.minutes, change.delay)
        assertEquals(noon + 5.minutes, change.time)
    }

    @Test
    fun reportsArrivalDelaysAndLivePlatformChanges() {
        val found = changes(leg(), leg(arrivalDelay = 4, fromPlatform = "3"))
        assertEquals(2, found.size)
        assertTrue(found.any { it is TripChange.Arrival })
        assertEquals("3", found.filterIsInstance<TripChange.Platform>().single().platform)
        assertTrue(changes(leg(fromPlatform = null), leg(fromPlatform = "3")).isEmpty())
    }

    @Test
    fun cancellationOutweighsEverythingElse() {
        assertEquals(listOf<TripChange>(TripChange.Cancelled("S5", true)), changes(leg(), leg(departureDelay = 9, cancelled = true)))
    }

    @Test
    fun updatesFromTheRunAtTheMatchingStops() {
        fun waypoint(
            name: String,
            at: Instant,
            delay: Int,
            platform: String?,
        ) = Waypoint(
            name,
            LatLng(0.0, 0.0),
            "id-$name",
            platform,
            EventTime(at, at + delay.minutes, true),
            EventTime(at, at + delay.minutes, true),
            false,
            null,
        )
        val run =
            Leg.Transit(
                from = waypoint("Start", noon - 10.minutes, 6, "9"),
                to = waypoint("End", noon + 50.minutes, 6, "9"),
                start = EventTime(noon - 10.minutes, noon - 4.minutes, true),
                end = EventTime(noon + 50.minutes, noon + 56.minutes, true),
                geometry = emptyList(),
                line = Line("S5", null, TransportMode.SUBURBAN, null, null, null),
                headsign = null,
                tripId = "trip",
                intermediateStops = listOf(waypoint("A", noon, 6, "2"), waypoint("B", noon + 30.minutes, 7, null)),
                alerts = emptyList(),
                isCancelled = false,
                isWheelchairAccessible = null,
            )
        val updated = leg().updatedFrom(run)
        assertEquals(noon + 6.minutes, updated.from.expected)
        assertEquals("2", updated.from.platform)
        assertEquals(noon + 37.minutes, updated.to.expected)
        assertEquals("1", updated.to.platform)
    }
}
