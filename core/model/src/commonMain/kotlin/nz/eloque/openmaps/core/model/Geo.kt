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

private fun Double.toRadians() = this * PI / 180

enum class OsmType { NODE, WAY, RELATION }

data class OsmId(
    val type: OsmType,
    val id: Long,
) {
    companion object {
        /** Parses `node/123`, `way/123` or `relation/123`, returning null for anything else. */
        fun parse(value: String): OsmId? {
            val (type, id) = value.split('/').takeIf { it.size == 2 } ?: return null
            val osmType = OsmType.entries.firstOrNull { it.name.equals(type, ignoreCase = true) } ?: return null
            return id.toLongOrNull()?.let { OsmId(osmType, it) }
        }
    }
}
