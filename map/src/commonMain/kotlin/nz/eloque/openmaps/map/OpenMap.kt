package nz.eloque.openmaps.map

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.JsonPrimitive
import nz.eloque.openmaps.core.model.LatLng
import org.maplibre.compose.interaction.ClickResult
import org.maplibre.compose.interaction.MapInteractions
import org.maplibre.compose.map.MaplibreMap
import org.maplibre.compose.overlay.DisappearingCompassButton
import org.maplibre.compose.overlay.DisappearingScaleBar
import org.maplibre.compose.overlay.MapOverlay
import org.maplibre.compose.overlay.MaplibreLogo

/**
 * [contentPadding] is the area covered by UI on top of the map. Controls and the camera center stay clear of it.
 * The logo and attribution keep clear of [ornamentPadding] only, so they can sit below buttons over the map.
 */
@Composable
fun OpenMap(
    controller: MapController,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(),
    ornamentPadding: PaddingValues = contentPadding,
) {
    var centeredOnFirstFix by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(controller) {
        if (centeredOnFirstFix) return@LaunchedEffect
        snapshotFlow { controller.hasLocationFix }.first { it }
        centeredOnFirstFix = controller.cameraPlaced || controller.centerOnUser()
    }

    LaunchedEffect(controller) {
        snapshotFlow { controller.mapState.style.loadState }.collect {
            BaseStylePlaceLayers.forEach { id ->
                controller.mapState.style.layers[id]
                    ?.asMutable
                    ?.setLayoutProperty("visibility", JsonPrimitive("none"))
            }
        }
    }

    val interactions =
        remember(controller) {
            MapInteractions {
                callbacks {
                    longClick {
                        onEvent { event ->
                            val position = event.position
                            val handler = controller.onLongPress
                            if (position == null || handler == null) {
                                ClickResult.Pass
                            } else {
                                // The position can lie in a repeated world copy, outside ±180° longitude.
                                val longitude = ((position.longitude + 540) % 360 + 360) % 360 - 180
                                handler(LatLng(position.latitude, longitude))
                                ClickResult.Consume
                            }
                        }
                    }
                }
            }
        }

    MaplibreMap(
        modifier = modifier,
        state = controller.mapState,
        viewportInsets = contentPadding,
        interactions = interactions,
    ) {
        if (controller.measuring != null) Crosshair(Modifier.padding(contentPadding))
        Box(
            Modifier
                .fillMaxSize()
                .padding(contentPadding)
                .consumeWindowInsets(contentPadding)
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(MapOverlay.Spacing),
        ) {
            DisappearingScaleBar(
                metersPerDp = { controller.mapState.viewport?.metersPerDpAtCenter ?: 0.0 },
                zoom = { controller.mapState.cameraPosition.zoom },
                modifier = Modifier.align(Alignment.TopStart),
            )
            DisappearingCompassButton(Modifier.align(Alignment.TopEnd))
            (controller.floatingAction ?: controller.layersAction)?.let { MapActionButton(it, Modifier.align(Alignment.TopEnd)) }
        }
        Box(
            Modifier
                .fillMaxSize()
                .padding(ornamentPadding)
                .consumeWindowInsets(ornamentPadding)
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(MapOverlay.Spacing),
        ) {
            MaplibreLogo(Modifier.align(Alignment.BottomStart))
            MapAttribution(Modifier.align(Alignment.BottomEnd))
        }
    }
}
