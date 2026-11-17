package nz.eloque.openmaps.ui.directions

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import nz.eloque.openmaps.core.model.DistanceUnit
import nz.eloque.openmaps.core.model.ElevationProfile
import nz.eloque.openmaps.ui.components.formatHeight
import nz.eloque.openmaps.ui.resources.Res
import nz.eloque.openmaps.ui.resources.elevation_range
import nz.eloque.openmaps.ui.resources.elevation_summary
import org.jetbrains.compose.resources.stringResource

/** Climb and descent of a route over a small chart of its heights. */
@Composable
fun ElevationChart(
    profile: ElevationProfile,
    unit: DistanceUnit,
    modifier: Modifier = Modifier,
) {
    val lowest = profile.lowest ?: return
    val highest = profile.highest ?: return
    val summary = stringResource(Res.string.elevation_summary, formatHeight(profile.ascent, unit), formatHeight(profile.descent, unit))
    val line = MaterialTheme.colorScheme.primary
    val fill = line.copy(alpha = 0.2f)
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
            Text(summary, style = MaterialTheme.typography.bodyMedium)
            Text(
                stringResource(Res.string.elevation_range, formatHeight(lowest, unit), formatHeight(highest, unit)),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Canvas(Modifier.fillMaxWidth().height(56.dp).semantics { contentDescription = summary }) {
            val heights = profile.heights
            // Flat routes would otherwise fill the chart with a few meters of noise.
            val span = maxOf(highest - lowest, MIN_SPAN_METERS)
            val points =
                heights.mapIndexed { index, height ->
                    Offset(
                        x = size.width * index / (heights.size - 1).coerceAtLeast(1),
                        y = size.height * (1 - ((height - lowest) / span)).toFloat(),
                    )
                }
            val outline = Path().apply { points.forEachIndexed { i, p -> if (i == 0) moveTo(p.x, p.y) else lineTo(p.x, p.y) } }
            val area =
                Path().apply {
                    addPath(outline)
                    lineTo(size.width, size.height)
                    lineTo(0f, size.height)
                    close()
                }
            drawPath(area, fill)
            drawPath(outline, line, style = Stroke(width = 2.dp.toPx(), join = StrokeJoin.Round))
        }
    }
}

private const val MIN_SPAN_METERS = 50.0
