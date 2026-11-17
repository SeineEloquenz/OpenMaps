package nz.eloque.openmaps.data.routing

import nz.eloque.openmaps.core.model.LatLng
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.roundToInt
import kotlin.math.sin

/** A point a route must pass, heading the way the route goes there, without ending a leg. */
internal data class Through(
    val location: LatLng,
    val heading: Int,
)

/** Share of [line] that runs along [other]. */
internal fun overlap(
    line: List<LatLng>,
    other: List<LatLng>,
): Double {
    val cells = other.densified().mapTo(HashSet()) { it.cell() }
    val points = line.densified()
    return points.count { p -> p.cell().near().any { it in cells } }.toDouble() / points.size.coerceAtLeast(1)
}

/** The point of [route] furthest from [main], or null when [route] never leaves it by much. */
internal fun detour(
    route: List<LatLng>,
    main: List<LatLng>,
): Through? {
    val samples = route.densified(SAMPLE_METERS)
    val others = main.densified()
    if (samples.size < 3 || others.isEmpty()) return null
    val index = (1 until samples.lastIndex).maxBy { i -> others.minOf { it.distanceTo(samples[i]) } }
    if (others.minOf { it.distanceTo(samples[index]) } < MIN_DETOUR_METERS) return null
    return Through(samples[index], bearing(samples[index - 1], samples[index + 1]))
}

private fun bearing(
    a: LatLng,
    b: LatLng,
): Int {
    val dLon = (b.longitude - a.longitude) * PI / 180
    val lat1 = a.latitude * PI / 180
    val lat2 = b.latitude * PI / 180
    val y = sin(dLon) * cos(lat2)
    val x = cos(lat1) * sin(lat2) - sin(lat1) * cos(lat2) * cos(dLon)
    return ((atan2(y, x) * 180 / PI).roundToInt() + 360) % 360
}

private data class Cell(
    val x: Long,
    val y: Long,
)

private fun LatLng.cell() = Cell(floor(longitude / CELL_DEGREES).toLong(), floor(latitude / CELL_DEGREES).toLong())

private fun Cell.near() = (-1L..1L).flatMap { dx -> (-1L..1L).map { dy -> Cell(x + dx, y + dy) } }

/** Points no further apart than [step] meters, so long straight segments count by their length. */
private fun List<LatLng>.densified(step: Double = STEP_METERS): List<LatLng> =
    if (size < 2) {
        this
    } else {
        zipWithNext().flatMap { (a, b) ->
            val steps = (a.distanceTo(b) / step).toInt().coerceAtLeast(1)
            (0 until steps).map { s ->
                val t = s.toDouble() / steps
                LatLng(a.latitude + (b.latitude - a.latitude) * t, a.longitude + (b.longitude - a.longitude) * t)
            }
        } + last()
    }

// About 30 m, so a road counts as shared even when the two lines' points sit on different sides of a cell border.
private const val CELL_DEGREES = 0.0003
private const val STEP_METERS = 30.0
private const val SAMPLE_METERS = 200.0
private const val MIN_DETOUR_METERS = 500.0

/**
 * Small squares along the middle of [line], as Valhalla exclude polygons of longitude and latitude pairs, so a route
 * avoiding them has to leave [line] for most of its length. Kept small since Valhalla limits their total perimeter.
 */
internal fun blocksAlong(line: List<LatLng>): List<List<List<Double>>> {
    val points = line.densified()
    if (points.size < 2) return emptyList()
    val length = points.zipWithNext().sumOf { (a, b) -> a.distanceTo(b) }
    val spacing = maxOf(length * BLOCKED_SHARE / MAX_BLOCKS, MIN_BLOCK_SPACING_METERS)
    var travelled = 0.0
    var next = length * (1 - BLOCKED_SHARE) / 2
    val end = length - next
    val blocks = mutableListOf<List<List<Double>>>()
    for ((a, b) in points.zipWithNext()) {
        travelled += a.distanceTo(b)
        if (travelled < next) continue
        if (travelled > end) break
        blocks += square(b)
        next += spacing
    }
    return blocks
}

private fun square(center: LatLng): List<List<Double>> {
    val dLat = BLOCK_HALF_METERS / METERS_PER_DEGREE
    val dLon = dLat / cos(center.latitude * PI / 180)
    val (lat, lon) = center.latitude to center.longitude
    return listOf(
        listOf(lon - dLon, lat - dLat),
        listOf(lon + dLon, lat - dLat),
        listOf(lon + dLon, lat + dLat),
        listOf(lon - dLon, lat + dLat),
        listOf(lon - dLon, lat - dLat),
    )
}

private const val METERS_PER_DEGREE = 111_320.0
private const val BLOCKED_SHARE = 0.5
private const val MAX_BLOCKS = 12
private const val MIN_BLOCK_SPACING_METERS = 300.0
private const val BLOCK_HALF_METERS = 25.0
