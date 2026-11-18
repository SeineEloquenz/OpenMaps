package nz.eloque.openmaps.ui.weather

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AcUnit
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Dehaze
import androidx.compose.material.icons.filled.Grain
import androidx.compose.material.icons.filled.NightsStay
import androidx.compose.material.icons.filled.Thunderstorm
import androidx.compose.material.icons.filled.Umbrella
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material.icons.filled.WbCloudy
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.CancellationException
import nz.eloque.openmaps.core.model.DistanceUnit
import nz.eloque.openmaps.core.model.Forecast
import nz.eloque.openmaps.core.model.LatLng
import nz.eloque.openmaps.core.model.SettingsStore
import nz.eloque.openmaps.core.model.WeatherCondition
import nz.eloque.openmaps.core.model.WeatherForecasts
import nz.eloque.openmaps.core.model.WeatherHour
import nz.eloque.openmaps.ui.components.Weekdays
import nz.eloque.openmaps.ui.components.clockTime
import nz.eloque.openmaps.ui.resources.Res
import nz.eloque.openmaps.ui.resources.weather_at_arrival
import nz.eloque.openmaps.ui.resources.weather_clear
import nz.eloque.openmaps.ui.resources.weather_cloudy
import nz.eloque.openmaps.ui.resources.weather_drizzle
import nz.eloque.openmaps.ui.resources.weather_fog
import nz.eloque.openmaps.ui.resources.weather_freezing_rain
import nz.eloque.openmaps.ui.resources.weather_high_low
import nz.eloque.openmaps.ui.resources.weather_partly_cloudy
import nz.eloque.openmaps.ui.resources.weather_rain
import nz.eloque.openmaps.ui.resources.weather_showers
import nz.eloque.openmaps.ui.resources.weather_snow
import nz.eloque.openmaps.ui.resources.weather_snow_showers
import nz.eloque.openmaps.ui.resources.weather_source
import nz.eloque.openmaps.ui.resources.weather_thunderstorm
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import kotlin.math.roundToInt
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant

/** The forecast for [location], null while loading, without a location or when the source fails. */
@Composable
fun rememberForecast(location: LatLng?): Forecast? {
    val forecasts = koinInject<WeatherForecasts>()
    val key = location?.let { (it.latitude * KEY_CELLS).roundToInt() to (it.longitude * KEY_CELLS).roundToInt() }
    val state by produceState<Forecast?>(null, key) {
        value =
            location?.let {
                try {
                    forecasts.at(it)
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    null
                }
            }
    }
    return state
}

/** The weather now, the next hours and the coming days. */
@Composable
fun ForecastSection(
    forecast: Forecast,
    modifier: Modifier = Modifier,
) {
    val unit by koinInject<SettingsStore>().distanceUnit.collectAsStateWithLifecycle()
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            val now = forecast.current
            Icon(now.icon, contentDescription = null, modifier = Modifier.size(32.dp))
            Text(temperature(now.temperature, unit), style = MaterialTheme.typography.headlineSmall)
            Column {
                Text(stringResource(now.condition.label))
                forecast.days.firstOrNull()?.let {
                    Text(
                        stringResource(Res.string.weather_high_low, temperature(it.high, unit), temperature(it.low, unit)),
                        style = MaterialTheme.typography.bodySmall,
                        color = muted,
                    )
                }
            }
        }
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            forecast.hours.take(HOURS).forEach { hour ->
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(clockTime(hour.time), style = MaterialTheme.typography.labelSmall, color = muted)
                    Icon(hour.icon, contentDescription = stringResource(hour.condition.label), modifier = Modifier.size(20.dp))
                    Text(temperature(hour.temperature, unit), style = MaterialTheme.typography.bodySmall)
                    Text(
                        hour.precipitationChance?.takeIf { it >= MIN_CHANCE }?.let { "$it%" } ?: "",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            forecast.days.drop(1).forEach { day ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(stringResource(Weekdays[day.weekday]), style = MaterialTheme.typography.bodySmall, color = muted)
                    Icon(
                        day.condition.icon(day = true),
                        contentDescription = stringResource(day.condition.label),
                        modifier = Modifier.size(16.dp),
                    )
                    Text("${temperature(day.high, unit)} / ${temperature(day.low, unit)}", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        Text(stringResource(Res.string.weather_source, forecast.attribution), style = MaterialTheme.typography.labelSmall, color = muted)
    }
}

/** One line on the weather expected at the destination when arriving at [arrival]. */
@Composable
fun ArrivalWeather(
    forecast: Forecast,
    arrival: Instant,
    modifier: Modifier = Modifier,
) {
    val hour = forecast.hourAt(arrival) ?: return
    val unit by koinInject<SettingsStore>().distanceUnit.collectAsStateWithLifecycle()
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Icon(hour.icon, contentDescription = null, modifier = Modifier.size(20.dp))
        Text(
            listOf(
                stringResource(Res.string.weather_at_arrival, clockTime(arrival)),
                temperature(hour.temperature, unit),
                stringResource(hour.condition.label),
            ).joinToString(" · "),
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

private fun Forecast.hourAt(time: Instant): WeatherHour? = hours.lastOrNull { it.time <= time }?.takeIf { time - it.time < 1.hours }

private fun temperature(
    celsius: Double,
    unit: DistanceUnit,
): String =
    when (unit) {
        DistanceUnit.METRIC -> "${celsius.roundToInt()}°"
        DistanceUnit.IMPERIAL -> "${(celsius * 9 / 5 + 32).roundToInt()}°"
    }

private val WeatherHour.icon: ImageVector get() = condition.icon(day)

private fun WeatherCondition.icon(day: Boolean): ImageVector =
    when (this) {
        WeatherCondition.CLEAR -> if (day) Icons.Default.WbSunny else Icons.Default.Bedtime
        WeatherCondition.PARTLY_CLOUDY -> if (day) Icons.Default.WbCloudy else Icons.Default.NightsStay
        WeatherCondition.CLOUDY -> Icons.Default.Cloud
        WeatherCondition.FOG -> Icons.Default.Dehaze
        WeatherCondition.DRIZZLE -> Icons.Default.Grain
        WeatherCondition.RAIN, WeatherCondition.SHOWERS -> Icons.Default.Umbrella
        WeatherCondition.FREEZING_RAIN -> Icons.Default.WaterDrop
        WeatherCondition.SNOW, WeatherCondition.SNOW_SHOWERS -> Icons.Default.AcUnit
        WeatherCondition.THUNDERSTORM -> Icons.Default.Thunderstorm
    }

private val WeatherCondition.label: StringResource
    get() =
        when (this) {
            WeatherCondition.CLEAR -> Res.string.weather_clear
            WeatherCondition.PARTLY_CLOUDY -> Res.string.weather_partly_cloudy
            WeatherCondition.CLOUDY -> Res.string.weather_cloudy
            WeatherCondition.FOG -> Res.string.weather_fog
            WeatherCondition.DRIZZLE -> Res.string.weather_drizzle
            WeatherCondition.RAIN -> Res.string.weather_rain
            WeatherCondition.FREEZING_RAIN -> Res.string.weather_freezing_rain
            WeatherCondition.SNOW -> Res.string.weather_snow
            WeatherCondition.SHOWERS -> Res.string.weather_showers
            WeatherCondition.SNOW_SHOWERS -> Res.string.weather_snow_showers
            WeatherCondition.THUNDERSTORM -> Res.string.weather_thunderstorm
        }

private const val KEY_CELLS = 50.0
private const val HOURS = 24
private const val MIN_CHANCE = 20
