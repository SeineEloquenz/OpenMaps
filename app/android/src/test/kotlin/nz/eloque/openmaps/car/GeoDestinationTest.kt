package nz.eloque.openmaps.car

import nz.eloque.openmaps.core.model.LatLng
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class GeoDestinationTest {
    @Test
    fun spokenAddressIsSearched() {
        assertEquals(CarDestination.Search("Schloss Karlsruhe"), geoDestination("0,0?q=Schloss Karlsruhe"))
    }

    @Test
    fun coordinatesTakeTheLabel() {
        assertEquals(
            CarDestination.At("Schloss", LatLng(49.0134, 8.4044)),
            geoDestination("49.0134,8.4044?q=49.0134,8.4044(Schloss)"),
        )
    }

    @Test
    fun coordinatesWithAQueryKeepItAsName() {
        assertEquals(CarDestination.At("Work", LatLng(49.0, 8.4)), geoDestination("49.0,8.4?q=Work"))
    }

    @Test
    fun bareCoordinatesAreTheirOwnName() {
        assertEquals(CarDestination.At("49.0,8.4", LatLng(49.0, 8.4)), geoDestination("49.0,8.4"))
    }

    @Test
    fun nothingToGoTo() {
        assertNull(geoDestination("0,0"))
    }
}
