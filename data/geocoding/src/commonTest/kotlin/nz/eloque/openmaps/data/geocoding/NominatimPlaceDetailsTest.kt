package nz.eloque.openmaps.data.geocoding

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
import nz.eloque.openmaps.core.model.OsmId
import nz.eloque.openmaps.core.model.OsmType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

// Trimmed response recorded from nominatim.openstreetmap.org.
private const val LOOKUP_JSON =
    """[{"place_id":156692351,"osm_type":"way","osm_id":29989787,"name":"Rijksmuseum",
    "extratags":{"fee":"yes","website":"https://www.rijksmuseum.nl/","wheelchair":"yes",
    "opening_hours":"Mo-Su 09:00-17:00"}}]"""

class NominatimPlaceDetailsTest {
    private val requests = mutableListOf<Url>()

    private fun source(body: String) =
        NominatimPlaceDetails(
            HttpClient(
                MockEngine { request ->
                    requests += request.url
                    respond(body, headers = headersOf(HttpHeaders.ContentType, "application/json"))
                },
            ) {
                install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
            },
            { NOMINATIM_API },
        )

    @Test
    fun readsExtraTags() =
        runTest {
            val details = source(LOOKUP_JSON).details(OsmId(OsmType.WAY, 29989787))

            assertEquals("Mo-Su 09:00-17:00", details?.openingHours)
            assertEquals("https://www.rijksmuseum.nl/", details?.website)
            assertEquals("yes", details?.wheelchair)
            assertEquals("W29989787", requests.single().parameters["osm_ids"])
            assertEquals("1", requests.single().parameters["extratags"])
        }

    @Test
    fun returnsNullWhenNothingFound() =
        runTest {
            assertNull(source("[]").details(OsmId(OsmType.NODE, 1)))
        }
}
