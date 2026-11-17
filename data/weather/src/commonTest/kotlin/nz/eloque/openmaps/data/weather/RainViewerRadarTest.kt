package nz.eloque.openmaps.data.weather

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpHeaders
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import nz.eloque.openmaps.core.model.RadarOptions
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Instant

// Trimmed from a real answer of api.librewxr.net.
private const val WEATHER_MAPS =
    """{"version":"2.0","generated":1794954784,"host":"https://api.librewxr.net","radar":{"past":[
    {"time":1794948000,"path":"/v2/radar/1794948000"},{"time":1794951000,"path":"/v2/radar/1794951000"},
    {"time":1794951600,"path":"/v2/radar/1794951600"},{"time":1794954600,"path":"/v2/radar/1794954600"}],
    "nowcast":[{"time":1794955200,"path":"/v2/radar/1794955200"}],
    "colorSchemes":[{"id":0,"name":"Black and White"}]},"satellite":{"infrared":[]}}"""

class RainViewerRadarTest {
    private fun client() =
        HttpClient(
            MockEngine { request ->
                assertEquals("https://wxr.example/public/weather-maps.json", request.url.toString())
                respond(WEATHER_MAPS, headers = headersOf(HttpHeaders.ContentType, "application/json"))
            },
        ) { install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) } }

    @Test
    fun theLastHourAndTheForecastAreShown() =
        runTest {
            val frames = RainViewerRadar(client(), flowOf("https://wxr.example/"), flowOf(RadarOptions())).frames.filterNotNull().first()
            assertEquals(listOf(false, false, true), frames.frames.map { it.forecast })
            assertEquals(Instant.fromEpochSeconds(1794951600), frames.frames.first().time)
            assertEquals(Instant.fromEpochSeconds(1794954600), frames.frames[frames.latestObserved].time)
            assertEquals(
                "https://api.librewxr.net/v2/radar/1794955200/256/{z}/{x}/{y}/2/1_1.png?arrows=dark&lightning=dots",
                frames.frames.last().urlTemplate,
            )
            assertEquals(
                "https://api.librewxr.net/v2/radar/1794955200/256/{z}/{x}/{y}/2/1_1.png?arrows=light&lightning=dots",
                frames.frames.last().darkUrlTemplate,
            )
        }

    @Test
    fun hiddenExtrasLeaveTheTilesPlain() =
        runTest {
            val options = RadarOptions(arrows = false, lightning = true)
            val frames = RainViewerRadar(client(), flowOf("https://wxr.example"), flowOf(options)).frames.filterNotNull().first()
            assertEquals(
                "https://api.librewxr.net/v2/radar/1794955200/256/{z}/{x}/{y}/2/1_1.png?lightning=dots",
                frames.frames.last().darkUrlTemplate,
            )
            val plain =
                RainViewerRadar(
                    client(),
                    flowOf("https://wxr.example"),
                    flowOf(RadarOptions(false, false)),
                ).frames.filterNotNull().first()
            assertEquals("https://api.librewxr.net/v2/radar/1794955200/256/{z}/{x}/{y}/2/1_1.png", plain.frames.last().urlTemplate)
        }

    @Test
    fun noServerMeansNoFrames() =
        runTest {
            assertNull(RainViewerRadar(client(), flowOf(""), flowOf(RadarOptions())).frames.toList().single())
        }
}
