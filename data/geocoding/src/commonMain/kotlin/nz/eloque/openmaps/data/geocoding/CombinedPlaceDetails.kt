package nz.eloque.openmaps.data.geocoding

import nz.eloque.openmaps.core.model.PlaceDetails
import nz.eloque.openmaps.core.model.PlaceDetailsSource
import nz.eloque.openmaps.core.model.PlaceId

/** Asks each of [sources] in turn, so every place id reaches the source that issued it. */
internal class CombinedPlaceDetails(
    private val sources: List<PlaceDetailsSource>,
) : PlaceDetailsSource {
    override suspend fun details(id: PlaceId): PlaceDetails? = sources.firstNotNullOfOrNull { it.details(id) }

    override suspend fun detailsOf(ids: List<PlaceId>): Map<PlaceId, PlaceDetails> {
        val found = mutableMapOf<PlaceId, PlaceDetails>()
        for (source in sources) {
            val missing = ids.filter { it !in found }
            if (missing.isEmpty()) break
            found += source.detailsOf(missing)
        }
        return found
    }

    override fun pageUrl(id: PlaceId): String? = sources.firstNotNullOfOrNull { it.pageUrl(id) }
}
