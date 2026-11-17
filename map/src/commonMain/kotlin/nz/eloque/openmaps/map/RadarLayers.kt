package nz.eloque.openmaps.map

import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import nz.eloque.openmaps.core.model.RadarFrames
import org.maplibre.compose.expressions.dsl.const
import org.maplibre.compose.layers.Anchor
import org.maplibre.compose.layers.RasterLayer
import org.maplibre.compose.sources.rememberRasterTileSource

/** Every frame of [radar] as its own layer, all loading but only [selected] visible, so stepping through them does not flicker. */
@Composable
internal fun RadarLayers(
    radar: RadarFrames?,
    selected: Int,
) {
    if (radar == null) return
    Anchor.Below(FIRST_LABEL_LAYER) {
        radar.frames.forEachIndexed { index, frame ->
            key(frame.urlTemplate) {
                val tiles =
                    rememberRasterTileSource(listOf(frame.urlTemplate), tileSize = 256) {
                        maxZoom = radar.maxZoom
                        attributionHtml = radar.attribution
                    }
                RasterLayer(
                    id = "radar-${frame.time.epochSeconds}",
                    source = tiles,
                    opacity = const(if (index == selected) RADAR_OPACITY else 0f),
                    fadeDuration = const(kotlin.time.Duration.ZERO),
                )
            }
        }
    }
}

private const val RADAR_OPACITY = 0.75f
