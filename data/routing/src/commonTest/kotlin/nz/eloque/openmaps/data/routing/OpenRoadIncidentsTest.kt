package nz.eloque.openmaps.data.routing

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpHeaders
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import nz.eloque.openmaps.core.model.RoadEventKind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

private const val SOURCES_JSON =
    """{"generated_at":"2026-11-15T15:18:41Z","sources":[
    {"attribution":"NDW","country":"NL","id":"nl-ndw","incidents":1086,"licence":"CC0-1.0"},
    {"attribution":"Die Autobahn GmbH des Bundes","country":"DE","id":"de-autobahn","incidents":2109,"licence":"unconfirmed"}]}"""

private const val METADATA_JSON = """{"name":"tiles","minzoom":"6","maxzoom":"14","format":"pbf"}"""

class OpenRoadIncidentsTest {
    private val requested = mutableListOf<String>()

    private fun incidents(base: String?) =
        OpenRoadIncidents(
            HttpClient(
                MockEngine { request ->
                    requested += request.url.toString()
                    val body = if (request.url.encodedPath.endsWith("metadata.json")) METADATA_JSON else SOURCES_JSON
                    respond(body, headers = headersOf(HttpHeaders.ContentType, "application/json"))
                },
            ) {
                expectSuccess = true
                install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
            },
            MutableStateFlow(base),
        )

    @Test
    fun tilesComeFromTheServerWithEverySourceAttributed() =
        runTest {
            val tiles = incidents("http://localhost:8003/").tiles.filterNotNull().first()
            assertEquals("http://localhost:8003/v1/tiles/{z}/{x}/{y}.pbf", tiles.urlTemplate)
            assertEquals("NDW, Die Autobahn GmbH des Bundes", tiles.attribution)
            assertEquals(6 to 14, tiles.minZoom to tiles.maxZoom)
            assertEquals(
                listOf("http://localhost:8003/v1/sources.json", "http://localhost:8003/v1/tiles/metadata.json"),
                requested,
            )
        }

    @Test
    fun noTilesWithoutServer() =
        runTest {
            assertNull(incidents(null).tiles.first())
            assertEquals(emptyList(), requested)
        }

    @Test
    fun readsEventFromTileProperties() {
        val event =
            incidents(null).event(
                mapOf(
                    "id" to "de-autobahn:1",
                    "kind" to "roadworks",
                    "road_closed" to "true",
                    "lanes_restricted" to "false",
                    "speed_limit" to "60.0",
                    "road" to "A5",
                    "valid_from" to "2026-11-13T13:37:17.291902258Z",
                    "geometry_exact" to "false",
                    "attribution" to "Die Autobahn GmbH des Bundes",
                    "licence" to "unconfirmed",
                ),
            )!!
        assertEquals(RoadEventKind.ROADWORKS, event.kind)
        assertTrue(event.closed)
        assertEquals(60, event.speedLimit)
        assertEquals(Instant.parse("2026-11-13T13:37:17.291902258Z"), event.validFrom)
        assertNull(event.validUntil)
        assertFalse(event.exactGeometry)
        assertFalse(event.licenceConfirmed)
    }

    @Test
    fun unknownKindIsOther() {
        val event = incidents(null).event(mapOf("id" to "x", "kind" to "weather", "licence" to "CC0-1.0"))!!
        assertEquals(RoadEventKind.OTHER, event.kind)
        assertTrue(event.licenceConfirmed)
    }
}
