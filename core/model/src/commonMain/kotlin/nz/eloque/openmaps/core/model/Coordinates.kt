package nz.eloque.openmaps.core.model

import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.roundToLong

/**
 * Reads coordinates typed or pasted as decimal degrees (`49.0069, 8.4037`), with hemisphere letters (`49.0069N 8.4037E`),
 * as degrees, minutes and seconds (`49°0'24.8"N 8°24'13.3"E`), or as a Plus Code. A short Plus Code (`2CXW+QF`) needs [near].
 */
fun parseCoordinates(
    text: String,
    near: LatLng? = null,
): LatLng? = plusCode(text.trim().uppercase(), near) ?: degrees(text)

/** Decimal degrees with five places, about a meter, such as `49.00691, 8.40370`. */
fun LatLng.formatted(): String = "${decimal(latitude)}, ${decimal(longitude)}"

/** The 10 digit Open Location Code of this point, such as `8FXC2CXW+QF`, about 14 meters across. */
fun LatLng.plusCode(): String {
    var lat = floor((latitude.coerceIn(-90.0, 90.0) + 90) * CELLS_PER_DEGREE).toLong().coerceAtMost(180 * CELLS_PER_DEGREE - 1)
    var lon = floor((((longitude + 180) % 360 + 360) % 360) * CELLS_PER_DEGREE).toLong()
    val digits = CharArray(PAIRS * 2)
    for (pair in PAIRS - 1 downTo 0) {
        digits[pair * 2] = ALPHABET[(lat % BASE).toInt()]
        digits[pair * 2 + 1] = ALPHABET[(lon % BASE).toInt()]
        lat /= BASE
        lon /= BASE
    }
    val code = digits.concatToString()
    return code.take(SEPARATOR_AT) + "+" + code.drop(SEPARATOR_AT)
}

private fun decimal(value: Double): String {
    val scaled = (value * 100_000).roundToLong()
    val whole = abs(scaled) / 100_000
    val fraction = (abs(scaled) % 100_000).toString().padStart(5, '0')
    return "${if (scaled < 0) "-" else ""}$whole.$fraction"
}

private fun plusCode(
    text: String,
    near: LatLng?,
): LatLng? {
    if (!PlusCode.matches(text)) return null
    val separator = text.indexOf('+')
    val digits = text.replace("+", "")
    if (digits.any { it == '0' }) return null
    return if (separator == SEPARATOR_AT) {
        decode(digits)
    } else {
        near?.let { recover(digits, separator, it) }
    }
}

/** Center of the area a full code without its separator covers. */
private fun decode(digits: String): LatLng? {
    if (digits.length < SEPARATOR_AT || ALPHABET.indexOf(digits[0]) >= 9 || ALPHABET.indexOf(digits[1]) >= 18) return null
    var lat = 0.0
    var lon = 0.0
    var resolution = BASE.toDouble()
    val pairs = minOf(digits.length / 2, PAIRS)
    for (pair in 0 until pairs) {
        lat += ALPHABET.indexOf(digits[pair * 2]) * resolution
        lon += ALPHABET.indexOf(digits[pair * 2 + 1]) * resolution
        if (pair < pairs - 1) resolution /= BASE
    }
    return LatLng(lat - 90 + resolution / 2, lon - 180 + resolution / 2)
}

/** The full code nearest [near] that ends in a short code's [digits], whose separator came at [separator]. */
private fun recover(
    digits: String,
    separator: Int,
    near: LatLng,
): LatLng? {
    val missing = SEPARATOR_AT - separator
    if (missing <= 0 || missing % 2 != 0) return null
    val area = BASE.toDouble().let { base -> (0 until (missing / 2) - 1).fold(base) { r, _ -> r / BASE } }
    val prefix = near.plusCode().replace("+", "").take(missing)
    val center = decode(prefix + digits) ?: return null
    var lat = center.latitude
    var lon = center.longitude
    if (lat - near.latitude > area / 2 && lat - area >= -90) lat -= area
    if (near.latitude - lat > area / 2 && lat + area <= 90) lat += area
    if (lon - near.longitude > area / 2) lon -= area
    if (near.longitude - lon > area / 2) lon += area
    return LatLng(lat, lon)
}

private val PlusCode = Regex("""^[23456789CFGHJMPQRVWX]{2,8}\+[23456789CFGHJMPQRVWX]{0,3}$""")

private val Part =
    """([NSEW])?\s*([-+]?\d+(?:\.\d+)?)\s*°?\s*(?:(\d+(?:\.\d+)?)\s*['′]\s*)?""" +
        """(?:(\d+(?:\.\d+)?)\s*(?:["″]|'')\s*)?([NSEW])?"""
private val CoordinatePair = Regex("""^\s*$Part\s*[,;]?\s*$Part\s*$""", RegexOption.IGNORE_CASE)

private fun degrees(text: String): LatLng? {
    val groups = CoordinatePair.matchEntire(text)?.groupValues ?: return null
    // Plain numbers like "12 34" are more likely a search for an address than coordinates.
    if ('.' !in text && '°' !in text && groups.none { it.length == 1 && it[0].uppercaseChar() in "NSEW" }) return null
    val first = component(groups.subList(1, 6)) ?: return null
    val second = component(groups.subList(6, 11)) ?: return null
    val (lat, lon) =
        if (first.second == "E" ||
            first.second == "W" ||
            second.second == "N" ||
            second.second == "S"
        ) {
            second to first
        } else {
            first to second
        }
    if (lat.second == "E" || lat.second == "W" || lon.second == "N" || lon.second == "S") return null
    val latitude = lat.first
    val longitude = lon.first
    if (latitude !in -90.0..90.0 || longitude !in -180.0..180.0) return null
    return LatLng(latitude, longitude)
}

/** Signed degrees and the hemisphere letter, empty when none was given. */
private fun component(groups: List<String>): Pair<Double, String>? {
    val (before, degrees, minutes, seconds, after) = groups
    if (before.isNotEmpty() && after.isNotEmpty()) return null
    val letter = (before + after).uppercase()
    val value =
        abs(degrees.toDoubleOrNull() ?: return null) + (minutes.toDoubleOrNull() ?: 0.0) / 60 + (seconds.toDoubleOrNull() ?: 0.0) / 3600
    val negative = degrees.startsWith("-") || letter == "S" || letter == "W"
    if (letter.isNotEmpty() && degrees.startsWith("-")) return null
    return (if (negative) -value else value) to letter
}

private const val ALPHABET = "23456789CFGHJMPQRVWX"
private const val BASE = 20
private const val PAIRS = 5
private const val SEPARATOR_AT = 8
private const val CELLS_PER_DEGREE = 8000L
