package nz.eloque.openmaps.data.geocoding

import nz.eloque.openmaps.core.model.Charger
import nz.eloque.openmaps.core.model.Connector
import nz.eloque.openmaps.core.model.Fuel
import nz.eloque.openmaps.core.model.PlaceFeature
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class OsmTagsTest {
    @Test
    fun readsChargersFromSockets() {
        val tags =
            mapOf(
                "socket:type2_combo" to "2",
                "socket:type2_combo:output" to "150 kW;50 kW",
                "socket:type2" to "no",
                "socket:chademo" to "1",
            )

        assertEquals(listOf(Charger(Connector.CCS, 150, 2), Charger(Connector.CHADEMO, null, 1)), tags.chargers())
    }

    @Test
    fun readsTypedDetails() {
        val tags =
            mapOf(
                "fee" to "no",
                "stars" to "4S",
                "fuel:diesel" to "yes",
                "fuel:e10" to "no",
                "cash_in" to "yes",
                "changing_table" to "yes",
            )

        assertEquals(true, tags.free())
        assertEquals(4, tags.stars())
        assertEquals(setOf(Fuel.DIESEL), tags.fuels())
        assertTrue(tags.features().containsAll(setOf(PlaceFeature.CASH_DEPOSIT, PlaceFeature.CHANGING_TABLE)))
    }

    @Test
    fun readsDietReservationsAndDogs() {
        val tags = mapOf("diet:halal" to "only", "reservation" to "recommended", "dog" to "leashed", "toilets:wheelchair" to "yes")
        assertEquals(
            setOf(PlaceFeature.HALAL, PlaceFeature.RESERVATIONS, PlaceFeature.DOGS, PlaceFeature.WHEELCHAIR_TOILET),
            tags.features(),
        )
        assertEquals(setOf(PlaceFeature.RESERVATION_REQUIRED), mapOf("reservation" to "required", "dog" to "no").features())
    }
}
