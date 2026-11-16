package nz.eloque.openmaps.ui.guidance

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Straight
import androidx.compose.material.icons.filled.TurnLeft
import androidx.compose.material.icons.filled.TurnRight
import androidx.compose.material.icons.filled.TurnSharpLeft
import androidx.compose.material.icons.filled.TurnSharpRight
import androidx.compose.material.icons.filled.TurnSlightLeft
import androidx.compose.material.icons.filled.TurnSlightRight
import androidx.compose.material.icons.filled.UTurnLeft
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import nz.eloque.openmaps.core.model.DistanceUnit
import nz.eloque.openmaps.core.model.Lane
import nz.eloque.openmaps.core.model.LaneDirection
import kotlin.math.roundToInt

/** The lanes at the next junction, the recommended ones bright and those off the route faint. */
@Composable
fun LaneRow(
    lanes: List<Lane>,
    modifier: Modifier = Modifier,
) {
    val color = LocalContentColor.current
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        lanes.forEach { lane ->
            val direction = lane.chosen ?: lane.directions.firstOrNull() ?: LaneDirection.STRAIGHT
            val alpha =
                when {
                    lane.recommended -> 1f
                    lane.valid -> VALID_ALPHA
                    else -> INVALID_ALPHA
                }
            Icon(direction.icon, contentDescription = null, tint = color.copy(alpha = alpha), modifier = Modifier.size(28.dp))
        }
    }
}

private val LaneDirection.icon: ImageVector
    get() =
        when (this) {
            LaneDirection.SHARP_LEFT -> Icons.Default.TurnSharpLeft
            LaneDirection.LEFT -> Icons.Default.TurnLeft
            LaneDirection.SLIGHT_LEFT -> Icons.Default.TurnSlightLeft
            LaneDirection.STRAIGHT -> Icons.Default.Straight
            LaneDirection.SLIGHT_RIGHT -> Icons.Default.TurnSlightRight
            LaneDirection.RIGHT -> Icons.Default.TurnRight
            LaneDirection.SHARP_RIGHT -> Icons.Default.TurnSharpRight
            LaneDirection.U_TURN -> Icons.Default.UTurnLeft
        }

/** A round speed limit sign, filled red while [speeding]. Miles per hour when [unit] is imperial. */
@Composable
fun SpeedLimitSign(
    kph: Int,
    speeding: Boolean,
    unit: DistanceUnit,
    modifier: Modifier = Modifier,
) {
    val value = if (unit == DistanceUnit.IMPERIAL) (kph / KPH_PER_MPH / 5).roundToInt() * 5 else kph
    Box(
        modifier
            .size(56.dp)
            .background(if (speeding) SignRed else Color.White, CircleShape)
            .border(6.dp, SignRed, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            value.toString(),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = if (speeding) Color.White else Color.Black,
        )
    }
}

private const val VALID_ALPHA = 0.6f
private const val INVALID_ALPHA = 0.25f
private const val KPH_PER_MPH = 1.609344
private val SignRed = Color(0xFFD32F2F)
