package nz.eloque.openmaps.ui.components

import androidx.compose.runtime.Composable
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

fun formatDistance(meters: Double): String =
    if (meters < 1000) {
        "${(meters / 10).roundToInt() * 10} m"
    } else {
        val tenths = (meters / 100).roundToInt()
        "${tenths / 10}.${tenths % 10} km"
    }
