package nz.eloque.openmaps.ui.tracks

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import nz.eloque.openmaps.core.model.ElevationProfile
import nz.eloque.openmaps.core.model.SettingsStore
import nz.eloque.openmaps.core.model.Track
import nz.eloque.openmaps.core.model.TrackStore
import nz.eloque.openmaps.core.platform.Files
import nz.eloque.openmaps.map.MapController
import nz.eloque.openmaps.map.MapLine
import nz.eloque.openmaps.ui.components.formatDate
import nz.eloque.openmaps.ui.components.formatDistance
import nz.eloque.openmaps.ui.components.formatDuration
import nz.eloque.openmaps.ui.directions.ElevationChart
import nz.eloque.openmaps.ui.navigation.TrackKey
import nz.eloque.openmaps.ui.resources.Res
import nz.eloque.openmaps.ui.resources.cancel
import nz.eloque.openmaps.ui.resources.delete
import nz.eloque.openmaps.ui.resources.export_gpx
import nz.eloque.openmaps.ui.resources.name
import nz.eloque.openmaps.ui.resources.ok
import nz.eloque.openmaps.ui.resources.rename
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject

/** A saved track drawn on the map, with its length, time and climb, to export, rename or delete. */
@Composable
fun TrackSheet(
    key: TrackKey,
    map: MapController,
    onDeleted: () -> Unit,
) {
    val store = koinInject<TrackStore>()
    val files = koinInject<Files>()
    val unit by koinInject<SettingsStore>().distanceUnit.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var version by remember { mutableStateOf(0) }
    val track by produceState<Track?>(null, key, version) { value = store.track(key.id) }
    var renaming by remember { mutableStateOf(false) }

    val color = MaterialTheme.colorScheme.primary
    val points = track?.points?.map { it.location }.orEmpty()
    DisposableEffect(map, points) {
        val lines = listOf(MapLine(points, color))
        map.lines = lines
        onDispose { if (map.lines === lines) map.lines = emptyList() }
    }
    LaunchedEffect(points.size) { if (points.isNotEmpty()) map.fit(points) }

    val current = track ?: return
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(current.name, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(horizontal = 16.dp))
        Text(
            listOfNotNull(
                formatDistance(current.distanceMeters, unit),
                current.duration?.let { formatDuration(it) },
                formatDate(current.createdAt.toLocalDateTime(TimeZone.currentSystemDefault()).date),
            ).joinToString(" · "),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
        LazyRow(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            item {
                Action(Icons.Default.Share, Res.string.export_gpx) {
                    scope.launch { store.exportGpx(current.id)?.let { files.share("${current.name}.gpx", GPX_MIME_TYPE, it) } }
                }
            }
            item { Action(Icons.Default.Edit, Res.string.rename) { renaming = true } }
            item {
                Action(Icons.Default.Delete, Res.string.delete) {
                    scope.launch {
                        store.delete(current.id)
                        onDeleted()
                    }
                }
            }
        }
        current.elevation()?.let { ElevationChart(it, unit, Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) }
    }

    if (renaming) {
        var text by remember { mutableStateOf(current.name) }
        AlertDialog(
            onDismissRequest = { renaming = false },
            title = { Text(stringResource(Res.string.rename)) },
            text = {
                OutlinedTextField(value = text, onValueChange = {
                    text = it
                }, label = { Text(stringResource(Res.string.name)) }, singleLine = true)
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        renaming = false
                        scope.launch {
                            store.rename(current.id, text.trim().ifEmpty { current.name })
                            version++
                        }
                    },
                ) { Text(stringResource(Res.string.ok)) }
            },
            dismissButton = { TextButton(onClick = { renaming = false }) { Text(stringResource(Res.string.cancel)) } },
        )
    }
}

@Composable
private fun Action(
    icon: ImageVector,
    label: StringResource,
    onClick: () -> Unit,
) {
    FilledTonalButton(onClick = onClick) {
        Icon(icon, contentDescription = null)
        Text(stringResource(label), modifier = Modifier.padding(start = 8.dp))
    }
}

/** Heights along the track at even steps of its average point spacing, null when it has too few. */
private fun Track.elevation(): ElevationProfile? {
    val heights = points.mapNotNull { it.elevation }
    if (heights.size < 2 || heights.size < points.size / 2) return null
    return ElevationProfile(distanceMeters / (heights.size - 1), heights)
}

const val GPX_MIME_TYPE = "application/gpx+xml"
