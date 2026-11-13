package nz.eloque.openmaps.ui.navigation

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable
import nz.eloque.openmaps.core.model.LatLng
import nz.eloque.openmaps.core.model.OsmId
import nz.eloque.openmaps.core.model.SearchResult

@Serializable
data object HomeKey : NavKey

@Serializable
data object SearchKey : NavKey

@Serializable
data class PlaceKey(
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val subtitle: String?,
    val category: String?,
    val osmId: String?,
) : NavKey {
    val location: LatLng get() = LatLng(latitude, longitude)
}

@Serializable
data class StopKey(
    val id: String,
    val name: String,
    val latitude: Double,
    val longitude: Double,
) : NavKey {
    val location: LatLng get() = LatLng(latitude, longitude)
}

fun SearchResult.toKey(): NavKey =
    when (this) {
        is SearchResult.Stop -> StopKey(id, name, location.latitude, location.longitude)
        is SearchResult.Place ->
            PlaceKey(name, location.latitude, location.longitude, area, category, osmId?.let { "${it.type.name.lowercase()}/${it.id}" })
        is SearchResult.Address -> {
            val subtitle = listOfNotNull(postcode, area).joinToString(" ").takeIf { it.isNotBlank() }
            PlaceKey(name, location.latitude, location.longitude, subtitle, category = null, osmId = null)
        }
    }

val PlaceKey.parsedOsmId: OsmId? get() = osmId?.let(OsmId::parse)
