package nz.eloque.openmaps.core.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CoordinatesTest {
    private val karlsruhe = LatLng(49.00691, 8.40370)

    private fun assertNear(
        expected: LatLng,
        actual: LatLng?,
        meters: Double = 10.0,
    ) {
        assertNotNull(actual)
        assertTrue(expected.distanceTo(actual) < meters, "$actual is not within $meters m of $expected")
    }

    @Test
    fun readsDecimalDegrees() {
        assertNear(karlsruhe, parseCoordinates("49.00691, 8.40370"))
        assertNear(karlsruhe, parseCoordinates("49.00691 8.4037"))
        assertNear(LatLng(-36.8485, 174.7633), parseCoordinates("-36.8485,174.7633"))
    }

    @Test
    fun readsHemisphereLettersInEitherOrder() {
        assertNear(karlsruhe, parseCoordinates("49.00691N 8.40370E"))
        assertNear(karlsruhe, parseCoordinates("E 8.40370, N 49.00691"))
        assertNear(LatLng(-36.8485, 174.7633), parseCoordinates("36.8485 S, 174.7633 E"))
        assertNear(LatLng(40.7128, -74.006), parseCoordinates("40.7128N 74.006W"))
    }

    @Test
    fun readsDegreesMinutesAndSeconds() {
        assertNear(karlsruhe, parseCoordinates("""49°0'24.9"N 8°24'13.3"E"""))
        assertNear(karlsruhe, parseCoordinates("49° 0.4146' N, 8° 24.222' E"))
    }

    @Test
    fun leavesOtherSearchesAlone() {
        assertNull(parseCoordinates("12 34"))
        assertNull(parseCoordinates("Kaiserstraße 12"))
        assertNull(parseCoordinates("95.0, 8.4"))
        assertNull(parseCoordinates("49.0N 8.4N"))
    }

    @Test
    fun plusCodesRoundTrip() {
        val code = karlsruhe.plusCode()
        assertEquals(11, code.length)
        assertEquals('+', code[8])
        assertNear(karlsruhe, parseCoordinates(code))
        assertNear(karlsruhe, parseCoordinates(code.lowercase()))
    }

    @Test
    fun shortPlusCodesNeedANearbyPoint() {
        val short = karlsruhe.plusCode().drop(4)
        assertNull(parseCoordinates(short))
        assertNear(karlsruhe, parseCoordinates(short, near = LatLng(48.9, 8.6)))
    }

    @Test
    fun formatsWithFiveDecimals() {
        assertEquals("49.00691, 8.40370", karlsruhe.formatted())
        assertEquals("-36.84850, 174.76330", LatLng(-36.8485, 174.7633).formatted())
    }
}
