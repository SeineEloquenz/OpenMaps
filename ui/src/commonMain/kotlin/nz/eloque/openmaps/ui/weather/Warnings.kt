package nz.eloque.openmaps.ui.weather

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import nz.eloque.openmaps.core.model.LatLng
import nz.eloque.openmaps.core.model.WarningSeverity
import nz.eloque.openmaps.core.model.WeatherWarning
import nz.eloque.openmaps.core.model.WeatherWarnings
import nz.eloque.openmaps.ui.components.dayTime
import nz.eloque.openmaps.ui.components.timeRange
import nz.eloque.openmaps.ui.resources.Res
import nz.eloque.openmaps.ui.resources.close
import nz.eloque.openmaps.ui.resources.warning_source
import nz.eloque.openmaps.ui.resources.warning_until
import nz.eloque.openmaps.ui.resources.weather_warnings
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import kotlin.math.roundToInt
import kotlin.time.Clock

/** Warnings in force at [location], empty while loading, without a location or when the source fails. */
@Composable
fun rememberWarnings(location: LatLng?): List<WeatherWarning> {
    val warnings = koinInject<WeatherWarnings>()
    // Rounded so small moves of the user's position do not ask again.
    val key = location?.let { (it.latitude * KEY_CELLS).roundToInt() to (it.longitude * KEY_CELLS).roundToInt() }
    val state by produceState(emptyList<WeatherWarning>(), key) {
        value =
            location?.let {
                try {
                    warnings.at(it)
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    emptyList()
                }
            } ?: emptyList()
    }
    return state
}

/** A warning's headline and period, with its full text and areas after a tap. [prefix] names where it applies. */
@Composable
fun WarningCard(
    warning: WeatherWarning,
    modifier: Modifier = Modifier,
    prefix: String? = null,
    expandedAtStart: Boolean = false,
) {
    var expanded by rememberSaveable(warning.id) { mutableStateOf(expandedAtStart) }
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = modifier.fillMaxWidth().clickable { expanded = !expanded },
    ) {
        Row(Modifier.padding(12.dp).animateContentSize(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(Icons.Default.Warning, contentDescription = null, tint = warning.severity.color, modifier = Modifier.size(24.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    listOfNotNull(prefix, warning.title).joinToString(" · "),
                    style = MaterialTheme.typography.titleSmall,
                )
                warning.period()?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (expanded) {
                    warning.description?.let { Text(it, modifier = Modifier.padding(top = 8.dp)) }
                    if (warning.regions.isNotEmpty()) {
                        Text(
                            warning.regions.joinToString(", "),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                    }
                    warning.source?.let {
                        Text(
                            stringResource(Res.string.warning_source, it),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                }
            }
        }
    }
}

/** Every warning in [warnings], opened from the warning pill over the map. */
@Composable
fun WarningsDialog(
    warnings: List<WeatherWarning>,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(Icons.Default.Warning, contentDescription = null, tint = warnings.firstOrNull()?.severity?.color ?: Color.Unspecified)
        },
        title = { Text(stringResource(Res.string.weather_warnings)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                warnings.forEach { WarningCard(it, expandedAtStart = warnings.size == 1) }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(Res.string.close)) } },
    )
}

@Composable
private fun WeatherWarning.period(): String? {
    val start = start
    val end = end
    return when {
        start != null && end != null && start > Clock.System.now() -> timeRange(start, end)
        end != null -> stringResource(Res.string.warning_until, dayTime(end))
        else -> null
    }
}

/** MeteoAlarm's awareness colours. */
val WarningSeverity.color: Color
    get() =
        when (this) {
            WarningSeverity.EXTREME -> Color(0xFFEA4335)
            WarningSeverity.SEVERE -> Color(0xFFFA7B17)
            WarningSeverity.MODERATE -> Color(0xFFF9AB00)
            WarningSeverity.MINOR -> Color(0xFF34A853)
            WarningSeverity.UNKNOWN -> Color(0xFF9AA0A6)
        }

private const val KEY_CELLS = 50.0
