package nz.eloque.openmaps.ui.navigation

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable
import nz.eloque.openmaps.core.model.FavouriteLabel
import nz.eloque.openmaps.core.model.LatLng
import nz.eloque.openmaps.core.model.PlaceCategory
import nz.eloque.openmaps.core.model.PlaceId
import nz.eloque.openmaps.core.model.RouteEnd
import nz.eloque.openmaps.core.model.SearchResult
import nz.eloque.openmaps.core.model.TransportMode

@Serializable
data object HomeKey : NavKey

@Serializable
data object AboutKey : NavKey

@Serializable
data object SettingsKey : NavKey

/** Places of one category in the visible map area. */
@Serializable
data class CategoryKey(
    val category: PlaceCategory,
) : NavKey

enum class DirectionsField { FROM, TO }

/** Search for the main map, for one end of the directions when [field] is set, or to save a favourite when [saveAs] is set. */
@Serializable
data class SearchKey(
    val field: DirectionsField? = null,
    val saveAs: FavouriteLabel? = null,
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
    val placeId: String?,
    /** True for a pin dropped by long-pressing the map. */
    val pinned: Boolean = false,
) : NavKey {
    val location: LatLng get() = LatLng(latitude, longitude)
}

@Serializable
data class StopKey(
    val id: String,
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val modes: Set<TransportMode> = emptySet(),
) : NavKey {
    val location: LatLng get() = LatLng(latitude, longitude)
}

fun StopKey.toSearchResult(): SearchResult = SearchResult.Stop(id, name, location, null, modes)

fun PlaceKey.toSearchResult(): SearchResult =
    if (placeId == null && category == null) {
        SearchResult.Address(name, location, subtitle, null, null, null)
    } else {
        SearchResult.Place(parsedId, name, location, subtitle, category)
    }

fun SearchResult.toKey(): NavKey =
    when (this) {
        is SearchResult.Stop -> StopKey(id, name, location.latitude, location.longitude, modes)
        is SearchResult.Place ->
            PlaceKey(name, location.latitude, location.longitude, area, category, id?.value)
        is SearchResult.Address -> {
            val subtitle = listOfNotNull(postcode, area).joinToString(" ").takeIf { it.isNotBlank() }
            PlaceKey(name, location.latitude, location.longitude, subtitle, category = null, placeId = null)
        }
    }

val PlaceKey.parsedId: PlaceId? get() = placeId?.let(::PlaceId)

/** One vehicle's run. [stopName] is the stop it was opened from, which is highlighted. */
@Serializable
data class TripKey(
    val tripId: String,
    val stopName: String?,
) : NavKey

@Serializable
data object SavedKey : NavKey

fun Endpoint.toRouteEnd(): RouteEnd = RouteEnd(name, location, stopId)

fun RouteEnd.toEndpoint(): Endpoint = Endpoint(name, location.latitude, location.longitude, stopId)
