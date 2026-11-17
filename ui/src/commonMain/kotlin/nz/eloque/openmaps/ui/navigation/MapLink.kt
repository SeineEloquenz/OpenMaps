package nz.eloque.openmaps.ui.navigation

import nz.eloque.openmaps.core.model.LatLng
import nz.eloque.openmaps.core.model.TravelMode

/** What a link opened from another app asks for. */
sealed interface MapLink {
    data class Show(
        val location: LatLng,
        val label: String?,
    ) : MapLink

    data class Find(
        val query: String,
    ) : MapLink

    /** Directions from the user's location to [to], or to the first search result for [query]. */
    data class Route(
        val to: LatLng?,
        val query: String?,
        val mode: TravelMode?,
    ) : MapLink
}

/** Reads `geo:` and `google.navigation:` URIs and map links of Google, Apple, OpenStreetMap and OsmAnd. */
fun parseMapLink(link: String): MapLink? {
    val text = link.trim()
    val scheme = text.substringBefore(':', "").lowercase()
    return when (scheme) {
        "geo" -> geo(text.substringAfter(':'))
        "google.navigation" -> navigation(params(text.substringAfter(':')))
        "http", "https" -> web(text)
        else -> null
    }
}

private fun geo(rest: String): MapLink? {
    val path = rest.substringBefore('?')
    val params = params(rest.substringAfter('?', ""))
    params["q"]?.takeIf { it.isNotBlank() }?.let { return place(it) }
    return coordinates(path)?.takeUnless { it.latitude == 0.0 && it.longitude == 0.0 }?.let { MapLink.Show(it, null) }
}

private fun navigation(params: Map<String, String>): MapLink? {
    val target = params["q"] ?: params["ll"] ?: return null
    val mode =
        when (params["mode"]) {
            "w" -> TravelMode.WALK
            "b" -> TravelMode.BIKE
            "l", "d" -> TravelMode.CAR
            else -> null
        }
    return route(target, mode)
}

private fun web(link: String): MapLink? {
    val afterScheme = link.substringAfter("://")
    val host = afterScheme.substringBefore('/').substringBefore('?').substringBefore('#').lowercase().removePrefix("www.")
    val path = afterScheme.removePrefix(afterScheme.substringBefore('/')).substringBefore('?').substringBefore('#')
    val params = params(link.substringAfter('?', "").substringBefore('#'))
    val fragment = link.substringAfter('#', "")
    return when {
        host == "openstreetmap.org" || host == "osm.org" -> openStreetMap(path, params, fragment)
        host == "maps.apple.com" -> apple(params)
        host == "osmand.net" -> osmAnd(params, fragment)
        host.startsWith("maps.google.") || (host.startsWith("google.") && path.startsWith("/maps")) -> google(path, params)
        else -> null
    }
}

private fun openStreetMap(
    path: String,
    params: Map<String, String>,
    fragment: String,
): MapLink? {
    val marker = coordinates("${params["mlat"]},${params["mlon"]}")
    if (marker != null) return MapLink.Show(marker, null)
    if (path.startsWith("/directions")) {
        params["route"]?.substringAfter(';', "")?.let(::coordinates)?.let { return MapLink.Route(it, null, null) }
    }
    if (path.startsWith("/search")) params["query"]?.let { return place(it) }
    return mapFragment(fragment)?.let { MapLink.Show(it, null) }
}

private fun apple(params: Map<String, String>): MapLink? {
    params["daddr"]?.let { target ->
        val mode =
            when (params["dirflg"]) {
                "w" -> TravelMode.WALK
                "r" -> TravelMode.TRANSIT
                "d" -> TravelMode.CAR
                else -> null
            }
        return route(target, mode)
    }
    val location = (params["ll"] ?: params["sll"])?.let(::coordinates)
    val label = params["q"]?.takeIf { it.isNotBlank() }
    return when {
        location != null -> MapLink.Show(location, label)
        label != null -> place(label)
        params["address"] != null -> MapLink.Find(params.getValue("address"))
        else -> null
    }
}

private fun osmAnd(
    params: Map<String, String>,
    fragment: String,
): MapLink? {
    coordinates("${params["lat"]},${params["lon"]}")?.let { return MapLink.Show(it, params["name"]) }
    return mapFragment(fragment)?.let { MapLink.Show(it, null) }
}

private fun google(
    path: String,
    params: Map<String, String>,
): MapLink? {
    if (path.startsWith("/maps/dir")) {
        params["destination"]?.let { return route(it, googleMode(params["travelmode"])) }
        val parts = path.removePrefix("/maps/dir").split('/').map(::decode).filter { it.isNotBlank() && !it.startsWith("@") }
        parts.lastOrNull()?.let { return route(it, null) }
    }
    params["daddr"]?.let { return route(it, googleMode(params["dirflg"])) }
    (params["q"] ?: params["query"])?.takeIf { it.isNotBlank() }?.let { return place(it) }
    params["ll"]?.let(::coordinates)?.let { return MapLink.Show(it, null) }
    val segments = path.split('/').map(::decode)
    val at = segments.firstOrNull { it.startsWith("@") }?.let { coordinates(it.removePrefix("@")) }
    val name = segments.getOrNull(segments.indexOf("place") + 1)?.takeIf { segments.contains("place") && !it.startsWith("@") }
    if (segments.contains("search")) {
        segments.getOrNull(segments.indexOf("search") + 1)?.takeIf { it.isNotBlank() && !it.startsWith("@") }?.let { return place(it) }
    }
    return when {
        at != null -> MapLink.Show(at, name)
        name != null -> MapLink.Find(name)
        else -> null
    }
}

private fun googleMode(value: String?) =
    when (value) {
        "walking", "w" -> TravelMode.WALK
        "bicycling", "b" -> TravelMode.BIKE
        "transit", "r" -> TravelMode.TRANSIT
        "driving", "d" -> TravelMode.CAR
        else -> null
    }

private fun place(text: String): MapLink {
    val location = coordinates(text) ?: return MapLink.Find(text.trim())
    val label = text.substringAfter('(', "").substringBeforeLast(')').trim().takeIf { it.isNotEmpty() }
    return MapLink.Show(location, label)
}

private fun route(
    target: String,
    mode: TravelMode?,
): MapLink.Route {
    val location = coordinates(target)
    return MapLink.Route(location, if (location == null) target.trim() else null, mode)
}

/** `#map=zoom/lat/lon` as used by openstreetmap.org and OsmAnd. */
private fun mapFragment(fragment: String): LatLng? {
    val parts = fragment.removePrefix("map=").split('/', '&')
    if (parts.size < 3) return null
    return coordinates("${parts[1]},${parts[2]}")
}

private val Coordinates = Regex("""^\s*([-+]?\d{1,2}(?:\.\d+)?)\s*,\s*([-+]?\d{1,3}(?:\.\d+)?)(?:[,;].*)?\s*(?:\(.*\))?\s*$""")

private fun coordinates(text: String): LatLng? {
    val match = Coordinates.matchEntire(text) ?: return null
    val lat = match.groupValues[1].toDoubleOrNull() ?: return null
    val lon = match.groupValues[2].toDoubleOrNull() ?: return null
    if (lat !in -90.0..90.0 || lon !in -180.0..180.0) return null
    return LatLng(lat, lon)
}

private fun params(query: String): Map<String, String> =
    query
        .split('&')
        .filter { '=' in it }
        .associate { decode(it.substringBefore('=')) to decode(it.substringAfter('=')) }

private fun decode(text: String): String {
    val bytes = ArrayList<Byte>(text.length)
    var i = 0
    while (i < text.length) {
        val c = text[i]
        when {
            c == '+' -> bytes += ' '.code.toByte()
            c == '%' && i + 2 < text.length && text.substring(i + 1, i + 3).toIntOrNull(16) != null -> {
                bytes += text.substring(i + 1, i + 3).toInt(16).toByte()
                i += 2
            }
            else -> bytes += c.toString().encodeToByteArray().toList()
        }
        i++
    }
    return bytes.toByteArray().decodeToString()
}
