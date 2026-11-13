package nz.eloque.openmaps.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import nz.eloque.openmaps.core.model.LatLng
import nz.eloque.openmaps.map.MapController

/** Marks [location] on the map and moves the camera there while this is composed. */
@Composable
fun FocusOnMap(
    map: MapController,
    location: LatLng,
) {
    LaunchedEffect(location) { map.show(location) }
    DisposableEffect(location) {
        map.marker = location
        onDispose { if (map.marker == location) map.marker = null }
    }
}
