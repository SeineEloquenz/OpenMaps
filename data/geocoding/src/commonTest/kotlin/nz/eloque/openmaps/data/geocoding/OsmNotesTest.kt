package nz.eloque.openmaps.data.geocoding

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.content.OutgoingContent
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import nz.eloque.openmaps.core.model.LatLng
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class OsmNotesTest {
    @Test
    fun postsTheNoteAndLinksToIt() =
        runTest {
            var body = ""
            var method: HttpMethod? = null
            var url = ""
            val client =
                HttpClient(
                    MockEngine { request ->
                        method = request.method
                        url = request.url.toString()
                        body = (request.body as OutgoingContent.ByteArrayContent).bytes().decodeToString()
                        respond(
                            """{"type":"Feature","geometry":{"type":"Point","coordinates":[8.4,49.0]},"properties":{"id":4711,"status":"open"}}""",
                            headers = headersOf(HttpHeaders.ContentType, "application/json"),
                        )
                    },
                ) { install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) } }

            val link = OsmNotes(client).report(LatLng(49.0, 8.4), "Café Klein: closed for good")

            assertEquals("https://www.openstreetmap.org/note/4711", link)
            assertEquals(HttpMethod.Post, method)
            assertEquals("https://api.openstreetmap.org/api/0.6/notes.json", url)
            assertTrue("lat=49.0" in body && "lon=8.4" in body)
            assertTrue("text=Caf%C3%A9+Klein%3A+closed+for+good%0A%0Avia+OpenMaps" in body, body)
        }
}
