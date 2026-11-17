package nz.eloque.openmaps.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.TrackChanges
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import nz.eloque.openmaps.core.model.LatLng
import nz.eloque.openmaps.core.model.SettingsStore
import nz.eloque.openmaps.core.model.TripWatchStore
import nz.eloque.openmaps.core.model.WatchedTrip
import nz.eloque.openmaps.ui.components.clockTime
import nz.eloque.openmaps.ui.components.formatDistance
import nz.eloque.openmaps.ui.components.rememberNow
import nz.eloque.openmaps.ui.resources.Res
import nz.eloque.openmaps.ui.resources.clear
import nz.eloque.openmaps.ui.resources.discard
import nz.eloque.openmaps.ui.resources.parked_car
import nz.eloque.openmaps.ui.resources.show_on_map
import nz.eloque.openmaps.ui.resources.stop_and_save
import nz.eloque.openmaps.ui.resources.walk_there
import nz.eloque.openmaps.ui.tracks.TrackRecorder
import nz.eloque.openmaps.ui.weather.WarningsDialog
import nz.eloque.openmaps.ui.weather.color
import nz.eloque.openmaps.ui.weather.rememberWarnings
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject

/** Small chips over the map for what is going on: a watched trip, a track being recorded, the parked car. */
@Composable
fun ActivePills(
    userLocation: LatLng?,
    onOpenWatched: (WatchedTrip) -> Unit,
    onOpenTrack: (Long) -> Unit,
    onShowCar: () -> Unit,
    onWalkToCar: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val now by rememberNow()
    val watched by koinInject<TripWatchStore>().trips.collectAsStateWithLifecycle(emptyList())
    val settings = koinInject<SettingsStore>()
    val parked by settings.parkedCar.collectAsStateWithLifecycle()
    val unit by settings.distanceUnit.collectAsStateWithLifecycle()
    val recorder = koinInject<TrackRecorder>()
    val recording by recorder.recording.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()

    val warnings = rememberWarnings(userLocation)
    var showWarnings by remember { mutableStateOf(false) }
    if (showWarnings && warnings.isNotEmpty()) WarningsDialog(warnings, onDismiss = { showWarnings = false })

    FlowRow(modifier, horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        warnings.firstOrNull()?.let { worst ->
            val more = if (warnings.size > 1) " +${warnings.size - 1}" else ""
            Pill(Icons.Default.Warning, worst.title + more, alert = false, onClick = { showWarnings = true }, tint = worst.severity.color)
        }
        watched.filter { it.arrival > now }.forEach { trip ->
            val leg = trip.legs.firstOrNull { it.to.expected > now } ?: trip.legs.last()
            val delay = leg.from.delay.inWholeMinutes
            val late = leg.cancelled || delay != 0L
            val text =
                "${leg.line} ${clockTime(leg.from.expected)}" +
                    if (delay > 0) {
                        " +$delay"
                    } else if (delay < 0) {
                        " $delay"
                    } else {
                        ""
                    }
            Pill(Icons.Default.TrackChanges, text, alert = late, onClick = { onOpenWatched(trip) })
        }
        recording?.let { current ->
            var open by remember { mutableStateOf(false) }
            Box {
                Pill(Icons.Default.FiberManualRecord, formatDistance(current.distanceMeters, unit), alert = true, onClick = { open = true })
                DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                    DropdownMenuItem(
                        text = { Text(stringResource(Res.string.stop_and_save)) },
                        onClick = {
                            open = false
                            scope.launch { recorder.stop()?.let(onOpenTrack) }
                        },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(Res.string.discard)) },
                        onClick = {
                            open = false
                            recorder.discard()
                        },
                    )
                }
            }
        }
        parked?.let { car ->
            var open by remember { mutableStateOf(false) }
            val text = userLocation?.let { formatDistance(it.distanceTo(car.location), unit) } ?: stringResource(Res.string.parked_car)
            Box {
                Pill(Icons.Default.DirectionsCar, text, alert = false, onClick = { open = true })
                DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                    DropdownMenuItem(
                        text = { Text(stringResource(Res.string.walk_there)) },
                        onClick = {
                            open = false
                            onWalkToCar()
                        },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(Res.string.show_on_map)) },
                        onClick = {
                            open = false
                            onShowCar()
                        },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(Res.string.clear)) },
                        onClick = {
                            open = false
                            settings.setParkedCar(null)
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun Pill(
    icon: ImageVector,
    text: String,
    alert: Boolean,
    onClick: () -> Unit,
    tint: Color = Color.Unspecified,
) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        shadowElevation = 4.dp,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(
                icon,
                contentDescription = null,
                tint = if (alert) MaterialTheme.colorScheme.error else tint,
                modifier = Modifier.size(18.dp),
            )
            Text(
                text,
                style = MaterialTheme.typography.labelLarge,
                color = if (alert) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(start = 6.dp),
            )
        }
    }
}
