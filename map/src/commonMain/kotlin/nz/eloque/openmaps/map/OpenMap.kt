package nz.eloque.openmaps.map

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import kotlinx.coroutines.flow.first
import org.maplibre.compose.map.MaplibreMap

/** [contentPadding] is the area covered by UI on top of the map. Controls and the camera center stay clear of it. */
@Composable
fun OpenMap(
    controller: MapController,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(),
) {
    var centeredOnFirstFix by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(controller) {
        if (centeredOnFirstFix) return@LaunchedEffect
        snapshotFlow { controller.hasLocationFix }.first { it }
        centeredOnFirstFix = controller.centerOnUser()
    }

    MaplibreMap(
        modifier = modifier,
        state = controller.mapState,
        viewportInsets = contentPadding,
    )
}
