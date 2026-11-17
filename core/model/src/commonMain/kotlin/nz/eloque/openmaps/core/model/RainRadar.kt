package nz.eloque.openmaps.core.model

import kotlinx.coroutines.flow.Flow
import kotlin.time.Instant

/** One radar picture as raster tiles, measured or forecast. */
data class RadarFrame(
    val time: Instant,
    val forecast: Boolean,
    /** With `{z}`, `{x}` and `{y}` placeholders, for the light map. */
    val urlTemplate: String,
    /** The same for the dark map. */
    val darkUrlTemplate: String,
)

data class RadarFrames(
    /** Oldest first. */
    val frames: List<RadarFrame>,
    val maxZoom: Int,
    val attribution: String,
) {
    /** The newest measured frame, or the first one if all are forecasts. */
    val latestObserved: Int get() = frames.indexOfLast { !it.forecast }.coerceAtLeast(0)
}

/** What the radar layer draws besides the rain. */
data class RadarOptions(
    val arrows: Boolean = true,
    val lightning: Boolean = true,
)

interface RainRadar {
    /** The current frames, refreshed while collected, or null while no source is set up or reachable. */
    val frames: Flow<RadarFrames?>
}
