package nz.eloque.openmaps.data.transit

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpHeaders
import io.ktor.http.Url
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import nz.eloque.openmaps.core.model.LatLng
import nz.eloque.openmaps.core.model.Leg
import nz.eloque.openmaps.core.model.OsmId
import nz.eloque.openmaps.core.model.OsmType
import nz.eloque.openmaps.core.model.PlanRequest
import nz.eloque.openmaps.core.model.PlanTarget
import nz.eloque.openmaps.core.model.SearchResult
import nz.eloque.openmaps.core.model.TransportMode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes

class MotisClientTest {
    private val requests = mutableListOf<Url>()

    private fun client(body: String) =
        HttpClient(
            MockEngine { request ->
                requests += request.url
                respond(body, headers = headersOf(HttpHeaders.ContentType, "application/json"))
            },
        ) {
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
        }

    @Test
    fun geocodeMapsPlacesStopsAndOsmIds() =
        runTest {
            val geocoder = MotisGeocoder(client(GEOCODE_JSON), TRANSITOUS_API) { "nl" }
            val results = geocoder.search("Rijksmuseum", near = LatLng(52.36, 4.89))

            val place = assertIs<SearchResult.Place>(results.first())
            assertEquals(OsmId(OsmType.WAY, 29989787), place.osmId)
            assertEquals("Amsterdam", place.area)
            assertTrue(results.any { it is SearchResult.Stop && TransportMode.TRAM in it.modes })
            assertEquals("52.36,4.89", requests.single().parameters["place"])
            assertEquals("nl", requests.single().parameters["language"])
        }

    @Test
    fun stopsFirstMovesStopsToTheTop() =
        runTest {
            val geocoder = MotisGeocoder(client(GEOCODE_JSON), TRANSITOUS_API) { null }
            val results = geocoder.search("Rijksmuseum", near = null, stopsFirst = true)
            assertIs<SearchResult.Stop>(results.first())
        }

    @Test
    fun planMapsWalkAndTransitLegs() =
        runTest {
            val repository = MotisTransitRepository(client(PLAN_JSON), TRANSITOUS_API)
            val result =
                repository.plan(
                    PlanRequest(
                        from = PlanTarget.Coordinates(LatLng(52.3596918, 4.8846391)),
                        to = PlanTarget.Stop("de-DELFI_000008400058"),
                        time = null,
                    ),
                )

            val legs = result.itineraries.single().legs
            assertIs<Leg.Street>(legs[0])
            val bus = assertIs<Leg.Transit>(legs[1])
            assertEquals("391", bus.line.label)
            assertEquals(TransportMode.BUS, bus.mode)
            assertEquals(0xFFE1251B, bus.line.color)
            assertEquals(false, bus.isWheelchairAccessible)
            assertTrue(bus.geometry.size > 1)
            assertEquals("de-DELFI_000008400058", requests.single().parameters["toPlace"])
        }

    @Test
    fun departuresKeepScheduledAndRealtimeTimes() =
        runTest {
            val repository = MotisTransitRepository(client(STOP_TIMES_JSON), TRANSITOUS_API)
            val result = repository.departures("de-DELFI_000008400058")

            val intercity = result.departures.first()
            assertEquals("Intercity", intercity.line.label)
            assertEquals(8.minutes, intercity.time.delay)
            assertEquals("2a", intercity.platform)
            assertTrue(intercity.time.isRealTime)
        }

    @Test
    fun alertKeepsFirstTranslation() {
        val alert = AlertDto(headerText = "Werkzaamheden -|Construction works").toAlert()
        assertEquals("Werkzaamheden", alert?.header)
    }

    @Test
    fun unknownModeFallsBackToOther() {
        assertEquals(TransportMode.OTHER, transportMode("HOVERCRAFT"))
    }
}
