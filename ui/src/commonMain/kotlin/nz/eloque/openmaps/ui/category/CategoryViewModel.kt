package nz.eloque.openmaps.ui.category

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import nz.eloque.openmaps.core.model.CategorySearch
import nz.eloque.openmaps.core.model.ChargingStations
import nz.eloque.openmaps.core.model.GeoBounds
import nz.eloque.openmaps.core.model.LatLng
import nz.eloque.openmaps.core.model.PlaceCategory
import nz.eloque.openmaps.core.model.PlaceDetails
import nz.eloque.openmaps.core.model.PlaceDetailsSource
import nz.eloque.openmaps.core.model.SearchResult
import nz.eloque.openmaps.ui.components.LoadState
import nz.eloque.openmaps.ui.components.loadOnce
import nz.eloque.openmaps.ui.place.toCategoryLabel

class CategoryViewModel(
    private val search: CategorySearch,
    private val detailsSource: PlaceDetailsSource,
    private val charging: ChargingStations,
    private val category: PlaceCategory,
) : ViewModel() {
    /** Whether results come from the charging station database instead of OpenStreetMap, which needs attribution. */
    val fromChargingDatabase: Boolean
        get() = category == PlaceCategory.CHARGING && charging.available

    private val _results = MutableStateFlow<LoadState<List<SearchResult.Place>>>(LoadState.Loading)
    val results: StateFlow<LoadState<List<SearchResult.Place>>> = _results.asStateFlow()

    /** Opening hours, cuisine and more for the results, empty until loaded. */
    private val _details = MutableStateFlow<Map<SearchResult.Place, PlaceDetails>>(emptyMap())
    val details: StateFlow<Map<SearchResult.Place, PlaceDetails>> = _details.asStateFlow()

    private val _filters = MutableStateFlow(CategoryFilters())
    val filters: StateFlow<CategoryFilters> = _filters.asStateFlow()

    fun setFilters(filters: CategoryFilters) {
        _filters.value = filters
    }

    /** The area the current results cover, or null before the first search. */
    private val _searchedArea = MutableStateFlow<GeoBounds?>(null)
    val searchedArea: StateFlow<GeoBounds?> = _searchedArea.asStateFlow()

    /** Whether the map has been fitted to the first results. Later searches leave the camera alone. */
    var fitted = false

    private var center: LatLng? = null
    private var job: Job? = null

    /** Searches [bounds], grown to at least [MIN_RADIUS_METERS] around [center] so a close zoom still finds enough places. */
    fun search(
        center: LatLng,
        bounds: GeoBounds,
    ) {
        val area = bounds.coveringAtLeast(center, MIN_RADIUS_METERS)
        this.center = center
        _searchedArea.value = area
        _results.value = LoadState.Loading
        _details.value = emptyMap()
        job?.cancel()
        job =
            viewModelScope.launch {
                if (fromChargingDatabase) {
                    val state = loadOnce { charging.stations(center, area).map { (place, details) -> place.withName() to details } }
                    _details.value = (state as? LoadState.Loaded)?.value.orEmpty().toMap()
                    _results.value =
                        when (state) {
                            is LoadState.Loaded -> LoadState.Loaded(state.value.map { it.first })
                            LoadState.Failed -> LoadState.Failed
                            LoadState.Loading -> LoadState.Loading
                        }
                    return@launch
                }
                val state = loadOnce { search.search(category, center, area).map { it.withName() } }
                _results.value = state
                val places = (state as? LoadState.Loaded)?.value.orEmpty().filter { it.osmId != null }
                if (places.isEmpty()) return@launch
                try {
                    val byId = detailsSource.detailsOf(places.mapNotNull { it.osmId })
                    _details.value = places.mapNotNull { place -> byId[place.osmId]?.let { place to it } }.toMap()
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    // The list stays usable with names and distances only.
                }
            }
    }

    /** Treats [bounds] as searched, for when the camera moved to show the results already found. */
    fun showing(bounds: GeoBounds) {
        _searchedArea.value = bounds
    }

    private fun SearchResult.Place.withName() = if (name.isBlank()) copy(name = category?.toCategoryLabel().orEmpty()) else this

    fun retry() {
        val center = center ?: return
        val bounds = _searchedArea.value ?: return
        search(center, bounds)
    }
}

private const val MIN_RADIUS_METERS = 1000.0
