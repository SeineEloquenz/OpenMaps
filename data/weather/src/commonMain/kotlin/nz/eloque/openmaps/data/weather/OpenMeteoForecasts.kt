package nz.eloque.openmaps.data.weather

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.UtcOffset
import kotlinx.datetime.asTimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import nz.eloque.openmaps.core.model.Forecast
import nz.eloque.openmaps.core.model.LatLng
import nz.eloque.openmaps.core.model.WeatherCondition
import nz.eloque.openmaps.core.model.WeatherDay
import nz.eloque.openmaps.core.model.WeatherForecasts
import nz.eloque.openmaps.core.model.WeatherHour
import kotlin.math.roundToLong
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

@Serializable
internal data class OpenMeteoDto(
    @SerialName("utc_offset_seconds") val utcOffsetSeconds: Int = 0,
    val current: CurrentDto,
    val hourly: HourlyDto,
    val daily: DailyDto,
)

@Serializable
internal data class CurrentDto(
    val time: Long,
    @SerialName("temperature_2m") val temperature: Double,
    @SerialName("weather_code") val code: Int,
    @SerialName("is_day") val isDay: Int = 1,
)

@Serializable
internal data class HourlyDto(
    val time: List<Long>,
    @SerialName("temperature_2m") val temperature: List<Double?>,
    @SerialName("weather_code") val code: List<Int?>,
    @SerialName("is_day") val isDay: List<Int?> = emptyList(),
    @SerialName("precipitation_probability") val precipitation: List<Int?> = emptyList(),
)

@Serializable
internal data class DailyDto(
    val time: List<Long>,
    @SerialName("weather_code") val code: List<Int?>,
    @SerialName("temperature_2m_max") val high: List<Double?>,
    @SerialName("temperature_2m_min") val low: List<Double?>,
    @SerialName("precipitation_probability_max") val precipitation: List<Int?> = emptyList(),
)

/** Forecasts from an Open-Meteo server, which picks the best weather model for each place. */
internal class OpenMeteoForecasts(
    private val client: HttpClient,
    private val baseUrl: Flow<String?>,
    private val clock: Clock = Clock.System,
    private val keep: Duration = 30.minutes,
) : WeatherForecasts {
    private val mutex = Mutex()
    private val cache = mutableMapOf<Pair<Long, Long>, Pair<Instant, Forecast?>>()

    override suspend fun at(location: LatLng): Forecast? {
        val base = baseUrl.first()?.takeIf { it.isNotBlank() }?.trimEnd('/') ?: return null
        val key = (location.latitude * CELLS_PER_DEGREE).roundToLong() to (location.longitude * CELLS_PER_DEGREE).roundToLong()
        val now = clock.now()
        mutex.withLock { cache[key]?.takeIf { now - it.first < keep }?.let { return it.second } }
        val forecast =
            client
                .get("$base/v1/forecast") {
                    parameter("latitude", key.first / CELLS_PER_DEGREE)
                    parameter("longitude", key.second / CELLS_PER_DEGREE)
                    parameter("current", "temperature_2m,weather_code,is_day")
                    parameter("hourly", "temperature_2m,weather_code,is_day,precipitation_probability")
                    parameter("daily", "weather_code,temperature_2m_max,temperature_2m_min,precipitation_probability_max")
                    parameter("timezone", "auto")
                    parameter("timeformat", "unixtime")
                    parameter("forecast_days", DAYS)
                }.body<OpenMeteoDto>()
                .toForecast(now)
        mutex.withLock {
            cache.entries.removeAll { now - it.value.first >= keep }
            cache[key] = now to forecast
        }
        return forecast
    }
}

internal fun OpenMeteoDto.toForecast(now: Instant): Forecast? {
    val condition = weatherCondition(current.code) ?: return null
    val hours =
        hourly.time.indices.mapNotNull { i ->
            val time = Instant.fromEpochSeconds(hourly.time[i])
            if (time + 1.hours <= now) return@mapNotNull null
            WeatherHour(
                time = time,
                temperature = hourly.temperature.getOrNull(i) ?: return@mapNotNull null,
                condition = hourly.code.getOrNull(i)?.let(::weatherCondition) ?: return@mapNotNull null,
                day = hourly.isDay.getOrNull(i) != 0,
                precipitationChance = hourly.precipitation.getOrNull(i),
            )
        }
    val zone: TimeZone = UtcOffset(seconds = utcOffsetSeconds).asTimeZone()
    val days =
        daily.time.indices.mapNotNull { i ->
            WeatherDay(
                weekday = Instant.fromEpochSeconds(daily.time[i]).toLocalDateTime(zone).dayOfWeek.ordinal,
                condition = daily.code.getOrNull(i)?.let(::weatherCondition) ?: return@mapNotNull null,
                high = daily.high.getOrNull(i) ?: return@mapNotNull null,
                low = daily.low.getOrNull(i) ?: return@mapNotNull null,
                precipitationChance = daily.precipitation.getOrNull(i),
            )
        }
    return Forecast(
        current = WeatherHour(Instant.fromEpochSeconds(current.time), current.temperature, condition, current.isDay != 0),
        hours = hours,
        days = days,
        attribution = "Open-Meteo.com",
    )
}

/** WMO weather interpretation codes as Open-Meteo documents them. */
internal fun weatherCondition(code: Int): WeatherCondition? =
    when (code) {
        0 -> WeatherCondition.CLEAR
        1, 2 -> WeatherCondition.PARTLY_CLOUDY
        3 -> WeatherCondition.CLOUDY
        45, 48 -> WeatherCondition.FOG
        51, 53, 55 -> WeatherCondition.DRIZZLE
        56, 57, 66, 67 -> WeatherCondition.FREEZING_RAIN
        61, 63, 65 -> WeatherCondition.RAIN
        71, 73, 75, 77 -> WeatherCondition.SNOW
        80, 81, 82 -> WeatherCondition.SHOWERS
        85, 86 -> WeatherCondition.SNOW_SHOWERS
        95, 96, 99 -> WeatherCondition.THUNDERSTORM
        else -> null
    }

// Open-Meteo's models are a few kilometres coarse, so places this close share one forecast.
private const val CELLS_PER_DEGREE = 50.0
private const val DAYS = 4
