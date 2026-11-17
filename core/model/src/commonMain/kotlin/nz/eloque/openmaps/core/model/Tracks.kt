package nz.eloque.openmaps.core.model

import kotlinx.coroutines.flow.Flow
import kotlin.time.Duration
import kotlin.time.Instant

data class TrackPoint(
    val location: LatLng,
    /** Meters above sea level. */
    val elevation: Double? = null,
    val time: Instant? = null,
)

/** A line the user recorded or imported, such as a hike. */
data class Track(
    val id: Long,
    val name: String,
    val createdAt: Instant,
    val points: List<TrackPoint>,
) {
    val distanceMeters: Double
        get() = points.zipWithNext().sumOf { (a, b) -> a.location.distanceTo(b.location) }

    /** Null when the points carry no times. */
    val duration: Duration?
        get() {
            val start = points.firstNotNullOfOrNull { it.time } ?: return null
            val end = points.lastOrNull { it.time != null }?.time ?: return null
            return end - start
        }
}

/** A track without its points, for lists. */
data class TrackSummary(
    val id: Long,
    val name: String,
    val createdAt: Instant,
    val distanceMeters: Double,
)

interface TrackStore {
    /** Newest first. */
    val tracks: Flow<List<TrackSummary>>

    suspend fun track(id: Long): Track?

    suspend fun save(
        name: String,
        points: List<TrackPoint>,
        createdAt: Instant,
    ): Long

    suspend fun rename(
        id: Long,
        name: String,
    )

    suspend fun delete(id: Long)

    /** Saves each track and route in a GPX document, named [fallbackName] where it has no name. Empty when it holds none or is no GPX. */
    suspend fun importGpx(
        text: String,
        fallbackName: String,
    ): List<Long>

    /** The track as a GPX document, null when it does not exist. */
    suspend fun exportGpx(id: Long): String?
}
