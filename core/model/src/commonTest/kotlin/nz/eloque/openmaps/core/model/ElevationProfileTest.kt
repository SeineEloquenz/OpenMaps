package nz.eloque.openmaps.core.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ElevationProfileTest {
    // A flat stretch wobbling by four meters, then a steady climb of 60 meters and a level top.
    private val heights =
        List(20) { 100.0 + if (it % 2 == 0) 4.0 else 0.0 } + List(13) { 100.0 + it * 5.0 } + List(6) { 160.0 }

    @Test
    fun countsTheHillButNotTheWobbles() {
        val profile = ElevationProfile(30.0, heights)
        assertEquals(60.0, profile.ascent, 8.0)
        assertTrue(profile.descent < 5.0, "descent ${profile.descent}")
        assertEquals(100.0, profile.lowest)
        assertEquals(160.0, profile.highest)
    }

    @Test
    fun joinsLegsWithoutRepeatingTheStop() {
        val joined = ElevationProfile(30.0, listOf(1.0, 2.0)) + ElevationProfile(30.0, listOf(2.0, 3.0))
        assertEquals(listOf(1.0, 2.0, 3.0), joined.heights)
    }
}
