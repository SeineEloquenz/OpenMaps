package nz.eloque.openmaps.ui.components

import androidx.compose.runtime.Composable
import nz.eloque.openmaps.core.model.DistanceUnit
import nz.eloque.openmaps.ui.resources.Res
import nz.eloque.openmaps.ui.resources.duration_hours
import nz.eloque.openmaps.ui.resources.duration_minutes
import org.jetbrains.compose.resources.stringResource
import kotlin.math.roundToInt
import kotlin.time.Duration

@Composable
fun formatDuration(duration: Duration): String {
    val minutes = ((duration.inWholeSeconds + 30) / 60).toInt().coerceAtLeast(1)
    return if (minutes < 60) {
        stringResource(Res.string.duration_minutes, minutes)
    } else {
        stringResource(Res.string.duration_hours, minutes / 60, minutes % 60)
    }
}

fun formatDistance(
    meters: Double,
    unit: DistanceUnit = DistanceUnit.METRIC,
): String =
    when (unit) {
        DistanceUnit.METRIC ->
            if (meters < 1000) "${(meters / 10).roundToInt() * 10} m" else "${oneDecimal(meters / 1000)} km"
        DistanceUnit.IMPERIAL -> {
            val feet = meters / METERS_PER_FOOT
            if (feet < 1000) "${(feet / 50).roundToInt().coerceAtLeast(1) * 50} ft" else "${oneDecimal(meters / METERS_PER_MILE)} mi"
        }
    }

private fun oneDecimal(value: Double): String {
    val tenths = (value * 10).roundToInt()
    return "${tenths / 10}.${tenths % 10}"
}

private const val METERS_PER_FOOT = 0.3048
private const val METERS_PER_MILE = 1609.344
