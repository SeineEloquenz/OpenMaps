package nz.eloque.openmaps.map

import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import nz.eloque.openmaps.core.model.RadarFrames
import nz.eloque.openmaps.core.model.SatelliteImage
import org.maplibre.compose.expressions.dsl.const
import org.maplibre.compose.layers.Anchor
import org.maplibre.compose.layers.RasterLayer
import org.maplibre.compose.sources.rememberRasterTileSource

/** Every frame of [radar] as its own layer, all loading but only [selected] visible, so stepping through them does not flicker. */
@Composable
internal fun RadarLayers(
    radar: RadarFrames?,
    selected: Int,
    dark: Boolean,
) {
    if (radar == null) return
    Anchor.Below(FIRST_LABEL_LAYER) {
        radar.frames.forEachIndexed { index, frame ->
            val url = if (dark) frame.darkUrlTemplate else frame.urlTemplate
            key(url) {
                val tiles =
                    rememberRasterTileSource(listOf(url), tileSize = 256) {
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

/** The satellite picture of the clouds, under the radar and the base map's labels. */
@Composable
internal fun CloudLayer(clouds: SatelliteImage?) {
    if (clouds == null) return
    Anchor.Below(FIRST_LABEL_LAYER) {
        key(clouds.urlTemplate) {
            val tiles =
                rememberRasterTileSource(listOf(clouds.urlTemplate), tileSize = 256) {
                    maxZoom = clouds.maxZoom
                    attributionHtml = CLOUD_ATTRIBUTION
                }
            RasterLayer(id = "clouds", source = tiles, opacity = const(CLOUD_OPACITY))
        }
    }
}

private const val RADAR_OPACITY = 0.75f
private const val CLOUD_OPACITY = 0.85f
private const val CLOUD_ATTRIBUTION = "Satellite: NOAA GMGSI via LibreWXR"
