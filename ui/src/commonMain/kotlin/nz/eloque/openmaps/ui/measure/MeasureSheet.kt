package nz.eloque.openmaps.ui.measure

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.AddLocation
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import nz.eloque.openmaps.core.model.LatLng
import nz.eloque.openmaps.core.model.SettingsStore
import nz.eloque.openmaps.map.MapController
import nz.eloque.openmaps.ui.components.formatDistance
import nz.eloque.openmaps.ui.navigation.MeasureKey
import nz.eloque.openmaps.ui.resources.Res
import nz.eloque.openmaps.ui.resources.measure_add_point
import nz.eloque.openmaps.ui.resources.measure_hint
import nz.eloque.openmaps.ui.resources.measure_title
import nz.eloque.openmaps.ui.resources.measure_undo
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject

/** Measures a line from the start point through each added point to the map center. */
@Composable
fun MeasureSheet(
    key: MeasureKey,
    map: MapController,
) {
    val unit by koinInject<SettingsStore>().distanceUnit.collectAsStateWithLifecycle()
    var coordinates by rememberSaveable { mutableStateOf(listOf(key.latitude, key.longitude)) }
    val points = coordinates.chunked(2).map { (lat, lon) -> LatLng(lat, lon) }
    DisposableEffect(map, points) {
        map.measuring = points
        onDispose { map.measuring = null }
    }
    LaunchedEffect(key) { map.show(points.first(), zoomIn = false) }
    val line = points + map.center
    val meters = line.zipWithNext().sumOf { (a, b) -> a.distanceTo(b) }

    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(stringResource(Res.string.measure_title), style = MaterialTheme.typography.titleMedium)
        Text(formatDistance(meters, unit), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Text(stringResource(Res.string.measure_hint), color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { coordinates = coordinates + listOf(map.center.latitude, map.center.longitude) }) {
                Icon(Icons.Default.AddLocation, contentDescription = null)
                Text(stringResource(Res.string.measure_add_point), modifier = Modifier.padding(start = 8.dp))
            }
            OutlinedButton(onClick = { coordinates = coordinates.dropLast(2) }, enabled = points.size > 1) {
                Icon(Icons.AutoMirrored.Filled.Undo, contentDescription = null)
                Text(stringResource(Res.string.measure_undo), modifier = Modifier.padding(start = 8.dp))
            }
        }
    }
}
