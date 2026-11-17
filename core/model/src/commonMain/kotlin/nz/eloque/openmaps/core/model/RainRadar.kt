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
    /** Edge length of a tile image in pixels. */
    val tileSize: Int,
    val attribution: String,
    /** Satellite pictures of the clouds, oldest first. */
    val clouds: List<SatelliteImage> = emptyList(),
    /** Colour schemes the source can draw the radar in. */
    val colourSchemes: List<RadarColourScheme> = emptyList(),
) {
    /** The newest measured frame, or the first one if all are forecasts. */
    val latestObserved: Int get() = frames.indexOfLast { !it.forecast }.coerceAtLeast(0)
}

data class SatelliteImage(
    val time: Instant,
    /** With `{z}`, `{x}` and `{y}` placeholders. */
    val urlTemplate: String,
    val maxZoom: Int,
    val tileSize: Int,
)

data class RadarColourScheme(
    val id: Int,
    val name: String,
)

/** How the radar layer draws the rain and what it adds to it. */
data class RadarOptions(
    val arrows: Boolean = true,
    val lightning: Boolean = true,
    val stormCells: Boolean = false,
    /** A [RadarColourScheme.id]. */
    val colourScheme: Int = DEFAULT_COLOUR_SCHEME,
) {
    companion object {
        /** Universal Blue in the Rain Viewer numbering. */
        const val DEFAULT_COLOUR_SCHEME = 2
    }
}

interface RainRadar {
    /** The current frames, refreshed while collected, or null while no source is set up or reachable. */
    val frames: Flow<RadarFrames?>
}
