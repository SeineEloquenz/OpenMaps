package nz.eloque.openmaps.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsBike
import androidx.compose.material.icons.automirrored.filled.DirectionsWalk
import androidx.compose.material.icons.filled.DirectionsBoat
import androidx.compose.material.icons.filled.DirectionsBus
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.DirectionsRailway
import androidx.compose.material.icons.filled.DirectionsSubway
import androidx.compose.material.icons.filled.Flight
import androidx.compose.material.icons.filled.Train
import androidx.compose.material.icons.filled.Tram
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import nz.eloque.openmaps.core.model.Departure
import nz.eloque.openmaps.core.model.Line
import nz.eloque.openmaps.core.model.TransportMode
import nz.eloque.openmaps.ui.resources.Res
import nz.eloque.openmaps.ui.resources.cancelled
import nz.eloque.openmaps.ui.resources.minutes
import nz.eloque.openmaps.ui.resources.now
import nz.eloque.openmaps.ui.resources.platform
import org.jetbrains.compose.resources.stringResource
import kotlin.time.Instant

val TransportMode.icon: ImageVector
    get() =
        when (this) {
            TransportMode.WALK -> Icons.AutoMirrored.Filled.DirectionsWalk
            TransportMode.BIKE -> Icons.AutoMirrored.Filled.DirectionsBike
            TransportMode.CAR -> Icons.Default.DirectionsCar
            TransportMode.BUS, TransportMode.COACH -> Icons.Default.DirectionsBus
            TransportMode.TRAM, TransportMode.CABLE_CAR, TransportMode.FUNICULAR -> Icons.Default.Tram
            TransportMode.SUBWAY -> Icons.Default.DirectionsSubway
            TransportMode.SUBURBAN, TransportMode.REGIONAL_RAIL -> Icons.Default.DirectionsRailway
            TransportMode.LONG_DISTANCE_RAIL, TransportMode.HIGHSPEED_RAIL, TransportMode.NIGHT_RAIL -> Icons.Default.Train
            TransportMode.FERRY -> Icons.Default.DirectionsBoat
            TransportMode.AIRPLANE -> Icons.Default.Flight
            TransportMode.OTHER -> Icons.Default.DirectionsBus
        }

@Composable
fun LineChip(
    line: Line,
    modifier: Modifier = Modifier,
) {
    val background = line.color?.let { Color(it) } ?: MaterialTheme.colorScheme.secondaryContainer
    val content =
        line.textColor?.let { Color(it) }
            ?: if (line.color == null) {
                MaterialTheme.colorScheme.onSecondaryContainer
            } else if (background.luminance() > 0.5f) {
                Color.Black
            } else {
                Color.White
            }
    Row(
        modifier =
            modifier
                .widthIn(min = 72.dp)
                .background(background, RoundedCornerShape(6.dp))
                .padding(horizontal = 6.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterHorizontally),
    ) {
        Icon(line.mode.icon, contentDescription = null, tint = content, modifier = Modifier.padding(vertical = 1.dp))
        if (line.label.isNotEmpty()) {
            Text(
                line.label,
                color = content,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
            )
        }
    }
}

data class DepartureGroup(
    val line: Line,
    val headsign: String?,
    val departures: List<Departure>,
)

/** Groups by line and direction like a station board, keeping the order of the first departure. */
fun groupDepartures(departures: List<Departure>): List<DepartureGroup> =
    departures
        .groupBy { Triple(it.line.label, it.line.mode, it.headsign) }
        .values
        .map { DepartureGroup(it.first().line, it.first().headsign, it) }

@Composable
fun DepartureGroupRow(
    group: DepartureGroup,
    now: Instant,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth().padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        LineChip(group.line)
        Column(Modifier.weight(1f)) {
            Text(
                group.headsign?.withoutLinePrefix(group.line) ?: group.line.longName.orEmpty(),
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            group.departures.first().platform?.let {
                Text(
                    stringResource(Res.string.platform, it),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            group.departures.take(3).forEachIndexed { index, departure ->
                DepartureTime(departure, now, emphasized = index == 0)
            }
        }
    }
}

@Composable
private fun DepartureTime(
    departure: Departure,
    now: Instant,
    emphasized: Boolean,
) {
    val color =
        when {
            departure.isCancelled -> MaterialTheme.colorScheme.error
            !departure.time.isRealTime -> MaterialTheme.colorScheme.onSurface
            departure.time.delay.inWholeMinutes >= 1 -> MaterialTheme.colorScheme.error
            else -> OnTime
        }
    Text(
        if (departure.isCancelled) stringResource(Res.string.cancelled) else countdown(departure.time.actual, now),
        color = color,
        style = if (emphasized) MaterialTheme.typography.titleSmall else MaterialTheme.typography.bodyMedium,
        textDecoration = if (departure.isCancelled) TextDecoration.LineThrough else null,
    )
}

private val OnTime = Color(0xFF2E9E5B)

/** Some feeds repeat the line in the headsign, such as `6 Arbeitsagentur` for tram 6. */
private fun String.withoutLinePrefix(line: Line): String =
    line.label
        .takeIf { it.isNotEmpty() && startsWith("$it ") }
        ?.let { removePrefix("$it ") }
        ?: this

@Composable
fun countdown(
    time: Instant,
    now: Instant,
): String {
    val minutes = ((time - now).inWholeSeconds + 30) / 60
    return when {
        minutes <= 0 -> stringResource(Res.string.now)
        minutes < 60 -> stringResource(Res.string.minutes, minutes.toInt())
        else -> clockTime(time)
    }
}

fun clockTime(time: Instant): String {
    val local = time.toLocalDateTime(TimeZone.currentSystemDefault())
    return "${local.hour.toString().padStart(2, '0')}:${local.minute.toString().padStart(2, '0')}"
}
