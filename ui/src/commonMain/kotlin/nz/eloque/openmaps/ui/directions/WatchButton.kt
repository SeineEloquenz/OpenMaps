package nz.eloque.openmaps.ui.directions

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.TrackChanges
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import nz.eloque.openmaps.core.model.Itinerary
import nz.eloque.openmaps.core.model.TripWatcher
import nz.eloque.openmaps.core.model.toWatchedTrip
import nz.eloque.openmaps.ui.components.rememberNotificationPermissionRequest
import nz.eloque.openmaps.ui.resources.Res
import nz.eloque.openmaps.ui.resources.reminder_needs_notifications
import nz.eloque.openmaps.ui.resources.watch_started
import nz.eloque.openmaps.ui.resources.watch_trip
import nz.eloque.openmaps.ui.resources.watching_trip
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import kotlin.time.Clock

/** Starts or stops background checks of [itinerary] for delays, cancellations and platform changes. */
@Composable
fun WatchButton(
    itinerary: Itinerary,
    destinationName: String,
    snackbar: SnackbarHostState,
    modifier: Modifier = Modifier,
) {
    val watcher = koinInject<TripWatcher>()
    val watched by watcher.watched.collectAsState()
    val requestPermission = rememberNotificationPermissionRequest()
    val scope = rememberCoroutineScope()
    if (itinerary.id in watched) {
        FilledTonalButton(onClick = { watcher.stop(itinerary.id) }, modifier = modifier) {
            Icon(Icons.Default.TrackChanges, contentDescription = null)
            Text(stringResource(Res.string.watching_trip), modifier = Modifier.padding(start = 8.dp))
        }
        return
    }
    val trip = itinerary.toWatchedTrip(destinationName, Clock.System.now()) ?: return
    if (trip.arrival < Clock.System.now()) return
    OutlinedButton(
        onClick = {
            requestPermission { granted ->
                scope.launch {
                    if (granted) {
                        watcher.watch(trip.copy(checkedAt = Clock.System.now()))
                        snackbar.showSnackbar(getString(Res.string.watch_started))
                    } else {
                        snackbar.showSnackbar(getString(Res.string.reminder_needs_notifications))
                    }
                }
            }
        },
        modifier = modifier,
    ) {
        Icon(Icons.Default.TrackChanges, contentDescription = null)
        Text(stringResource(Res.string.watch_trip), modifier = Modifier.padding(start = 8.dp))
    }
}
