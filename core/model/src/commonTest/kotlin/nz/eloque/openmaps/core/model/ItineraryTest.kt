package nz.eloque.openmaps.core.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Instant

class ItineraryTest {
    private fun route(
        id: String,
        start: String,
        end: String,
        transfers: Int = 0,
    ) = Itinerary(id, Instant.parse("2026-11-15T$start:00Z"), Instant.parse("2026-11-15T$end:00Z"), transfers, emptyList())

    private val routes =
        listOf(
            route("early slow", "12:36", "13:01", transfers = 1),
            route("late fast", "12:51", "13:12"),
            route("same arrival, transfer", "12:40", "12:58", transfers = 1),
            route("same arrival, direct", "12:38", "12:58"),
        )

    @Test
    fun bestArrivesFirstThenHasFewerTransfers() {
        assertEquals("same arrival, direct", routes.best()?.id)
    }

    @Test
    fun bestForArriveByLeavesLast() {
        assertEquals("late fast", routes.best(arriveBy = true)?.id)
    }

    @Test
    fun noRoutesHaveNoBest() {
        assertNull(emptyList<Itinerary>().best())
    }
}
