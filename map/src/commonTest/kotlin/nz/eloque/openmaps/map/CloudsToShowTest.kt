package nz.eloque.openmaps.map

import nz.eloque.openmaps.core.model.RadarFrame
import nz.eloque.openmaps.core.model.RadarFrames
import nz.eloque.openmaps.core.model.SatelliteImage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

private val Hour = Instant.parse("2026-11-17T20:00:00Z")

private fun cloud(at: Instant) = SatelliteImage(at, "clouds/${at.epochSeconds}", 5, 512)

private val clouds = (-3..0).map { cloud(Hour + it.hours) }

private fun radar(vararg minutes: Int) =
    RadarFrames(
        frames = minutes.map { RadarFrame(Hour + it.minutes, it > 40, "r", "r") },
        maxZoom = 7,
        tileSize = 512,
        attribution = "",
    )

class CloudsToShowTest {
    @Test
    fun onTheirOwnEveryPictureLoadsAndTheChosenOneShows() {
        assertEquals(clouds to 2, cloudsToShow(clouds, radar = null, radarFrame = 0, cloudFrame = 2))
        assertEquals(clouds to 3, cloudsToShow(clouds, radar = null, radarFrame = 0, cloudFrame = 7))
    }

    @Test
    fun alongsideTheRadarThePictureTakenBeforeTheFrameShows() {
        // Radar from 19:20 to 20:50, so only the 19:00 and 20:00 pictures matter.
        val radar = radar(-40, -20, 0, 20, 50)
        val relevant = clouds.takeLast(2)
        assertEquals(relevant to 0, cloudsToShow(clouds, radar, radarFrame = 1, cloudFrame = 0))
        assertEquals(relevant to 1, cloudsToShow(clouds, radar, radarFrame = 2, cloudFrame = 0))
        assertEquals(relevant to 1, cloudsToShow(clouds, radar, radarFrame = 4, cloudFrame = 0))
    }

    @Test
    fun noPicturesShowNothing() {
        assertEquals(emptyList<SatelliteImage>() to 0, cloudsToShow(emptyList(), radar(0), 0, 0))
    }
}
