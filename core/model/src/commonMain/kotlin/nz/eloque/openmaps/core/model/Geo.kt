package nz.eloque.openmaps.core.model

import kotlin.math.PI
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

data class LatLng(
    val latitude: Double,
    val longitude: Double,
) {
    /** Great-circle distance in meters. */
    fun distanceTo(other: LatLng): Double {
        val dLat = (other.latitude - latitude).toRadians()
        val dLon = (other.longitude - longitude).toRadians()
        val a =
            sin(dLat / 2).let { it * it } +
                cos(latitude.toRadians()) * cos(other.latitude.toRadians()) * sin(dLon / 2).let { it * it }
        return 2 * EARTH_RADIUS_METERS * asin(sqrt(a))
    }
}

private const val EARTH_RADIUS_METERS = 6_371_000.0
private const val METERS_PER_DEGREE = 111_320.0

private fun Double.toRadians() = this * PI / 180

/** Identifies a place across searches and restarts. Only the source that issued it knows what [value] means. */
data class PlaceId(
    val value: String,
)

data class GeoBounds(
    val south: Double,
    val west: Double,
    val north: Double,
    val east: Double,
) {
    operator fun contains(point: LatLng) = point.latitude in south..north && point.longitude in west..east

    fun intersects(other: GeoBounds) = south <= other.north && other.south <= north && west <= other.east && other.west <= east

    /** These bounds, grown where needed to reach at least [radiusMeters] from [center] in every direction. */
    fun coveringAtLeast(
        center: LatLng,
        radiusMeters: Double,
    ): GeoBounds {
        val latitude = radiusMeters / METERS_PER_DEGREE
        val longitude = latitude / cos(center.latitude * PI / 180)
        return GeoBounds(
            south = minOf(south, center.latitude - latitude),
            west = minOf(west, center.longitude - longitude),
            north = maxOf(north, center.latitude + latitude),
            east = maxOf(east, center.longitude + longitude),
        )
    }
}
