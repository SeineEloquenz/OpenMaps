package nz.eloque.openmaps.data.geocoding

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import nz.eloque.openmaps.core.model.GeoBounds
import nz.eloque.openmaps.core.model.LatLng
import nz.eloque.openmaps.core.model.LoadedPlaces
import nz.eloque.openmaps.core.model.NearbyPlace
import nz.eloque.openmaps.core.model.NearbyPlaces
import nz.eloque.openmaps.core.model.NearbyResults
import nz.eloque.openmaps.core.model.PlaceCategory
import nz.eloque.openmaps.core.model.PlaceDetails
import nz.eloque.openmaps.core.model.PlaceDetailsSource
import nz.eloque.openmaps.core.model.SearchResult

/**
 * Charging stations from [charging] when it is available. Everything else from [candidates] and the loaded places,
 * keeping the nearest [maxResults] and adding [details] for them.
 */
internal class CompositeNearbyPlaces(
    private val candidates: CategoryCandidates,
    private val details: PlaceDetailsSource,
    private val charging: ChargingStations,
    private val maxResults: Int = MAX_RESULTS,
) : NearbyPlaces {
    override fun search(
        category: PlaceCategory,
        center: LatLng,
        area: GeoBounds,
        loaded: LoadedPlaces?,
    ): Flow<NearbyResults> =
        flow {
            if (category == PlaceCategory.CHARGING && charging.available) {
                val stations = charging.stations(center, area).take(maxResults)
                emit(NearbyResults(stations.map { (place, details) -> NearbyPlace(place, details) }, charging.attribution))
                return@flow
            }
            var places = candidates.search(category, center, area).nearest(center)
            val known = mutableMapOf<SearchResult.Place, PlaceDetails>()

            suspend fun publish() = emit(NearbyResults(places.map { NearbyPlace(it, known[it]) }))

            suspend fun addDetails() {
                val missing = places.filter { it.id != null && it !in known }
                if (missing.isEmpty()) return
                try {
                    val byId = details.detailsOf(missing.mapNotNull { it.id })
                    missing.forEach { place -> byId[place.id]?.let { known[place] = it } }
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    return
                }
                publish()
            }

            publish()
            addDetails()
            loaded?.places(category)?.collect { found ->
                val have = places.mapNotNull { it.id }.toSet()
                val added = found.filter { it.id != null && it.id !in have && area.contains(it.location) }
                if (added.isEmpty()) return@collect
                places = (places + added).nearest(center)
                publish()
                addDetails()
            }
        }

    private fun List<SearchResult.Place>.nearest(center: LatLng) = sortedBy { it.location.distanceTo(center) }.take(maxResults)
}

private const val MAX_RESULTS = 50

private fun GeoBounds.contains(point: LatLng) = point.latitude in south..north && point.longitude in west..east
