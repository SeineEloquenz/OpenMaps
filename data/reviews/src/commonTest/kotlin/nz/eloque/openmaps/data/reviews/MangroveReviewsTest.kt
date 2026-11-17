package nz.eloque.openmaps.data.reviews

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpHeaders
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import nz.eloque.openmaps.core.model.LatLng
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Instant

// Trimmed from a real answer of api.mangrove.reviews, plus a review without rating or text.
private const val REVIEWS =
    """{"reviews":[
    {"signature":"old","jwt":"x","kid":"k","payload":{"iat":1794955147,"sub":"geo:49.26,16.57?q=Globus%20Bistro&u=30","rating":50,
    "opinion":" Na teplá jídla spíše nouzovka. ","metadata":{"client_id":"https://openreviews.cz/","nickname":"Jiří Eischmann"}}},
    {"signature":"new","jwt":"x","kid":"k","payload":{"iat":1794957739,"sub":"geo:49.26,16.57?q=Globus%20Bistro&u=30","rating":100,
    "metadata":{"client_id":"https://mangrove.reviews"}}},
    {"signature":"empty","jwt":"x","kid":"k","payload":{"iat":1794957000,"sub":"geo:49.26,16.57?q=Globus%20Bistro&u=30","opinion":" "}}
    ]}"""

class MangroveReviewsTest {
    @Test
    fun reviewsComeNewestFirstAsStars() =
        runTest {
            val client =
                HttpClient(
                    MockEngine { request ->
                        assertEquals("geo:49.26,16.57?q=Globus%20Bistro&u=50", request.url.parameters["sub"])
                        respond(REVIEWS, headers = headersOf(HttpHeaders.ContentType, "application/json"))
                    },
                ) { install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) } }
            val reviews = MangroveReviews(client, flowOf("https://mangrove.example/")).of("Globus Bistro", LatLng(49.26, 16.57))
            assertEquals(listOf("new", "old"), reviews.reviews.map { it.id })
            assertEquals(listOf(5, 3), reviews.reviews.map { it.stars })
            assertEquals(4.0, reviews.average)
            assertEquals("Na teplá jídla spíše nouzovka.", reviews.reviews.last().opinion)
            assertEquals("Jiří Eischmann", reviews.reviews.last().author)
            assertEquals(Instant.fromEpochSeconds(1794957739), reviews.reviews.first().time)
        }

    @Test
    fun namesWithQueryCharactersStayInTheName() {
        assertEquals("geo:1.0,2.0?q=Fish%20%26%20Chips&u=50", subject("Fish & Chips", LatLng(1.0, 2.0)))
    }

    @Test
    fun noServerMeansNoReviews() =
        runTest {
            val client = HttpClient(MockEngine { error("no request expected") })
            assertTrue(MangroveReviews(client, flowOf("")).of("Globus Bistro", LatLng(49.26, 16.57)).reviews.isEmpty())
        }
}
