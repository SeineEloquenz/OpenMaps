package nz.eloque.openmaps.ui.guidance

import nz.eloque.openmaps.core.model.LatLng
import kotlin.test.Test
import kotlin.test.assertEquals

class AlongRouteTest {
    // About 2.2 km due east along a line of latitude, then 1.1 km north.
    private val route = listOf(LatLng(49.0, 8.0), LatLng(49.0, 8.03), LatLng(49.01, 8.03))

    @Test
    fun keepsPlacesNearTheRouteInTheOrderItPassesThem() {
        val places =
            listOf(
                "past the corner" to LatLng(49.005, 8.0302),
                "far off" to LatLng(49.02, 8.01),
                "on the first stretch" to LatLng(49.0003, 8.01),
            )
        val found = alongRoute(route, places, corridor = 100.0) { it.second }
        assertEquals(listOf("on the first stretch", "past the corner"), found.map { it.item.first })
        assertEquals(route[0].distanceTo(LatLng(49.0, 8.01)), found[0].ahead, 5.0)
        assertEquals(33.0, found[0].offRoute, 2.0)
    }

    @Test
    fun sectionsJoinUpAndSplitTheLengthEvenly() {
        val parts = sections(route, 3)
        assertEquals(3, parts.size)
        assertEquals(route.first(), parts.first().first())
        assertEquals(parts[0].last(), parts[1].first())
        val lengths = parts.map { part -> part.zipWithNext { a, b -> a.distanceTo(b) }.sum() }
        lengths.forEach { assertEquals(lengths.first(), it, 1.0) }
    }

    @Test
    fun boundsCoverTheRouteWithAMargin() {
        val bounds = boundsAround(route, margin = 1000.0)
        assertEquals(48.991, bounds.south, 0.001)
        assertEquals(49.019, bounds.north, 0.001)
        assert(bounds.west < 8.0 && bounds.east > 8.03)
    }
}
