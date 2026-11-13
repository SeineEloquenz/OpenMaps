package nz.eloque.openmaps.ui.directions

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import nz.eloque.openmaps.core.model.EventTime
import nz.eloque.openmaps.core.model.Itinerary
import nz.eloque.openmaps.core.model.Leg
import nz.eloque.openmaps.core.model.TransportMode
import nz.eloque.openmaps.core.model.Waypoint
import nz.eloque.openmaps.map.MapController
import nz.eloque.openmaps.ui.components.LineChip
import nz.eloque.openmaps.ui.components.clockTime
import nz.eloque.openmaps.ui.components.formatDistance
import nz.eloque.openmaps.ui.components.formatDuration
import nz.eloque.openmaps.ui.components.icon
import nz.eloque.openmaps.ui.components.withoutLinePrefix
import nz.eloque.openmaps.ui.navigation.RouteKey
import nz.eloque.openmaps.ui.resources.Res
import nz.eloque.openmaps.ui.resources.bike_leg
import nz.eloque.openmaps.ui.resources.drive_leg
import nz.eloque.openmaps.ui.resources.platform
import nz.eloque.openmaps.ui.resources.route_expired
import nz.eloque.openmaps.ui.resources.stops_count
import nz.eloque.openmaps.ui.resources.towards
import nz.eloque.openmaps.ui.resources.walk_leg
import nz.eloque.openmaps.ui.resources.your_location
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject

@Composable
fun RouteSheet(
    route: RouteKey,
    map: MapController,
) {
    val itinerary = koinInject<ItineraryCache>()[route.itineraryId]
    ShowItineraryOnMap(map, itinerary)
    if (itinerary == null) {
        Text(
            stringResource(Res.string.route_expired),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(16.dp),
        )
        return
    }
    RouteTimeline(itinerary, route.destinationName ?: stringResource(Res.string.your_location))
}

@Composable
private fun RouteTimeline(
    itinerary: Itinerary,
    destinationName: String,
) {
    LazyColumn(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 8.dp)) {
                Text(
                    "${clockTime(itinerary.start)} – ${clockTime(itinerary.end)}",
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.weight(1f),
                )
                Text(formatDuration(itinerary.duration), style = MaterialTheme.typography.titleMedium)
            }
            HorizontalDivider()
        }
        items(itinerary.legs) { leg ->
            when (leg) {
                is Leg.Transit -> TransitLegItem(leg)
                is Leg.Street -> StreetLegItem(leg)
            }
        }
        itinerary.legs.lastOrNull()?.let { last ->
            if (last is Leg.Street) {
                item { StopLine(last.end, last.to.copy(name = destinationName), emphasized = true) }
            }
        }
    }
}

@Composable
private fun StreetLegItem(leg: Leg.Street) {
    val duration = formatDuration(leg.end.actual - leg.start.actual)
    val label = listOfNotNull(duration, leg.distanceMeters?.let(::formatDistance)).joinToString(" · ")
    Row(Modifier.padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(leg.mode.icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            stringResource(
                when (leg.mode) {
                    TransportMode.BIKE -> Res.string.bike_leg
                    TransportMode.CAR -> Res.string.drive_leg
                    else -> Res.string.walk_leg
                },
                label,
            ),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 16.dp),
        )
    }
}

@Composable
private fun TransitLegItem(leg: Leg.Transit) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        StopLine(leg.start, leg.from, emphasized = true)
        Row(verticalAlignment = Alignment.CenterVertically) {
            LineChip(leg.line)
            leg.headsign?.let {
                Text(
                    stringResource(Res.string.towards, it.withoutLinePrefix(leg.line)),
                    modifier = Modifier.padding(start = 12.dp),
                    maxLines = 1,
                )
            }
        }
        leg.alerts.forEach { alert ->
            Row(verticalAlignment = Alignment.Top) {
                Icon(
                    Icons.Default.Warning,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(18.dp),
                )
                Text(alert.header, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(start = 8.dp))
            }
        }
        val stops = leg.intermediateStops.size + 1
        Row(
            Modifier.clickable(enabled = leg.intermediateStops.isNotEmpty()) { expanded = !expanded }.padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "${stringResource(Res.string.stops_count, stops)} · ${formatDuration(leg.end.actual - leg.start.actual)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (leg.intermediateStops.isNotEmpty()) {
                Icon(if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore, contentDescription = null)
            }
        }
        if (expanded) {
            leg.intermediateStops.forEach { stop ->
                (stop.departure ?: stop.arrival)?.let { StopLine(it, stop, emphasized = false) }
            }
        }
        StopLine(leg.end, leg.to, emphasized = true)
    }
}

@Composable
private fun StopLine(
    time: EventTime,
    waypoint: Waypoint,
    emphasized: Boolean,
) {
    val delayed = time.isRealTime && time.delay.inWholeMinutes >= 1
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 2.dp)) {
        Column(Modifier.width(56.dp)) {
            Text(
                clockTime(time.actual),
                fontWeight = if (emphasized) FontWeight.Bold else null,
                color = if (delayed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                textDecoration = if (waypoint.isCancelled) TextDecoration.LineThrough else null,
            )
            if (delayed) {
                Text(
                    clockTime(time.scheduled),
                    style = MaterialTheme.typography.labelSmall,
                    textDecoration = TextDecoration.LineThrough,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Column(Modifier.weight(1f)) {
            Text(
                waypoint.name,
                fontWeight = if (emphasized) FontWeight.SemiBold else null,
                style = if (emphasized) MaterialTheme.typography.bodyLarge else MaterialTheme.typography.bodyMedium,
            )
            if (emphasized) {
                waypoint.platform?.let {
                    Text(
                        stringResource(Res.string.platform, it),
                        style = MaterialTheme.typography.bodySmall,
                        color =
                            if (waypoint.scheduledPlatform != null && waypoint.scheduledPlatform != it) {
                                MaterialTheme.colorScheme.error
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                    )
                }
            }
        }
    }
}
