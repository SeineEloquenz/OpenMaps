package nz.eloque.openmaps.ui.directions

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import nz.eloque.openmaps.core.model.Leg
import nz.eloque.openmaps.map.MapController
import nz.eloque.openmaps.ui.components.LineChip
import nz.eloque.openmaps.ui.components.LoadState
import nz.eloque.openmaps.ui.components.LoadStateContent
import nz.eloque.openmaps.ui.components.rememberNow
import nz.eloque.openmaps.ui.components.withoutLinePrefix
import nz.eloque.openmaps.ui.navigation.TripKey
import nz.eloque.openmaps.ui.resources.Res
import nz.eloque.openmaps.ui.resources.towards
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

/** One vehicle's run with every stop. Stops already passed are dimmed and the stop it was opened from is emphasized. */
@Composable
fun TripSheet(
    trip: TripKey,
    map: MapController,
) {
    val viewModel = koinViewModel<TripViewModel> { parametersOf(trip.tripId) }
    val state by viewModel.trip.collectAsStateWithLifecycle()
    val now by rememberNow()
    val itinerary = (state as? LoadState.Loaded)?.value
    ShowItineraryOnMap(map, itinerary)

    val leg = itinerary?.legs?.filterIsInstance<Leg.Transit>()?.firstOrNull()
    if (leg == null) {
        LoadStateContent(if (itinerary != null) LoadState.Failed else state, onRetry = viewModel::retry) {}
        return
    }
    val stops = listOf(leg.from) + leg.intermediateStops + leg.to
    LazyColumn(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 8.dp)) {
                LineChip(leg.line)
                leg.headsign?.let {
                    Text(
                        stringResource(Res.string.towards, it.withoutLinePrefix(leg.line)),
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(start = 12.dp),
                    )
                }
            }
        }
        items(leg.alerts) { alert ->
            Row(verticalAlignment = Alignment.Top, modifier = Modifier.padding(bottom = 6.dp)) {
                Icon(
                    Icons.Default.Warning,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(18.dp),
                )
                Text(alert.header, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(start = 8.dp))
            }
        }
        item { HorizontalDivider(Modifier.padding(bottom = 8.dp)) }
        items(stops) { stop ->
            val time = stop.departure ?: stop.arrival ?: return@items
            StopLine(time, stop, emphasized = stop.name == trip.stopName, dimmed = time.actual < now)
        }
    }
}
