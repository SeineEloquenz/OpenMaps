package nz.eloque.openmaps.data.storage

import nz.eloque.openmaps.core.model.LatLng
import nz.eloque.openmaps.core.model.PlaceId
import nz.eloque.openmaps.core.model.SearchResult
import nz.eloque.openmaps.core.model.TransportMode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class PlaceColumnsTest {
    private val location = LatLng(49.0, 8.4)

    @Test
    fun roundTripsEveryKind() {
        val results =
            listOf(
                SearchResult.Stop("de:123", "Hbf", location, "Karlsruhe", setOf(TransportMode.TRAM, TransportMode.REGIONAL_RAIL)),
                SearchResult.Place(PlaceId("osm:way/42"), "Museum", location, "Karlsruhe", "museum_16"),
                SearchResult.Address("Main St 1", location, null, "Main St", "1", "76131"),
            )
        results.forEach { assertEquals(it, it.toColumns().toSearchResult()) }
    }

    @Test
    fun unknownKindAndModesDoNotThrow() {
        val columns = PlaceColumns("teleporter", "Somewhere", 1.0, 2.0, stopId = "x", modes = "TRAM,HOVERCRAFT")
        assertIs<SearchResult.Place>(columns.toSearchResult())

        val stop = columns.copy(kind = "stop").toSearchResult()
        assertEquals(setOf(TransportMode.TRAM), assertIs<SearchResult.Stop>(stop).modes)
    }
}
