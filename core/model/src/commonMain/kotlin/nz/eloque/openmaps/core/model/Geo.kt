package nz.eloque.openmaps.core.model

data class LatLng(
    val latitude: Double,
    val longitude: Double,
)

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
