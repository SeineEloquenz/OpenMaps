package nz.eloque.openmaps.core.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GeoBoundsTest {
    private val center = LatLng(49.0, 8.4)

    @Test
    fun smallBoundsGrowToTheRadius() {
        val grown = GeoBounds(48.999, 8.399, 49.001, 8.401).coveringAtLeast(center, 1000.0)

        assertTrue(LatLng(grown.north, center.longitude).distanceTo(center) in 990.0..1010.0)
        assertTrue(LatLng(center.latitude, grown.east).distanceTo(center) in 990.0..1010.0)
    }

    @Test
    fun largeBoundsStayAsTheyAre() {
        val large = GeoBounds(48.9, 8.2, 49.1, 8.6)

        assertEquals(large, large.coveringAtLeast(center, 1000.0))
    }
}
