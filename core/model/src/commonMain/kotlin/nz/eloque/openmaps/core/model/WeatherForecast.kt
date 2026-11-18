package nz.eloque.openmaps.core.model

import kotlin.time.Instant

enum class WeatherCondition {
    CLEAR,
    PARTLY_CLOUDY,
    CLOUDY,
    FOG,
    DRIZZLE,
    RAIN,
    FREEZING_RAIN,
    SNOW,
    SHOWERS,
    SNOW_SHOWERS,
    THUNDERSTORM,
}

/** The weather at [time], with temperatures in degrees Celsius. */
data class WeatherHour(
    val time: Instant,
    val temperature: Double,
    val condition: WeatherCondition,
    val day: Boolean,
    /** Percent, null where the model gives none. */
    val precipitationChance: Int? = null,
)

/** A day at the place, where [weekday] counts from 0 for Monday in the place's own time zone. */
data class WeatherDay(
    val weekday: Int,
    val condition: WeatherCondition,
    val high: Double,
    val low: Double,
    val precipitationChance: Int? = null,
)

data class Forecast(
    val current: WeatherHour,
    val hours: List<WeatherHour>,
    val days: List<WeatherDay>,
    /** Who made the forecast, to be credited where it is shown. */
    val attribution: String,
)

interface WeatherForecasts {
    /** The forecast for [location], or null where the source has none. */
    suspend fun at(location: LatLng): Forecast?
}
