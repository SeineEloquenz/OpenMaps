package nz.eloque.openmaps.data.weather

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
import nz.eloque.openmaps.core.model.WeatherCondition
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Clock
import kotlin.time.Instant

// Trimmed from a real answer of api.open-meteo.com for Karlsruhe on Wednesday 2026-11-18.
private const val KARLSRUHE =
    """{"latitude":49.01082,"longitude":8.409113,"utc_offset_seconds":3600,"timezone":"Europe/Berlin",
    "current":{"time":1794987900,"interval":900,"temperature_2m":11.4,"weather_code":3,"is_day":1,"wind_speed_10m":21.4},
    "hourly":{"time":[1794981600,1794985200,1794988800,1794992400],"temperature_2m":[11.4,10.9,11.6,null],
    "weather_code":[3,61,80,3],"is_day":[1,1,1,1],"precipitation_probability":[5,40,null,10]},
    "daily":{"time":[1794956400,1795042800],"weather_code":[61,0],"temperature_2m_max":[14.9,9.0],
    "temperature_2m_min":[8.1,2.5],"precipitation_probability_max":[60,0]}}"""

private object ForecastClock : Clock {
    override fun now() = Instant.fromEpochSeconds(1794988000)
}

class OpenMeteoForecastsTest {
    @Test
    fun readsCurrentHoursAndDays() =
        runTest {
            var requests = 0
            val client =
                HttpClient(
                    MockEngine { request ->
                        requests++
                        assertEquals("/v1/forecast", request.url.encodedPath)
                        assertEquals("49.0", request.url.parameters["latitude"])
                        respond(KARLSRUHE, headers = headersOf(HttpHeaders.ContentType, "application/json"))
                    },
                ) { install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) } }
            val forecasts = OpenMeteoForecasts(client, flowOf("https://example.org/"), ForecastClock)

            val forecast = forecasts.at(LatLng(49.0069, 8.4037))!!
            assertEquals(11.4, forecast.current.temperature)
            assertEquals(WeatherCondition.CLOUDY, forecast.current.condition)
            assertEquals(listOf(WeatherCondition.RAIN, WeatherCondition.SHOWERS), forecast.hours.map { it.condition })
            assertEquals(40, forecast.hours.first().precipitationChance)
            assertEquals(listOf(2, 3), forecast.days.map { it.weekday })
            assertEquals(14.9, forecast.days.first().high)

            forecasts.at(LatLng(49.001, 8.401))
            assertEquals(1, requests)
        }
}
