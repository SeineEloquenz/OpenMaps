package nz.eloque.openmaps.data.routing

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpHeaders
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import nz.eloque.openmaps.core.model.DistanceUnit
import nz.eloque.openmaps.core.model.LatLng
import nz.eloque.openmaps.core.model.LocationUpdates
import nz.eloque.openmaps.core.model.ManeuverType
import nz.eloque.openmaps.core.model.NavigationStatus
import nz.eloque.openmaps.core.model.RouteOptions
import nz.eloque.openmaps.core.model.TransportMode
import org.maplibre.navigation.core.models.DirectionsResponse
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

class ValhallaNavigatorTest {
    private val bodies = mutableListOf<String>()

    private val routes =
        ValhallaNavigationRoutes(
            HttpClient(
                MockEngine { request ->
                    bodies += (request.body as TextContent).text
                    respond(OSRM_ROUTE_JSON, headers = headersOf(HttpHeaders.ContentType, "application/json"))
                },
            ) {
                expectSuccess = true
                install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
            },
            baseUrl = { "http://localhost:8002" },
            language = { "en-US" },
            units = { DistanceUnit.METRIC },
        )

    private val steps = DirectionsResponse.fromJson(OSRM_ROUTE_JSON).routes.single().legs.single().steps

    @Test
    fun asksForInstructionsInTheUsersLanguageAndHeading() =
        runTest {
            routes.route(LatLng(49.6116, 6.1319), 90f, LatLng(49.608, 6.125), TransportMode.CAR, RouteOptions())
            val body = bodies.single()
            listOf(
                "\"format\":\"osrm\"",
                "\"voice_instructions\":true",
                "\"banner_instructions\":true",
                "\"language\":\"en-US\"",
                "\"heading\":90",
                "\"date_time\":{\"type\":0}",
            ).forEach { assertTrue(it in body, it) }
        }

    @Test
    fun showsTheTurnAtTheEndOfTheStep() {
        val instruction = steps[0].upcoming(steps[0].distance, steps[1])!!
        assertEquals(ManeuverType.LEFT, instruction.maneuver)
        assertEquals("Boulevard Royal", instruction.text.substringBefore("/").trim())
    }

    // MapLibre Navigation reports progress on the main dispatcher, which plain JVM tests lack. It is not reset
    // afterwards, since the library may still post to it after stopping.
    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun simulatedTripMovesAlongTheRoute() =
        runTest {
            Dispatchers.setMain(Dispatchers.Default)
            val navigator =
                ValhallaNavigator(
                    routes,
                    object : LocationUpdates {
                        override fun updates() = emptyFlow<nz.eloque.openmaps.core.model.LocationFix>()
                    },
                )
            navigator.start(LatLng(49.6116, 6.1319), LatLng(49.608, 6.125), TransportMode.CAR, RouteOptions(), simulate = true)
            val moving =
                withContext(Dispatchers.Default) {
                    withTimeout(20.seconds) { navigator.state.first { it?.position != null && it.travelledMeters > 0 } }
                }!!
            assertEquals(NavigationStatus.NAVIGATING, moving.status)
            assertTrue(moving.remainingMeters < DirectionsResponse.fromJson(OSRM_ROUTE_JSON).routes.single().distance)
            assertTrue(moving.route.size > 10)
            navigator.stop()
            assertEquals(null, navigator.state.value)
        }
}
