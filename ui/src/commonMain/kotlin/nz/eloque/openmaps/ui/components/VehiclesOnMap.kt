package nz.eloque.openmaps.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import nz.eloque.openmaps.core.model.GeoBounds
import nz.eloque.openmaps.core.model.LiveVehicles
import nz.eloque.openmaps.core.model.TransportMode
import nz.eloque.openmaps.core.model.VehicleRun
import nz.eloque.openmaps.map.MapController
import nz.eloque.openmaps.map.VehicleMarker
import org.koin.compose.koinInject
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

/** Moves transit vehicles across the map while [on] and zoomed in, asking for new runs only when they run out or the view leaves them. */
@Composable
fun VehiclesOnMap(
    map: MapController,
    on: Boolean,
    onOpenTrip: (tripId: String, name: String) -> Unit,
) {
    val source = koinInject<LiveVehicles>()
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(map, onOpenTrip) { map.onVehicleClick = { onOpenTrip(it.tripId, it.name) } }
    LaunchedEffect(map, on) {
        map.vehicles = emptyList()
        if (!on) return@LaunchedEffect
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            var runs = emptyList<VehicleRun>()
            var area: GeoBounds? = null
            var until = Instant.DISTANT_PAST
            var fetched = Instant.DISTANT_PAST
            var failed = Instant.DISTANT_PAST
            snapshotFlow { if (map.zoom < MIN_ZOOM) null else map.visibleBounds?.let { it to map.isMoving } }
                .distinctUntilChanged()
                .collectLatest { view ->
                    if (view == null) {
                        map.vehicles = emptyList()
                        return@collectLatest
                    }
                    val (visible, moving) = view
                    while (true) {
                        val now = Clock.System.now()
                        val covered = area?.let { it.contains(visible) } == true
                        if (!moving && (!covered || now - fetched >= REFRESH) && now - failed >= RETRY) {
                            val padded = visible.padded()
                            try {
                                runs = source.runs(padded, map.zoom.toInt(), now, now + WINDOW)
                                area = padded
                                fetched = now
                                until = now + WINDOW
                            } catch (e: CancellationException) {
                                throw e
                            } catch (_: Exception) {
                                failed = now
                            }
                        }
                        map.vehicles = if (now < until) runs.markersAt(now, visible.padded()) else emptyList()
                        delay(TICK)
                    }
                }
        }
    }
}

private fun List<VehicleRun>.markersAt(
    time: Instant,
    area: GeoBounds,
): List<VehicleMarker> =
    groupBy { it.tripId }.values.mapNotNull { runs ->
        val moving = runs.firstNotNullOfOrNull { run -> run.positionAt(time)?.let { run to it } }
        val (run, location) =
            moving ?: runs.sortedBy { it.departure }.zipWithNext().firstOrNull { (before, after) ->
                time in before.arrival..after.departure
            }?.let { (before, _) -> before to before.path.lastOrNull() } ?: return@mapNotNull null
        if (location == null || location !in area) return@mapNotNull null
        VehicleMarker(run.tripId, run.name, location, run.color?.let(::Color) ?: run.mode.defaultColor)
    }

private operator fun GeoBounds.contains(other: GeoBounds) =
    other.south >= south && other.north <= north && other.west >= west && other.east <= east

/** These bounds grown by half their size on every side, so small pans stay within what was fetched. */
private fun GeoBounds.padded(): GeoBounds {
    val height = (north - south) / 2
    val width = (east - west) / 2
    return GeoBounds(south - height, west - width, north + height, east + width)
}

private val TransportMode.defaultColor: Color
    get() =
        when (this) {
            TransportMode.TRAM -> Color(0xFFD32F2F)
            TransportMode.SUBWAY -> Color(0xFF1565C0)
            TransportMode.SUBURBAN -> Color(0xFF2E7D32)
            TransportMode.REGIONAL_RAIL, TransportMode.LONG_DISTANCE_RAIL, TransportMode.HIGHSPEED_RAIL, TransportMode.NIGHT_RAIL ->
                Color(0xFF455A64)
            TransportMode.FERRY -> Color(0xFF0277BD)
            else -> Color(0xFF7B1FA2)
        }

private const val MIN_ZOOM = 12.0
private val WINDOW = 6.minutes
private val REFRESH = 3.minutes
private val TICK = 1.seconds
private val RETRY = 30.seconds
