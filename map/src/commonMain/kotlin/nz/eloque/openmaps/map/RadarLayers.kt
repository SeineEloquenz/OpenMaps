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
                    rememberRasterTileSource(listOf(url), tileSize = radar.tileSize) {
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

/** Satellite pictures of the clouds under the radar and the base map's labels, all loading but only [selected] visible. */
@Composable
internal fun CloudLayers(
    clouds: List<SatelliteImage>,
    selected: Int,
) {
    Anchor.Below(FIRST_LABEL_LAYER) {
        clouds.forEachIndexed { index, image ->
            key(image.urlTemplate) {
                val tiles =
                    rememberRasterTileSource(listOf(image.urlTemplate), tileSize = image.tileSize) {
                        maxZoom = image.maxZoom
                        attributionHtml = CLOUD_ATTRIBUTION
                    }
                RasterLayer(
                    id = "clouds-${image.time.epochSeconds}",
                    source = tiles,
                    opacity = const(if (index == selected) CLOUD_OPACITY else 0f),
                    fadeDuration = const(kotlin.time.Duration.ZERO),
                )
            }
        }
    }
}

/**
 * The cloud pictures to load and which of them to show. Alongside the radar that is the newest one taken before the radar
 * frame shown, so the two match in time, and only pictures from the radar's hour load. On their own all of them load.
 */
internal fun cloudsToShow(
    clouds: List<SatelliteImage>,
    radar: RadarFrames?,
    radarFrame: Int,
    cloudFrame: Int,
): Pair<List<SatelliteImage>, Int> {
    if (clouds.isEmpty()) return emptyList<SatelliteImage>() to 0
    val radarTimes = radar?.frames?.map { it.time }
    if (radarTimes.isNullOrEmpty()) return clouds to cloudFrame.coerceIn(clouds.indices)
    val first = clouds.indexOfLast { it.time <= radarTimes.first() }.coerceAtLeast(0)
    val relevant = clouds.subList(first, clouds.size)
    val shown = radarTimes.getOrNull(radarFrame) ?: radarTimes.last()
    return relevant to relevant.indexOfLast { it.time <= shown }.coerceAtLeast(0)
}

private const val RADAR_OPACITY = 0.75f
private const val CLOUD_OPACITY = 0.85f
private const val CLOUD_ATTRIBUTION = "Satellite: NOAA GMGSI via LibreWXR"
