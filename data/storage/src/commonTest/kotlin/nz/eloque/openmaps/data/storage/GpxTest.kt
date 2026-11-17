package nz.eloque.openmaps.data.storage

import nz.eloque.openmaps.core.model.LatLng
import nz.eloque.openmaps.core.model.Track
import nz.eloque.openmaps.core.model.TrackPoint
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Instant

private const val HIKE =
    """<?xml version="1.0" encoding="UTF-8"?>
<gpx version="1.1" creator="Some app" xmlns="http://www.topografix.com/GPX/1/1">
  <metadata><name>Black Forest weekend</name></metadata>
  <wpt lat="48.5" lon="8.2"><name>Hut</name></wpt>
  <trk>
    <name>Day one</name>
    <trkseg>
      <trkpt lat="48.50" lon="8.20"><ele>800</ele><time>2026-10-03T08:00:00Z</time></trkpt>
      <trkpt lat="48.51" lon="8.21"><ele>850.5</ele><time>2026-10-03T08:20:00Z</time></trkpt>
    </trkseg>
    <trkseg>
      <trkpt lat="48.52" lon="8.22"><ele>900</ele><time>2026-10-03T09:00:00Z</time></trkpt>
    </trkseg>
  </trk>
  <trk><trkseg><trkpt lat="48.6" lon="8.3"/></trkseg></trk>
  <rte><rtept lat="48.7" lon="8.4"/><rtept lat="48.71" lon="8.41"/></rte>
</gpx>"""

private val Now = Instant.parse("2026-11-17T12:00:00Z")

class GpxTest {
    @Test
    fun readsTracksAndRoutesWithTheirSegmentsJoined() {
        val tracks = readGpx(HIKE, "Imported", now = { Now })
        assertEquals(listOf("Day one", "Black Forest weekend"), tracks.map { it.name })
        val day = tracks[0]
        assertEquals(3, day.points.size)
        assertEquals(850.5, day.points[1].elevation)
        assertEquals(Instant.parse("2026-10-03T08:00:00Z"), day.createdAt)
        assertEquals(60, day.duration?.inWholeMinutes)
        assertEquals(Now, tracks[1].createdAt)
    }

    @Test
    fun readsGpx10AndFilesWithoutNamespace() {
        val old =
            """<gpx version="1.0" creator="x" xmlns="http://www.topografix.com/GPX/1/0"><trk><name>Old</name><trkseg>""" +
                """<trkpt lat="1" lon="2"><speed>3</speed></trkpt><trkpt lat="1.1" lon="2.1"/></trkseg></trk></gpx>"""
        assertEquals("Old", readGpx(old, "Imported").single().name)
        val bare = """<gpx version="1.1" creator="x"><rte><rtept lat="1" lon="2"/><rtept lat="1.1" lon="2.1"/></rte></gpx>"""
        assertEquals(2, readGpx(bare, "Imported").single().points.size)
    }

    @Test
    fun ignoresWhatIsNoGpx() {
        assertEquals(emptyList(), readGpx("<html>no</html>", "Imported"))
        assertEquals(emptyList(), readGpx("not even xml", "Imported"))
    }

    @Test
    fun writesWhatItReads() {
        val track =
            Track(
                id = 1,
                name = "Walk & talk",
                createdAt = Instant.parse("2026-11-17T10:00:00Z"),
                points =
                    listOf(
                        TrackPoint(LatLng(49.0, 8.4), 115.0, Instant.parse("2026-11-17T10:00:00Z")),
                        TrackPoint(LatLng(49.001, 8.401), null, null),
                    ),
            )
        val gpx = writeGpx(track)
        assertTrue("creator=\"OpenMaps\"" in gpx)
        val back = readGpx(gpx, "Imported").single()
        assertEquals(track.name, back.name)
        assertEquals(track.points, back.points)
    }

    @Test
    fun storedPointsRoundTrip() {
        val points =
            listOf(TrackPoint(LatLng(49.0, 8.4), 115.5, Instant.fromEpochMilliseconds(1_800_000_000_123)), TrackPoint(LatLng(-36.8, 174.7)))
        assertEquals(points, decodePoints(encodePoints(points)))
    }
}
