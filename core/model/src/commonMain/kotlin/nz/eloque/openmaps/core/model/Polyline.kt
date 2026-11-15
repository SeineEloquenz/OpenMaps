package nz.eloque.openmaps.core.model

import kotlin.math.pow

/** Decodes Google's encoded polyline format with the given decimal [precision]. */
fun decodePolyline(
    encoded: String,
    precision: Int,
): List<LatLng> {
    val factor = 10.0.pow(precision)
    val points = ArrayList<LatLng>()
    var index = 0
    var lat = 0L
    var lon = 0L

    fun nextValue(): Long {
        var result = 0L
        var shift = 0
        while (true) {
            val b = encoded[index++].code - 63
            result = result or ((b and 0x1f).toLong() shl shift)
            shift += 5
            if (b < 0x20) break
        }
        return if (result and 1L != 0L) (result shr 1).inv() else result shr 1
    }

    while (index < encoded.length) {
        lat += nextValue()
        lon += nextValue()
        points += LatLng(lat / factor, lon / factor)
    }
    return points
}
