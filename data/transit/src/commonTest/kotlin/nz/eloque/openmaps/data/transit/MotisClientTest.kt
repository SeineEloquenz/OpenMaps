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
import nz.eloque.openmaps.core.model.GeoBounds
import nz.eloque.openmaps.core.model.LatLng
import nz.eloque.openmaps.core.model.Leg
import nz.eloque.openmaps.core.model.ModeGroup
import nz.eloque.openmaps.core.model.OsmId
import nz.eloque.openmaps.core.model.OsmType
import nz.eloque.openmaps.core.model.PlanRequest
import nz.eloque.openmaps.core.model.PlanTarget
import nz.eloque.openmaps.core.model.RouteOptions
import nz.eloque.openmaps.core.model.SearchResult
import nz.eloque.openmaps.core.model.TransportMode
import nz.eloque.openmaps.core.model.WalkingSpeed
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

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
            val geocoder = MotisGeocoder(client(GEOCODE_JSON), { TRANSITOUS_API }) { "nl" }
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
            val geocoder = MotisGeocoder(client(GEOCODE_JSON), { TRANSITOUS_API }) { null }
            val results = geocoder.search("Rijksmuseum", near = null, stopsFirst = true)
            assertIs<SearchResult.Stop>(results.first())
        }

    @Test
    fun planMapsWalkAndTransitLegs() =
        runTest {
            val repository = MotisTransitRepository(client(PLAN_JSON), { TRANSITOUS_API })
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
            val repository = MotisTransitRepository(client(STOP_TIMES_JSON), { TRANSITOUS_API })
            val result = repository.departures("de-DELFI_000008400058")

            val intercity = result.departures.first()
            assertEquals("Intercity", intercity.line.label)
            assertEquals(8.minutes, intercity.time.delay)
            assertEquals("2a", intercity.platform)
            assertTrue(intercity.time.isRealTime)
        }

    @Test
    fun stopsInSendsCornersAndMapsModes() =
        runTest {
            val body =
                """[{"name":"Karlsruhe Barbarossaplatz","stopId":"de-KVV_Pde:08212:5003","lat":48.9949,"lon":8.3896,
                "tz":"Europe/Berlin","vertexType":"TRANSIT","modes":["TRAM","BUS"]},{"name":"No id","lat":1.0,"lon":2.0}]"""
            val repository = MotisTransitRepository(client(body), { TRANSITOUS_API })
            val stops = repository.stopsIn(GeoBounds(south = 48.99, west = 8.385, north = 49.002, east = 8.41))

            assertEquals(setOf(TransportMode.TRAM, TransportMode.BUS), stops.single().modes)
            assertEquals("48.99,8.41", requests.single().parameters["min"])
            assertEquals("49.002,8.385", requests.single().parameters["max"])
        }

    @Test
    fun planSendsRouteOptions() =
        runTest {
            val repository = MotisTransitRepository(client(PLAN_JSON), { TRANSITOUS_API })
            val target = PlanTarget.Coordinates(LatLng(1.0, 2.0))
            repository.plan(PlanRequest(target, target, time = null))
            repository.plan(
                PlanRequest(
                    target,
                    target,
                    time = null,
                    options =
                        RouteOptions(
                            modes = setOf(ModeGroup.LONG_DISTANCE_TRAIN, ModeGroup.BUS),
                            maxTransfers = 0,
                            walkingSpeed = WalkingSpeed.SLOW,
                            wheelchair = true,
                        ),
                ),
            )

            val defaults = requests[0].parameters
            assertEquals(null, defaults["transitModes"])
            assertEquals(null, defaults["maxTransfers"])
            assertEquals(null, defaults["pedestrianProfile"])
            val custom = requests[1].parameters
            assertEquals("HIGHSPEED_RAIL,LONG_DISTANCE,NIGHT_RAIL,BUS,COACH,FUNICULAR,AERIAL_LIFT,OTHER", custom["transitModes"])
            assertEquals("0", custom["maxTransfers"])
            assertEquals("0.9", custom["pedestrianSpeed"])
            assertEquals("WHEELCHAIR", custom["pedestrianProfile"])
        }

    @Test
    fun timesAreSentInWholeSeconds() =
        runTest {
            val repository = MotisTransitRepository(client(STOP_TIMES_JSON), { TRANSITOUS_API })
            repository.departures("stop", time = Instant.parse("2026-11-14T15:42:25.123456Z"))
            assertEquals("2026-11-14T15:42:25Z", requests.single().parameters["time"])
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
