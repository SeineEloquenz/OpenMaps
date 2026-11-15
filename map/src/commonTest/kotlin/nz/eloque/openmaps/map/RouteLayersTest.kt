package nz.eloque.openmaps.map

import nz.eloque.openmaps.core.model.LatLng
import kotlin.test.Test
import kotlin.test.assertEquals

class RouteLayersTest {
    @Test
    fun pointAlongMeasuresTheWholeRoute() {
        val route = listOf(LatLng(0.0, 0.0), LatLng(0.0, 1.0), LatLng(0.0, 3.0))

        assertEquals(LatLng(0.0, 1.5), pointAlong(route, 0.5))
        assertEquals(LatLng(0.0, 0.0), pointAlong(route, 0.0))
        assertEquals(LatLng(0.0, 3.0), pointAlong(route, 1.0))
    }
}
