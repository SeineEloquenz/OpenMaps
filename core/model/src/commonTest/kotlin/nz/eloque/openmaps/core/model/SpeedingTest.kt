package nz.eloque.openmaps.core.model

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SpeedingTest {
    private fun kph(value: Double) = value / 3.6

    @Test
    fun allowsFiveKilometresAnHourOnSlowRoads() {
        assertFalse(isSpeeding(kph(35.0), 30))
        assertTrue(isSpeeding(kph(36.0), 30))
    }

    @Test
    fun allowsTenPercentOnFastRoads() {
        assertFalse(isSpeeding(kph(132.0), 120))
        assertTrue(isSpeeding(kph(133.0), 120))
    }

    @Test
    fun neverSpeedingWithoutLimitOrSpeed() {
        assertFalse(isSpeeding(kph(200.0), null))
        assertFalse(isSpeeding(null, 50))
    }
}
