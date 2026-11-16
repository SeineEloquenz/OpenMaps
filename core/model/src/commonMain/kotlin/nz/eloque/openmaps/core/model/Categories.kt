package nz.eloque.openmaps.core.model

import kotlinx.coroutines.flow.Flow

/** Kinds of places offered as quick searches. */
enum class PlaceCategory {
    RESTAURANTS,
    CAFES,
    SUPERMARKETS,
    GAS_STATIONS,
    CHARGING,
    BARS,
    ATTRACTIONS,
    HOTELS,
    PHARMACIES,
    BAKERIES,
    ATMS,
    TOILETS,
    DRINKING_WATER,
    SHARED_VEHICLES,
}

/** A place found nearby, with its details once they are known. */
data class NearbyPlace(
    val place: SearchResult.Place,
    val details: PlaceDetails?,
)

/** Credit a data source requires wherever its data is shown. */
data class Attribution(
    val source: String,
    val url: String,
)

data class NearbyResults(
    /** Nearest to the search center first. */
    val places: List<NearbyPlace>,
    val attributions: List<Attribution> = emptyList(),
)

/** Places already at hand on the device, such as in loaded map data, which can include ones search services miss. */
fun interface LoadedPlaces {
    /** Emits the places of [category] known so far, again whenever more become known. */
    fun places(category: PlaceCategory): Flow<List<SearchResult.Place>>
}

interface NearbyPlaces {
    /**
     * Places of [category] in [area], nearest to [center] first. Emits the places as soon as they are found,
     * then again as details arrive or [loaded] adds places. Fails only when no places could be found at all.
     */
    fun search(
        category: PlaceCategory,
        center: LatLng,
        area: GeoBounds,
        loaded: LoadedPlaces?,
    ): Flow<NearbyResults>
}
