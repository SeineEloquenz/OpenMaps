package nz.eloque.openmaps.ui.navigation

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable
import nz.eloque.openmaps.core.model.LatLng
import nz.eloque.openmaps.core.model.OsmId
import nz.eloque.openmaps.core.model.SearchResult

@Serializable
data object HomeKey : NavKey

enum class DirectionsField { FROM, TO }

/** Search for the main map, or for one end of the directions when [field] is set. */
@Serializable
data class SearchKey(
    val field: DirectionsField? = null,
) : NavKey

/** A route end. Stops route by id, everything else by coordinates. */
@Serializable
data class Endpoint(
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val stopId: String? = null,
) {
    val location: LatLng get() = LatLng(latitude, longitude)
}

/** A null end means the user's location. */
@Serializable
data class DirectionsKey(
    val from: Endpoint?,
    val to: Endpoint?,
) : NavKey

@Serializable
data class RouteKey(
    val itineraryId: String,
    val destinationName: String?,
) : NavKey

fun SearchResult.toEndpoint(): Endpoint = Endpoint(name, location.latitude, location.longitude, (this as? SearchResult.Stop)?.id)

fun PlaceKey.toEndpoint(): Endpoint = Endpoint(name, latitude, longitude)

fun StopKey.toEndpoint(): Endpoint = Endpoint(name, latitude, longitude, id)

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
