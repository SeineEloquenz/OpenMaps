package nz.eloque.openmaps.ui.guidance

import nz.eloque.openmaps.core.model.LatLng
import kotlin.test.Test
import kotlin.test.assertEquals

class SplitTest {
    private val route = listOf(LatLng(49.0, 6.0), LatLng(49.0, 6.01), LatLng(49.0, 6.02))
    private val legLength = route[0].distanceTo(route[1])

    @Test
    fun cutsInsideASegment() {
        val (done, left) = split(route, legLength * 1.5)
        assertEquals(3, done.size)
        assertEquals(2, left.size)
        assertEquals(6.015, done.last().longitude, 1e-6)
        assertEquals(done.last(), left.first())
    }

    @Test
    fun nothingDoneAtTheStartAndEverythingDonePastTheEnd() {
        assertEquals(emptyList<LatLng>() to route, split(route, 0.0))
        assertEquals(route to emptyList<LatLng>(), split(route, legLength * 3))
    }
}
