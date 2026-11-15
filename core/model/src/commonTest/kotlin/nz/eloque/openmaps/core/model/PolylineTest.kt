package nz.eloque.openmaps.core.model

import kotlin.test.Test
import kotlin.test.assertEquals

class PolylineTest {
    @Test
    fun decodesGoogleReferenceExample() {
        val points = decodePolyline("_p~iF~ps|U_ulLnnqC_mqNvxq`@", precision = 5)
        assertEquals(
            listOf(LatLng(38.5, -120.2), LatLng(40.7, -120.95), LatLng(43.252, -126.453)),
            points,
        )
    }

    @Test
    fun decodesEmptyString() {
        assertEquals(emptyList(), decodePolyline("", precision = 6))
    }
}
