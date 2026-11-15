package nz.eloque.openmaps.data.geocoding

import nz.eloque.openmaps.core.model.Attribution
import nz.eloque.openmaps.core.model.GeoBounds
import nz.eloque.openmaps.core.model.LatLng
import nz.eloque.openmaps.core.model.PlaceCategory
import nz.eloque.openmaps.core.model.PlaceDetails
import nz.eloque.openmaps.core.model.SearchResult

/** A search service listing places of a category, often without details. */
internal fun interface CategoryCandidates {
    /** Places of [category] inside [bounds], nearest to [center] first. Unnamed places have an empty name. */
    suspend fun search(
        category: PlaceCategory,
        center: LatLng,
        bounds: GeoBounds,
    ): List<SearchResult.Place>
}

/** A charging station database with more detail than general place data. */
internal interface ChargingStations {
    val attribution: Attribution

    /** False until what the service needs, such as an API key, is set. */
    val available: Boolean

    /** Stations inside [bounds], nearest to [center] first. */
    suspend fun stations(
        center: LatLng,
        bounds: GeoBounds,
    ): List<Pair<SearchResult.Place, PlaceDetails>>
}
