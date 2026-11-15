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
import nz.eloque.openmaps.core.model.Accessibility
import nz.eloque.openmaps.core.model.GeoBounds
import nz.eloque.openmaps.core.model.OsmId
import nz.eloque.openmaps.core.model.OsmType
import nz.eloque.openmaps.core.model.PlaceFeature
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

// Trimmed response recorded from nominatim.openstreetmap.org.
private const val LOOKUP_JSON =
    """[{"place_id":156692351,"osm_type":"way","osm_id":29989787,"name":"Rijksmuseum",
    "extratags":{"fee":"yes","website":"https://www.rijksmuseum.nl/","wheelchair":"yes",
    "opening_hours":"Mo-Su 09:00-17:00","wikidata":"Q190804","phone":"+31 20 6747000;+31 20 6747047",
    "diet:vegan":"yes","internet_access":"wlan","toilets":"no"},"address":{"tourism":"Rijksmuseum","house_number":"1",
    "road":"Museumstraat","city":"Amsterdam","postcode":"1071 XX","country":"Nederland"},
    "boundingbox":["52.3592","52.3611","4.8828","4.8873"]}]"""

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
    fun looksUpManyPlacesInOneRequest() =
        runTest {
            val ids = listOf(OsmId(OsmType.WAY, 29989787), OsmId(OsmType.NODE, 1))
            val details = source(LOOKUP_JSON).detailsOf(ids)

            assertEquals(setOf(OsmId(OsmType.WAY, 29989787)), details.keys)
            assertEquals("Mo-Su 09:00-17:00", details.values.single().openingHours)
            assertEquals("W29989787,N1", requests.single().parameters["osm_ids"])
        }

    @Test
    fun readsExtraTags() =
        runTest {
            val details = source(LOOKUP_JSON).details(OsmId(OsmType.WAY, 29989787))

            assertEquals("Mo-Su 09:00-17:00", details?.openingHours)
            assertEquals("https://www.rijksmuseum.nl/", details?.website)
            assertEquals(Accessibility.YES, details?.wheelchair)
            assertEquals("Museumstraat 1, 1071 XX Amsterdam", details?.address)
            assertEquals("+31 20 6747000", details?.phone)
            assertEquals(GeoBounds(52.3592, 4.8828, 52.3611, 4.8873), details?.bounds)
            assertEquals("Q190804", details?.links?.wikidata)
            assertEquals(setOf(PlaceFeature.VEGAN, PlaceFeature.WIFI), details?.features)
            assertEquals("W29989787", requests.single().parameters["osm_ids"])
            assertEquals("1", requests.single().parameters["extratags"])
        }

    @Test
    fun returnsNullWhenNothingFound() =
        runTest {
            assertNull(source("[]").details(OsmId(OsmType.NODE, 1)))
        }
}
