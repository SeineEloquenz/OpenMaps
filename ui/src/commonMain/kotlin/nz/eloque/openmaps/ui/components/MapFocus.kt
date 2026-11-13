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
    zoomIn: Boolean = true,
) {
    LaunchedEffect(location) { map.show(location, zoomIn) }
    DisposableEffect(location) {
        // Compared by identity, so a sheet being replaced at the same coordinates does not clear its successor's marker.
        val own = location.copy()
        map.marker = own
        onDispose { if (map.marker === own) map.marker = null }
    }
}
