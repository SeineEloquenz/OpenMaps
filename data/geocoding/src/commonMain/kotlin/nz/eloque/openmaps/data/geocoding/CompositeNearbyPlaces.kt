package nz.eloque.openmaps.data.geocoding

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import nz.eloque.openmaps.core.model.FuelPrices
import nz.eloque.openmaps.core.model.GeoBounds
import nz.eloque.openmaps.core.model.LatLng
import nz.eloque.openmaps.core.model.LoadedPlaces
import nz.eloque.openmaps.core.model.NearbyPlace
import nz.eloque.openmaps.core.model.NearbyPlaces
import nz.eloque.openmaps.core.model.NearbyResults
import nz.eloque.openmaps.core.model.PlaceCategory
import nz.eloque.openmaps.core.model.PlaceDetails
import nz.eloque.openmaps.core.model.PlaceDetailsSource
import nz.eloque.openmaps.core.model.PricedStation
import nz.eloque.openmaps.core.model.SearchResult

/**
 * Charging stations from [charging] when it is available. Everything else from [candidates] and the loaded places,
 * keeping the nearest [maxResults] and adding [details] for them. Gas stations also get prices from [fuel], and
 * priced stations missing from place data are added.
 */
internal class CompositeNearbyPlaces(
    private val candidates: CategoryCandidates,
    private val details: PlaceDetailsSource,
    private val charging: ChargingStations,
    private val fuel: FuelPrices? = null,
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
                emit(NearbyResults(stations.map { (place, details) -> NearbyPlace(place, details) }, listOf(charging.attribution)))
                return@flow
            }
            coroutineScope {
                val priced = fuel?.takeIf { category == PlaceCategory.GAS_STATIONS }?.let { async { it.stationsOrEmpty(center, area) } }
                var places = candidates.search(category, center, area).nearest(center)
                val known = mutableMapOf<SearchResult.Place, PlaceDetails>()
                var stations = emptyList<PricedStation>()

                suspend fun publish() {
                    val matched = matchStations(places, stations)
                    val unmatched = stations.filter { it !in matched.values && it.location in area }.map { it.toPlace() to it }
                    val all = (places.map { it to matched[it] } + unmatched).sortedBy { (place, _) -> place.location.distanceTo(center) }
                    emit(
                        NearbyResults(
                            all.take(maxResults).map { (place, station) -> NearbyPlace(place, known[place].withPrices(station)) },
                            stations.map { it.source }.distinct(),
                        ),
                    )
                }

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
                priced?.await()?.takeIf { it.isNotEmpty() }?.let {
                    stations = it
                    publish()
                }
                loaded?.places(category)?.collect { found ->
                    val have = places.mapNotNull { it.id }.toSet()
                    val added = found.filter { it.id != null && it.id !in have && it.location in area }
                    if (added.isEmpty()) return@collect
                    places = (places + added).nearest(center)
                    publish()
                    addDetails()
                }
            }
        }

    private fun List<SearchResult.Place>.nearest(center: LatLng) = sortedBy { it.location.distanceTo(center) }.take(maxResults)
}

/** Pairs each priced station with the nearest unpaired place within [MATCH_METERS], closest pairs first. */
internal fun matchStations(
    places: List<SearchResult.Place>,
    stations: List<PricedStation>,
): Map<SearchResult.Place, PricedStation> {
    val pairs =
        places
            .flatMap { place ->
                stations.mapNotNull { station ->
                    place.location.distanceTo(station.location).takeIf { it <= MATCH_METERS }?.let { Triple(place, station, it) }
                }
            }.sortedBy { it.third }
    val matched = mutableMapOf<SearchResult.Place, PricedStation>()
    val used = mutableSetOf<PricedStation>()
    pairs.forEach { (place, station) ->
        if (place !in matched && station !in used) {
            matched[place] = station
            used += station
        }
    }
    return matched
}

private fun PricedStation.toPlace() =
    SearchResult.Place(null, name ?: brand ?: address?.substringBefore(",").orEmpty(), location, address?.substringAfter(", ", ""), "fuel")

private fun PlaceDetails?.withPrices(station: PricedStation?): PlaceDetails? {
    if (station == null) return this
    val base = this ?: PlaceDetails(address = station.address, openingHours = null, website = null, phone = null, wheelchair = null)
    return base.copy(fuelPrices = station.prices, priceSource = station.source, brand = base.brand ?: station.brand)
}

private suspend fun FuelPrices.stationsOrEmpty(
    center: LatLng,
    area: GeoBounds,
): List<PricedStation> =
    try {
        stations(center, area)
    } catch (e: CancellationException) {
        throw e
    } catch (_: Exception) {
        emptyList()
    }

private const val MAX_RESULTS = 50
private const val MATCH_METERS = 200.0
