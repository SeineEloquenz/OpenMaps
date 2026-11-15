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
import nz.eloque.openmaps.core.model.GeoBounds
import nz.eloque.openmaps.core.model.LatLng
import nz.eloque.openmaps.core.model.OsmId
import nz.eloque.openmaps.core.model.PlaceCategory
import nz.eloque.openmaps.core.model.PlaceDetails
import nz.eloque.openmaps.core.model.PlaceDetailsSource
import nz.eloque.openmaps.core.model.SearchResult
import nz.eloque.openmaps.ui.components.LoadState
import nz.eloque.openmaps.ui.components.loadOnce
import nz.eloque.openmaps.ui.place.toCategoryLabel

/** Which results to show. Places without the needed information are hidden while a filter is on. */
data class CategoryFilters(
    val openNow: Boolean = false,
    /** An OSM cuisine value, or null for any. */
    val cuisine: String? = null,
    val wheelchair: Boolean = false,
)

class CategoryViewModel(
    private val search: CategorySearch,
    private val detailsSource: PlaceDetailsSource,
    private val category: PlaceCategory,
) : ViewModel() {
    private val _results = MutableStateFlow<LoadState<List<SearchResult.Place>>>(LoadState.Loading)
    val results: StateFlow<LoadState<List<SearchResult.Place>>> = _results.asStateFlow()

    /** Opening hours, cuisine and more for the results, empty until loaded. */
    private val _details = MutableStateFlow<Map<OsmId, PlaceDetails>>(emptyMap())
    val details: StateFlow<Map<OsmId, PlaceDetails>> = _details.asStateFlow()

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
                val state = loadOnce { search.search(category, center, area).map { it.withName() } }
                _results.value = state
                val ids = (state as? LoadState.Loaded)?.value.orEmpty().mapNotNull { it.osmId }
                if (ids.isEmpty()) return@launch
                try {
                    _details.value = detailsSource.detailsOf(ids)
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
