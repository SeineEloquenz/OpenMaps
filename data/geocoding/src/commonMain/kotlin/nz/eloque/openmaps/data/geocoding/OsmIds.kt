package nz.eloque.openmaps.data.geocoding

import nz.eloque.openmaps.core.model.PlaceId

internal enum class OsmType { NODE, WAY, RELATION }

/** An OpenStreetMap object, issued as the place id `osm:<type>/<number>`. */
internal data class OsmRef(
    val type: OsmType,
    val number: Long,
) {
    val placeId: PlaceId get() = PlaceId("$PREFIX${type.name.lowercase()}/$number")

    /** The id Nominatim's lookup takes, such as `W123`. */
    val lookupId: String get() = "${type.name.first()}$number"

    val pageUrl: String get() = "https://www.openstreetmap.org/${type.name.lowercase()}/$number"
}

internal fun PlaceId.toOsm(): OsmRef? {
    val (type, number) = value.removePrefix(PREFIX).takeIf { value.startsWith(PREFIX) }?.split('/')?.takeIf { it.size == 2 } ?: return null
    val osmType = OsmType.entries.firstOrNull { it.name.equals(type, ignoreCase = true) } ?: return null
    return number.toLongOrNull()?.let { OsmRef(osmType, it) }
}

private const val PREFIX = "osm:"
