package nz.eloque.openmaps.ui.layers

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsBike
import androidx.compose.material.icons.filled.Construction
import androidx.compose.material.icons.filled.Hiking
import androidx.compose.material.icons.filled.LocationCity
import androidx.compose.material.icons.filled.Terrain
import androidx.compose.material.icons.filled.Train
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import nz.eloque.openmaps.core.model.MapLayer
import nz.eloque.openmaps.core.model.SettingsStore
import nz.eloque.openmaps.ui.resources.Res
import nz.eloque.openmaps.ui.resources.layer_buildings
import nz.eloque.openmaps.ui.resources.layer_cycling
import nz.eloque.openmaps.ui.resources.layer_hiking
import nz.eloque.openmaps.ui.resources.layer_roadworks
import nz.eloque.openmaps.ui.resources.layer_terrain
import nz.eloque.openmaps.ui.resources.layer_transit
import nz.eloque.openmaps.ui.resources.map_layers
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject

/** Switches the optional map layers on and off. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LayersSheet(onDismiss: () -> Unit) {
    val settings = koinInject<SettingsStore>()
    val layers by settings.mapLayers.collectAsStateWithLifecycle()
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Text(
            stringResource(Res.string.map_layers),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 32.dp),
        ) {
            MapLayer.entries.forEach { layer ->
                val on = layer in layers
                FilterChip(
                    selected = on,
                    onClick = { settings.setMapLayers(if (on) layers - layer else layers + layer) },
                    label = { Text(stringResource(layer.label)) },
                    leadingIcon = { Icon(layer.icon, contentDescription = null, modifier = Modifier.size(18.dp)) },
                )
            }
        }
    }
}

private val MapLayer.label: StringResource
    get() =
        when (this) {
            MapLayer.TERRAIN -> Res.string.layer_terrain
            MapLayer.TRANSIT -> Res.string.layer_transit
            MapLayer.CYCLING -> Res.string.layer_cycling
            MapLayer.HIKING -> Res.string.layer_hiking
            MapLayer.ROADWORKS -> Res.string.layer_roadworks
            MapLayer.BUILDINGS_3D -> Res.string.layer_buildings
        }

private val MapLayer.icon: ImageVector
    get() =
        when (this) {
            MapLayer.TERRAIN -> Icons.Default.Terrain
            MapLayer.TRANSIT -> Icons.Default.Train
            MapLayer.CYCLING -> Icons.AutoMirrored.Filled.DirectionsBike
            MapLayer.HIKING -> Icons.Default.Hiking
            MapLayer.ROADWORKS -> Icons.Default.Construction
            MapLayer.BUILDINGS_3D -> Icons.Default.LocationCity
        }
