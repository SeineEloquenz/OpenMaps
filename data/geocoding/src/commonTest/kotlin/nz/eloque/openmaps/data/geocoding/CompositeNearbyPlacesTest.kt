package nz.eloque.openmaps.data.geocoding

import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import nz.eloque.openmaps.core.model.Attribution
import nz.eloque.openmaps.core.model.Fuel
import nz.eloque.openmaps.core.model.FuelPrice
import nz.eloque.openmaps.core.model.FuelPrices
import nz.eloque.openmaps.core.model.GeoBounds
import nz.eloque.openmaps.core.model.LatLng
import nz.eloque.openmaps.core.model.LoadedPlaces
import nz.eloque.openmaps.core.model.PlaceCategory
import nz.eloque.openmaps.core.model.PlaceDetails
import nz.eloque.openmaps.core.model.PlaceDetailsSource
import nz.eloque.openmaps.core.model.PlaceId
import nz.eloque.openmaps.core.model.PricedStation
import nz.eloque.openmaps.core.model.SearchResult
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CompositeNearbyPlacesTest {
    private val center = LatLng(49.0, 8.4)
    private val area = GeoBounds(48.99, 8.39, 49.01, 8.41)

    private fun place(
        id: Long,
        latitude: Double,
    ) = SearchResult.Place(PlaceId("osm:node/$id"), "Place $id", LatLng(latitude, 8.4), null, "toilets")

    private val lookups = mutableListOf<List<PlaceId>>()
    private val details =
        object : PlaceDetailsSource {
            override suspend fun details(id: PlaceId) = null

            override fun pageUrl(id: PlaceId) = null

            override suspend fun detailsOf(ids: List<PlaceId>): Map<PlaceId, PlaceDetails> {
                lookups += ids
                return ids.associateWith {
                    PlaceDetails(address = null, openingHours = null, website = null, phone = null, wheelchair = null)
                }
            }
        }

    private fun charging(available: Boolean) =
        object : ChargingStations {
            override val attribution = Attribution("Chargers", "https://example.org")
            override val available = available

            override suspend fun stations(
                center: LatLng,
                bounds: GeoBounds,
            ) = listOf(place(9, 49.0) to PlaceDetails(address = null, openingHours = null, website = null, phone = null, wheelchair = null))
        }

    @Test
    fun addsDetailsThenLoadedPlacesInsideTheArea() =
        runTest {
            val nearby = CompositeNearbyPlaces({ _, _, _ -> listOf(place(1, 49.005)) }, details, charging(available = true))
            val loaded = LoadedPlaces { flowOf(listOf(place(1, 49.005), place(2, 49.001), place(3, 49.5))) }

            val emitted = nearby.search(PlaceCategory.TOILETS, center, area, loaded).toList()

            assertNull(emitted.first().places.single().details)
            val last = emitted.last()
            assertEquals(listOf("osm:node/2", "osm:node/1"), last.places.map { it.place.id?.value })
            assertEquals(2, last.places.count { it.details != null })
            assertEquals(listOf(listOf(PlaceId("osm:node/1")), listOf(PlaceId("osm:node/2"))), lookups)
            assertEquals(emptyList(), last.attributions)
        }

    @Test
    fun chargingComesFromTheStationDatabaseWithAttribution() =
        runTest {
            val nearby = CompositeNearbyPlaces({ _, _, _ -> error("not used") }, details, charging(available = true))

            val results = nearby.search(PlaceCategory.CHARGING, center, area, null).toList().single()

            assertEquals("Chargers", results.attributions.single().source)
            assertEquals("osm:node/9", results.places.single().place.id?.value)
        }

    @Test
    fun chargingFallsBackWithoutTheDatabase() =
        runTest {
            val nearby = CompositeNearbyPlaces({ _, _, _ -> listOf(place(1, 49.005)) }, details, charging(available = false))

            val results = nearby.search(PlaceCategory.CHARGING, center, area, null).toList().last()

            assertEquals("osm:node/1", results.places.single().place.id?.value)
        }
}

class StationMatchingTest {
    private fun place(
        name: String,
        longitude: Double,
    ) = SearchResult.Place(PlaceId("osm:$name"), name, LatLng(49.0, longitude), null, "fuel")

    private fun station(longitude: Double) =
        PricedStation(LatLng(49.0, longitude), null, null, null, listOf(FuelPrice(Fuel.DIESEL, 1.5, "EUR", null)), Attribution("p", "u"))

    @Test
    fun pairsEachStationWithTheClosestPlaceOnlyOnce() {
        val near = place("near", 8.4)
        val close = place("close", 8.4004)
        val far = place("far", 8.41)
        val a = station(8.4001)
        val b = station(8.4005)

        val matched = matchStations(listOf(near, close, far), listOf(a, b))

        assertEquals(mapOf(near to a, close to b), matched)
    }

    @Test
    fun addsPricesAndStationsMissingFromPlaceData() =
        runTest {
            val osm = place("osm", 8.4)
            val prices =
                object : FuelPrices {
                    override suspend fun stations(
                        center: LatLng,
                        area: GeoBounds,
                    ) = listOf(station(8.4002), station(8.405))
                }
            val nearby =
                CompositeNearbyPlaces(
                    { _, _, _ -> listOf(osm) },
                    object : PlaceDetailsSource {
                        override suspend fun details(id: PlaceId) = null

                        override fun pageUrl(id: PlaceId) = null

                        override suspend fun detailsOf(ids: List<PlaceId>) = emptyMap<PlaceId, PlaceDetails>()
                    },
                    object : ChargingStations {
                        override val attribution = Attribution("c", "u")
                        override val available = false

                        override suspend fun stations(
                            center: LatLng,
                            bounds: GeoBounds,
                        ) = emptyList<Pair<SearchResult.Place, PlaceDetails>>()
                    },
                    prices,
                )

            val last =
                nearby.search(
                    PlaceCategory.GAS_STATIONS,
                    LatLng(49.0, 8.4),
                    GeoBounds(48.99, 8.39, 49.01, 8.41),
                    null,
                ).toList().last()

            assertEquals(listOf("osm:osm", null), last.places.map { it.place.id?.value })
            assertTrue(last.places.all { it.details?.fuelPrices?.isNotEmpty() == true })
            assertEquals(listOf("p"), last.attributions.map { it.source })
        }
}
