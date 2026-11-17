package nz.eloque.openmaps.ui.layers

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import nz.eloque.openmaps.core.model.RadarFrames
import nz.eloque.openmaps.ui.components.clockTime
import nz.eloque.openmaps.ui.resources.Res
import nz.eloque.openmaps.ui.resources.radar_forecast
import nz.eloque.openmaps.ui.resources.radar_pause
import nz.eloque.openmaps.ui.resources.radar_play
import org.jetbrains.compose.resources.stringResource
import kotlin.math.roundToInt
import kotlin.time.Duration.Companion.milliseconds

/** Plays or scrubs through the radar [frames], measured ones first and the forecast after them. */
@Composable
fun RadarTimeline(
    frames: RadarFrames,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    var playing by remember { mutableStateOf(false) }
    val last = frames.frames.lastIndex
    LaunchedEffect(playing, frames) {
        var index = selected
        while (playing) {
            delay(if (index == last) END_PAUSE else STEP)
            index = if (index >= last) 0 else index + 1
            onSelect(index)
        }
    }
    val frame = frames.frames.getOrNull(selected) ?: return
    Surface(
        shape = RoundedCornerShape(28.dp),
        shadowElevation = 4.dp,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = modifier.widthIn(max = 480.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(end = 16.dp)) {
            IconButton(onClick = { playing = !playing }) {
                Icon(
                    if (playing) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = stringResource(if (playing) Res.string.radar_pause else Res.string.radar_play),
                )
            }
            if (last > 0) {
                Slider(
                    value = selected.toFloat(),
                    onValueChange = {
                        playing = false
                        onSelect(it.roundToInt())
                    },
                    valueRange = 0f..last.toFloat(),
                    steps = (last - 1).coerceAtLeast(0),
                    modifier = Modifier.weight(1f),
                )
            }
            Text(
                if (frame.forecast) stringResource(Res.string.radar_forecast, clockTime(frame.time)) else clockTime(frame.time),
                style = MaterialTheme.typography.labelLarge,
                color = if (frame.forecast) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(start = 8.dp),
            )
        }
    }
}

private val STEP = 500.milliseconds
private val END_PAUSE = 1500.milliseconds
