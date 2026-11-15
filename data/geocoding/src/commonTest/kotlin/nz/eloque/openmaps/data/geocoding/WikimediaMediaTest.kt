package nz.eloque.openmaps.data.geocoding

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.Url
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import nz.eloque.openmaps.core.model.GeoBounds
import nz.eloque.openmaps.core.model.LatLng
import nz.eloque.openmaps.core.model.MediaLinks
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

// Trimmed responses recorded from the Wikidata, Commons and Wikipedia APIs.
private const val CLAIMS_JSON = """{"claims":{"P18":[{"mainsnak":{"datavalue":{"value":"Rijksmuseum in Amsterdam.jpg"}}}]}}"""
private const val SITELINKS_JSON =
    """{"entities":{"Q190804":{"sitelinks":{"dewiki":{"title":"Rijksmuseum Amsterdam"},"enwiki":{"title":"Rijksmuseum"}}}}}"""
private const val CATEGORY_CLAIMS_JSON = """{"claims":{"P373":[{"mainsnak":{"datavalue":{"value":"Rijksmuseum Amsterdam"}}}]}}"""
private const val CATEGORY_JSON =
    """{"query":{"pages":[{"title":"File:Hall.jpg"},{"title":"File:Rijksmuseum in Amsterdam.jpg"}]}}"""
private const val PHOTOS_JSON =
    """{"query":{"pages":[{"title":"File:Hall.jpg","imageinfo":[{"thumburl":"https://x/hall.jpg",
    "descriptionurl":"https://c/Hall","mime":"image/jpeg"}]},
    {"title":"File:Rijksmuseum in Amsterdam.jpg","imageinfo":[{
    "thumburl":"https://upload.wikimedia.org/thumb/a.jpg/960px-a.jpg?utm_source=commons",
    "descriptionurl":"https://commons.wikimedia.org/wiki/File:Rijksmuseum_in_Amsterdam.jpg","mime":"image/jpeg",
    "extmetadata":{"Artist":{"value":"<a href=\"//commons.wikimedia.org/wiki/User:X\">X</a>"},"LicenseShortName":{"value":"CC BY-SA 4.0"}}}]}]}}"""
private const val NEARBY_JSON =
    """{"query":{"pages":[
    {"title":"File:Far.jpg","imageinfo":[{"thumburl":"https://x/far.jpg","descriptionurl":"https://c/Far","mime":"image/jpeg"}],
    "coordinates":[{"lat":49.001,"lon":8.39}]},
    {"title":"File:Map.svg","imageinfo":[{"thumburl":"https://x/map.png","descriptionurl":"https://c/Map","mime":"image/svg+xml"}],
    "coordinates":[{"lat":49.0,"lon":8.39}]},
    {"title":"File:Near.jpg","imageinfo":[{"thumburl":"https://x/near.jpg","descriptionurl":"https://c/Near","mime":"image/jpeg"}],
    "coordinates":[{"lat":49.0001,"lon":8.39}]}]}}"""
private const val SUMMARY_JSON =
    """{"type":"standard","title":"Rijksmuseum Amsterdam","extract":"Das Rijksmuseum ist ein Museum.",
    "content_urls":{"mobile":{"page":"https://de.m.wikipedia.org/wiki/Rijksmuseum_Amsterdam"}}}"""

class WikimediaMediaTest {
    private val requests = mutableListOf<Url>()

    private fun source(language: String? = "de") =
        WikimediaMedia(
            HttpClient(
                MockEngine { request ->
                    requests += request.url
                    val params = request.url.parameters
                    val body =
                        when {
                            params["property"] == "P18" -> CLAIMS_JSON
                            params["property"] == "P373" -> CATEGORY_CLAIMS_JSON
                            params["generator"] == "categorymembers" -> CATEGORY_JSON
                            params["action"] == "wbgetentities" -> SITELINKS_JSON
                            params["generator"] == "geosearch" -> NEARBY_JSON
                            params["titles"] != null -> PHOTOS_JSON
                            request.url.encodedPath.contains("/page/summary/") -> SUMMARY_JSON
                            else -> return@MockEngine respond("", HttpStatusCode.NotFound)
                        }
                    respond(body, headers = headersOf(HttpHeaders.ContentType, "application/json"))
                },
            ) {
                install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
            },
            { language },
        )

    @Test
    fun linkedPhotoAndArticleInDeviceLanguage() =
        runTest {
            val media = source().media(MediaLinks(wikidata = "Q190804"), LatLng(52.36, 4.885), null)

            val photo = media.photos.first()
            assertFalse(media.photosNearby)
            assertEquals(listOf(photo.url, "https://x/hall.jpg"), media.photos.map { it.url })
            assertTrue(requests.any { it.parameters["gcmtitle"] == "Category:Rijksmuseum Amsterdam" })
            assertEquals("https://upload.wikimedia.org/thumb/a.jpg/960px-a.jpg", photo.url)
            assertEquals("X · CC BY-SA 4.0", photo.credit)
            assertEquals("Das Rijksmuseum ist ein Museum.", media.article?.extract)
            assertTrue(requests.any { it.encodedPath.endsWith("/page/summary/Rijksmuseum_Amsterdam") && it.host == "de.wikipedia.org" })
        }

    @Test
    fun nearbyPhotosWhenNothingIsLinked() =
        runTest {
            val media = source().media(MediaLinks(), LatLng(49.0, 8.39), null)

            assertTrue(media.photosNearby)
            assertEquals(listOf("https://x/near.jpg", "https://x/far.jpg"), media.photos.map { it.url })
            assertNull(media.article)
        }

    @Test
    fun nearbyPhotosAroundTheOutline() =
        runTest {
            source().media(MediaLinks(), LatLng(49.0, 8.39), GeoBounds(48.999, 8.389, 49.001, 8.391))

            val bbox =
                requests.single {
                    it.parameters["generator"] == "geosearch"
                }.parameters["ggsbbox"]!!.split('|').map { it.toDouble() }
            assertEquals(49.001 + 50 / 111_320.0, bbox[0], 1e-9)
            assertTrue(bbox[1] < 8.389 - 50 / 111_320.0)
        }

    @Test
    fun readsCommonsFileTitles() {
        assertEquals("File:A b.jpg", commonsFileTitle("File:A b.jpg"))
        assertEquals("File:Ä b.jpg", commonsFileTitle("https://commons.wikimedia.org/wiki/File:%C3%84_b.jpg"))
        assertEquals("File:A b.jpg", commonsFileTitle("https://upload.wikimedia.org/wikipedia/commons/1/12/A_b.jpg"))
        assertEquals("File:A b.jpg", commonsFileTitle("https://upload.wikimedia.org/wikipedia/commons/thumb/1/12/A_b.jpg/320px-A_b.jpg"))
        assertNull(commonsFileTitle("https://example.com/photo.jpg"))
        assertNull(commonsFileTitle("Category:Rijksmuseum"))
    }
}
