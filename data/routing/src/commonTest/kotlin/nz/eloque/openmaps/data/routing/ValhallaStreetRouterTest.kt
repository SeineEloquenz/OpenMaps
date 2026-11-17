package nz.eloque.openmaps.data.routing

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import nz.eloque.openmaps.core.model.EventTime
import nz.eloque.openmaps.core.model.Itinerary
import nz.eloque.openmaps.core.model.LatLng
import nz.eloque.openmaps.core.model.Leg
import nz.eloque.openmaps.core.model.RouteOptions
import nz.eloque.openmaps.core.model.StreetRequest
import nz.eloque.openmaps.core.model.StreetRouter
import nz.eloque.openmaps.core.model.TransportMode
import nz.eloque.openmaps.core.model.Waypoint
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

// Trimmed from a Valhalla 3.9 pedestrian route in Luxembourg City.
private const val ROUTE_JSON =
    """{"trip":{"legs":[{"shape":"{n}r}AquauJh@hr@tBCD~CBfD@pBtMR","summary":{"time":523.771,"length":0.739},
    "maneuvers":[{"street_names":["Rue Notre-Dame"],"length":0.058},{"length":0.104},
    {"street_names":["Boulevard Franklin D. Roosevelt","N 50"],"length":0.121},{"street_names":["Rue de la Semois"],"length":0.072},
    {"street_names":["Rue de la Semois"],"length":0.312},{"length":0.0}]}],"summary":{"time":523.771,"length":0.739}}}"""

private const val NO_ROUTE_JSON = """{"error_code":171,"error":"No suitable edges near location","status_code":400}"""

private val Now = Instant.parse("2026-11-15T12:00:00Z")

class ValhallaStreetRouterTest {
    private val bodies = mutableListOf<String>()

    private fun router(
        status: HttpStatusCode = HttpStatusCode.OK,
        response: String = ROUTE_JSON,
        url: String? = "http://localhost:8002/",
    ) = ValhallaStreetRouter(
        HttpClient(
            MockEngine { request ->
                bodies += (request.body as TextContent).text
                respond(response, status, headersOf(HttpHeaders.ContentType, "application/json"))
            },
        ) {
            expectSuccess = true
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
        },
        servers = ValhallaServers(car = { url }, active = { url }, activeInterval = kotlin.time.Duration.ZERO),
        now = { Now },
    )

    private val request =
        StreetRequest(LatLng(49.61, 6.129), LatLng(49.608, 6.125), setOf(TransportMode.WALK), time = null)

    @Test
    fun mapsRouteToStreetLeg() =
        runTest {
            val route = router().route(request).single()
            val leg = route.legs.single() as Leg.Street
            assertEquals(TransportMode.WALK, leg.mode)
            assertEquals(Now, route.start)
            assertEquals(523L, (route.end - route.start).inWholeSeconds)
            assertEquals(739.0, leg.distanceMeters!!, 0.01)
            assertEquals(listOf("Rue de la Semois", "Boulevard Franklin D. Roosevelt").toSet(), leg.streets.toSet())
            assertEquals("Boulevard Franklin D. Roosevelt", leg.streets.first())
            assertEquals(7, leg.geometry.size)
        }

    @Test
    fun asksForClosuresAndWalkingOptions() =
        runTest {
            router().route(request.copy(options = RouteOptions(wheelchair = true)))
            val body = bodies.single()
            assertTrue("\"costing\":\"pedestrian\"" in body)
            assertTrue("\"date_time\":{\"type\":0}" in body)
            assertTrue("\"exclude_closures\":false" in body)
            assertTrue("\"search_cutoff\":1000" in body)
            assertTrue("\"type\":\"wheelchair\"" in body)
        }

    @Test
    fun avoidsOnlyWhatTheDriverChose() =
        runTest {
            router().route(request.copy(modes = setOf(TransportMode.CAR), options = RouteOptions(avoidTolls = true, avoidFerries = true)))
            val body = bodies.single()
            assertTrue("\"use_tolls\":0.0" in body)
            assertTrue("\"use_ferry\":0.0" in body)
            assertTrue("use_highways" !in body)
        }

    @Test
    fun arrivesByTheRequestedTime() =
        runTest {
            val arrival = Now + 30.minutes
            val route = router().route(request.copy(time = arrival, arriveBy = true)).single()
            assertEquals(arrival, route.end)
        }

    @Test
    fun unroutableRequestHasNoRoute() =
        runTest {
            assertEquals(emptyList(), router(HttpStatusCode.BadRequest, NO_ROUTE_JSON).route(request))
        }

    @Test
    fun fallsBackWhileNoServerIsSet() =
        runTest {
            val fallbackRoute = Itinerary("fallback", Now, Now, 0, emptyList())
            val fallback = fixed(fallbackRoute)
            assertEquals(listOf(fallbackRoute), FallbackStreetRouter(router(url = null), fallback).route(request))
            assertEquals(emptyList(), bodies)
        }

    @Test
    fun fallsBackWhenTheServerFails() =
        runTest {
            val fallback = fixed(Itinerary("fallback", Now, Now, 0, emptyList()))
            val routes = FallbackStreetRouter(router(HttpStatusCode.ServiceUnavailable, "{}"), fallback).route(request)
            assertEquals("fallback", routes.single().id)
        }
}

private fun fixed(route: Itinerary) =
    object : StreetRouter {
        override suspend fun route(request: StreetRequest) = listOf(route)
    }

class FallbackStreetRouterTest {
    private val start = Instant.parse("2026-11-15T12:00:00Z")

    private fun route(mode: TransportMode) =
        Itinerary(
            mode.name,
            start,
            start,
            0,
            listOf(
                Leg.Street(
                    mode,
                    waypointAt(),
                    waypointAt(),
                    EventTime(start, start, false),
                    EventTime(start, start, false),
                    emptyList(),
                    null,
                ),
            ),
        )

    private fun waypointAt() = Waypoint("", LatLng(0.0, 0.0), null, null, null, null, null, false, null)

    @Test
    fun asksTheFallbackOnlyForMissingModes() =
        runTest {
            val asked = mutableListOf<Set<TransportMode>>()
            val primary =
                object : StreetRouter {
                    override suspend fun route(request: StreetRequest) = listOf(route(TransportMode.CAR))
                }
            val fallback =
                object : StreetRouter {
                    override suspend fun route(request: StreetRequest): List<Itinerary> {
                        asked += request.modes
                        return request.modes.map { route(it) }
                    }
                }
            val request =
                StreetRequest(LatLng(0.0, 0.0), LatLng(0.0, 0.0), setOf(TransportMode.CAR, TransportMode.WALK), time = null)
            val routes = FallbackStreetRouter(primary, fallback).route(request)
            assertEquals(listOf(setOf(TransportMode.WALK)), asked)
            assertEquals(setOf("CAR", "WALK"), routes.map { it.id }.toSet())
        }
}
