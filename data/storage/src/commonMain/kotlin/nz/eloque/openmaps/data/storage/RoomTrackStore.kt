package nz.eloque.openmaps.data.storage

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import nz.eloque.openmaps.core.model.LatLng
import nz.eloque.openmaps.core.model.Track
import nz.eloque.openmaps.core.model.TrackPoint
import nz.eloque.openmaps.core.model.TrackStore
import nz.eloque.openmaps.core.model.TrackSummary
import org.maplibre.spatialk.gpx.Document
import org.maplibre.spatialk.gpx.Gpx
import org.maplibre.spatialk.gpx.Metadata
import org.maplibre.spatialk.gpx.TrackSegment
import org.maplibre.spatialk.gpx.Waypoint
import kotlin.time.Clock
import kotlin.time.Instant
import org.maplibre.spatialk.gpx.Track as GpxTrack

internal class RoomTrackStore(
    private val dao: TracksDao,
) : TrackStore {
    override val tracks: Flow<List<TrackSummary>> =
        dao.tracks().map { rows ->
            rows.map { TrackSummary(it.id, it.name, Instant.fromEpochMilliseconds(it.createdAt), it.distanceMeters) }
        }

    override suspend fun track(id: Long): Track? =
        dao.track(id)?.let { Track(it.id, it.name, Instant.fromEpochMilliseconds(it.createdAt), decodePoints(it.points)) }

    override suspend fun save(
        name: String,
        points: List<TrackPoint>,
        createdAt: Instant,
    ): Long {
        val distance = Track(0, name, createdAt, points).distanceMeters
        return dao.insert(
            TrackEntity(name = name, createdAt = createdAt.toEpochMilliseconds(), distanceMeters = distance, points = encodePoints(points)),
        )
    }

    override suspend fun rename(
        id: Long,
        name: String,
    ) = dao.rename(id, name)

    override suspend fun delete(id: Long) = dao.delete(id)

    override suspend fun importGpx(
        text: String,
        fallbackName: String,
    ): List<Long> = readGpx(text, fallbackName).map { save(it.name, it.points, it.createdAt) }

    override suspend fun exportGpx(id: Long): String? = track(id)?.let(::writeGpx)
}

/** The tracks and routes of a GPX document with at least two points, as unsaved tracks. */
internal fun readGpx(
    text: String,
    fallbackName: String,
    now: () -> Instant = { Clock.System.now() },
): List<Track> {
    val document = decodeGpx(text) ?: return emptyList()
    val documentName = document.metadata?.name?.takeIf { it.isNotBlank() }
    val lines =
        document.tracks.map { it.name to it.segments.flatMap(TrackSegment::points) } +
            document.routes.map { it.name to it.points }
    return lines
        .filter { (_, points) -> points.size >= 2 }
        .map { (name, points) ->
            val trackPoints = points.map { TrackPoint(LatLng(it.latitude, it.longitude), it.elevation, it.timestamp) }
            val start = trackPoints.firstNotNullOfOrNull { it.time } ?: document.metadata?.timestamp ?: now()
            Track(0, name?.takeIf { it.isNotBlank() } ?: documentName ?: fallbackName, start, trackPoints)
        }
}

internal fun writeGpx(track: Track): String {
    val points = track.points.map { Waypoint(it.location.latitude, it.location.longitude, elevation = it.elevation, timestamp = it.time) }
    val document =
        Document(
            creator = CREATOR,
            metadata = Metadata(name = track.name, timestamp = track.createdAt),
            tracks = listOf(GpxTrack(name = track.name, segments = listOf(TrackSegment(points)))),
        )
    return Gpx.encodeToString(document)
}

private const val CREATOR = "OpenMaps"

internal fun encodePoints(points: List<TrackPoint>): String =
    points.joinToString(";") { point ->
        listOf(
            point.location.latitude,
            point.location.longitude,
            point.elevation ?: "",
            point.time?.toEpochMilliseconds() ?: "",
        ).joinToString(",")
    }

internal fun decodePoints(text: String): List<TrackPoint> =
    text.split(';').filter { it.isNotEmpty() }.mapNotNull { entry ->
        val parts = entry.split(',')
        val latitude = parts.getOrNull(0)?.toDoubleOrNull() ?: return@mapNotNull null
        val longitude = parts.getOrNull(1)?.toDoubleOrNull() ?: return@mapNotNull null
        TrackPoint(
            LatLng(latitude, longitude),
            elevation = parts.getOrNull(2)?.toDoubleOrNull(),
            time = parts.getOrNull(3)?.toLongOrNull()?.let(Instant::fromEpochMilliseconds),
        )
    }

/**
 * Null for anything that is no GPX. GPX 1.0 differs from 1.1 only in parts left out here, so it is read as 1.1, as are files
 * that leave out the namespace.
 */
private fun decodeGpx(text: String) =
    try {
        val namespaced = text.replace(GPX_10_NAMESPACE, GPX_11_NAMESPACE)
        Gpx.decodeFromString(
            if (GPX_11_NAMESPACE in
                namespaced
            ) {
                namespaced
            } else {
                namespaced.replaceFirst("<gpx", "<gpx xmlns=\"$GPX_11_NAMESPACE\"")
            },
        )
    } catch (e: CancellationException) {
        throw e
    } catch (_: Exception) {
        null
    }

private const val GPX_10_NAMESPACE = "http://www.topografix.com/GPX/1/0"
private const val GPX_11_NAMESPACE = "http://www.topografix.com/GPX/1/1"
