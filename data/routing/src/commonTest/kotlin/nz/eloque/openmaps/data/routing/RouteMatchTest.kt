package nz.eloque.openmaps.data.routing

import nz.eloque.openmaps.core.model.LatLng
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RouteMatchTest {
    private val start = LatLng(49.0, 8.4)
    private val end = LatLng(49.0, 8.5)
    private val north = listOf(start, LatLng(49.02, 8.45), end)
    private val south = listOf(start, LatLng(48.98, 8.45), end)

    @Test
    fun measuresHowMuchOfALineFollowsAnother() {
        assertTrue(overlap(south, south) > 0.99)
        assertTrue(overlap(north, south) < 0.1)
        val fromHalfway = listOf(LatLng(49.01, 8.425), LatLng(49.02, 8.45), end)
        assertTrue(overlap(fromHalfway, north) > 0.99)
    }

    @Test
    fun findsWhereARouteStraysFurthest() {
        val through = assertNotNull(detour(north, south))
        assertEquals(49.02, through.location.latitude, 0.002)
        assertEquals(8.45, through.location.longitude, 0.002)
        assertEquals(90.0, through.heading.toDouble(), 30.0)
    }

    @Test
    fun noDetourAlongTheSameRoad() {
        assertNull(detour(south, south))
    }

    @Test
    fun blocksOnlyTheMiddleOfALine() {
        val blocks = blocksAlong(north)
        assertTrue(blocks.size in 2..12)
        val longitudes = blocks.map { square -> square.map { it[0] }.average() }
        assertTrue(longitudes.all { it in 8.42..8.48 })
        assertTrue(blocks.all { it.first() == it.last() })
    }
}
