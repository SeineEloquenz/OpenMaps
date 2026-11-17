package nz.eloque.openmaps.ui.layers

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import nz.eloque.openmaps.core.model.MapLayer
import nz.eloque.openmaps.core.model.SettingsStore
import nz.eloque.openmaps.ui.resources.Res
import nz.eloque.openmaps.ui.resources.back
import nz.eloque.openmaps.ui.resources.close
import nz.eloque.openmaps.ui.resources.radar_arrows
import nz.eloque.openmaps.ui.resources.radar_arrows_detail
import nz.eloque.openmaps.ui.resources.radar_lightning
import nz.eloque.openmaps.ui.resources.radar_lightning_detail
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject

/** Whether [LayerOptions] has anything to show for this layer. */
internal val MapLayer.hasOptions: Boolean
    get() = this == MapLayer.RAIN_RADAR

/** The settings of one map layer, opened by long pressing its tile. */
@Composable
internal fun LayerOptions(
    layer: MapLayer,
    onBack: () -> Unit,
    onDismiss: () -> Unit,
) {
    Column(Modifier.padding(bottom = 32.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 12.dp)) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(Res.string.back))
            }
            Text(
                stringResource(layer.label),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.weight(1f).padding(start = 4.dp),
            )
            IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, contentDescription = stringResource(Res.string.close)) }
        }
        when (layer) {
            MapLayer.RAIN_RADAR -> RadarOptionsList()
            else -> {}
        }
    }
}

@Composable
private fun RadarOptionsList() {
    val settings = koinInject<SettingsStore>()
    val options by settings.radarOptions.collectAsStateWithLifecycle()
    OptionSwitch(Res.string.radar_arrows, Res.string.radar_arrows_detail, options.arrows) {
        settings.setRadarOptions(options.copy(arrows = it))
    }
    OptionSwitch(Res.string.radar_lightning, Res.string.radar_lightning_detail, options.lightning) {
        settings.setRadarOptions(options.copy(lightning = it))
    }
}

@Composable
private fun OptionSwitch(
    title: StringResource,
    detail: StringResource,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
) {
    ListItem(
        headlineContent = { Text(stringResource(title)) },
        supportingContent = { Text(stringResource(detail)) },
        trailingContent = { Switch(checked = checked, onCheckedChange = null) },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier.padding(horizontal = 12.dp).toggleable(checked, role = Role.Switch, onValueChange = onChange),
    )
}
