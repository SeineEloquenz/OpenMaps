package nz.eloque.openmaps.core.model

import kotlinx.coroutines.flow.Flow

enum class FavouriteLabel { HOME, WORK, OTHER }

data class Favourite(
    val id: Long,
    val label: FavouriteLabel,
    val place: SearchResult,
    /** A name chosen by the user, shown in place of the place name. */
    val customName: String? = null,
) {
    val displayName: String
        get() = customName ?: place.name
}

/** One end of a saved route. Stops route by id, everything else by coordinates. */
data class RouteEnd(
    val name: String,
    val location: LatLng,
    val stopId: String?,
)

/** A start and destination to plan again later. A null end means the user's location. */
data class SavedRoute(
    val id: Long,
    val from: RouteEnd?,
    val to: RouteEnd?,
)

/** Identifies the same place across searches, so it can be matched against saved places. */
val SearchResult.storageKey: String
    get() =
        when (this) {
            is SearchResult.Stop -> "stop:$id"
            is SearchResult.Place -> osmId?.let { "osm:${it.type.name.lowercase()}/${it.id}" } ?: geoKey()
            is SearchResult.Address -> geoKey()
        }

private fun SearchResult.geoKey(): String {
    fun Double.rounded() = (this * 100_000).toLong()
    return "geo:${location.latitude.rounded()},${location.longitude.rounded()}:$name"
}

interface PlacesStore {
    /** Home first, then work, then the rest in the order they were saved. */
    val favourites: Flow<List<Favourite>>

    /** Most recent first. */
    val recents: Flow<List<SearchResult>>

    /** Saving as [FavouriteLabel.HOME] or [FavouriteLabel.WORK] replaces the previous one. */
    suspend fun saveFavourite(
        place: SearchResult,
        label: FavouriteLabel,
    )

    suspend fun removeFavourite(id: Long)

    suspend fun addRecent(place: SearchResult)

    /** A null or blank name restores the place name. */
    suspend fun renameFavourite(
        id: Long,
        name: String?,
    )

    /** Newest first. */
    val savedRoutes: Flow<List<SavedRoute>>

    suspend fun saveRoute(
        from: RouteEnd?,
        to: RouteEnd?,
    )

    suspend fun removeRoute(id: Long)
}
