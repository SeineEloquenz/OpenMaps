package nz.eloque.openmaps.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import nz.eloque.openmaps.core.model.SearchResult
import nz.eloque.openmaps.core.model.TransitRepository
import nz.eloque.openmaps.map.MapController
import nz.eloque.openmaps.map.STOPS_MIN_ZOOM
import org.koin.compose.koinInject

/** Shows the stops in the visible map area once zoomed in, reloading after the camera settles. */
@Composable
fun StopsOnMap(
    map: MapController,
    onOpenStop: (SearchResult.Stop) -> Unit,
) {
    val transit = koinInject<TransitRepository>()
    DisposableEffect(map, onOpenStop) {
        map.onStopClick = onOpenStop
        onDispose { map.onStopClick = null }
    }
    LaunchedEffect(map) {
        snapshotFlow { if (map.isMoving || map.zoom < STOPS_MIN_ZOOM) null else map.visibleBounds }
            .distinctUntilChanged()
            .collectLatest { bounds ->
                if (bounds == null) return@collectLatest
                delay(SETTLE_MILLIS)
                try {
                    map.stops = transit.stopsIn(bounds)
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    // The stops already shown stay until the next successful load.
                }
            }
    }
}

private const val SETTLE_MILLIS = 400L
