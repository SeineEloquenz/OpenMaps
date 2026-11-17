package nz.eloque.openmaps.ui.guidance

import nz.eloque.openmaps.core.model.GeoBounds
import nz.eloque.openmaps.core.model.LatLng
import kotlin.math.PI
import kotlin.math.cos

/** A place near the route, [ahead] meters along it and [offRoute] meters beside it. */
internal data class AlongRoute<T>(
    val item: T,
    val ahead: Double,
    val offRoute: Double,
)

/** The [items] within [corridor] meters of [route], in the order the route passes them. */
internal fun <T> alongRoute(
    route: List<LatLng>,
    items: List<T>,
    corridor: Double,
    location: (T) -> LatLng,
): List<AlongRoute<T>> {
    if (route.size < 2) return emptyList()
    val starts = DoubleArray(route.size)
    for (i in 1 until route.size) starts[i] = starts[i - 1] + route[i - 1].distanceTo(route[i])
    return items
        .mapNotNull { item ->
            val point = location(item)
            (0 until route.size - 1)
                .map { i ->
                    val (offset, fraction) = project(point, route[i], route[i + 1])
                    offset to starts[i] + fraction * (starts[i + 1] - starts[i])
                }.minBy { it.first }
                .takeIf { it.first <= corridor }
                ?.let { (offset, ahead) -> AlongRoute(item, ahead, offset) }
        }.sortedBy { it.ahead }
}

/** The first [meters] of [route]. */
internal fun firstMeters(
    route: List<LatLng>,
    meters: Double,
): List<LatLng> = split(route, meters).first

/** Bounds around [points], widened by [margin] meters on every side. */
internal fun boundsAround(
    points: List<LatLng>,
    margin: Double,
): GeoBounds {
    val lat = margin / METERS_PER_DEGREE
    val lon = margin / (METERS_PER_DEGREE * cos(points.first().latitude * PI / 180))
    return GeoBounds(
        south = points.minOf { it.latitude } - lat,
        west = points.minOf { it.longitude } - lon,
        north = points.maxOf { it.latitude } + lat,
        east = points.maxOf { it.longitude } + lon,
    )
}

/** Distance from [point] to the segment from [a] to [b], and how far along the segment the closest point lies. */
private fun project(
    point: LatLng,
    a: LatLng,
    b: LatLng,
): Pair<Double, Double> {
    val scale = cos(a.latitude * PI / 180)
    val (bx, by) = (b.longitude - a.longitude) * scale to b.latitude - a.latitude
    val (px, py) = (point.longitude - a.longitude) * scale to point.latitude - a.latitude
    val length = bx * bx + by * by
    val fraction = if (length == 0.0) 0.0 else ((px * bx + py * by) / length).coerceIn(0.0, 1.0)
    val closest = LatLng(a.latitude + fraction * (b.latitude - a.latitude), a.longitude + fraction * (b.longitude - a.longitude))
    return point.distanceTo(closest) to fraction
}

private const val METERS_PER_DEGREE = 111_320.0

/** [route] cut into [count] stretches of about equal length, each starting where the one before ends. */
internal fun sections(
    route: List<LatLng>,
    count: Int,
): List<List<LatLng>> {
    val length = route.zipWithNext { a, b -> a.distanceTo(b) }.sum()
    var rest = route
    return (1..count).mapNotNull {
        val (section, after) = split(rest, length / count)
        rest = after
        section.takeIf { it.size >= 2 }
    }
}
