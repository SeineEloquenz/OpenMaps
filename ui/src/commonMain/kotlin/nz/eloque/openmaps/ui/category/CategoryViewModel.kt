package nz.eloque.openmaps.ui.category

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import nz.eloque.openmaps.core.model.GeoBounds
import nz.eloque.openmaps.core.model.LatLng
import nz.eloque.openmaps.core.model.LoadedPlaces
import nz.eloque.openmaps.core.model.NearbyPlaces
import nz.eloque.openmaps.core.model.NearbyResults
import nz.eloque.openmaps.core.model.PlaceCategory
import nz.eloque.openmaps.ui.components.LoadState

class CategoryViewModel(
    private val nearby: NearbyPlaces,
    private val category: PlaceCategory,
) : ViewModel() {
    private val _results = MutableStateFlow<LoadState<NearbyResults>>(LoadState.Loading)
    val results: StateFlow<LoadState<NearbyResults>> = _results.asStateFlow()

    private val _filters = MutableStateFlow(CategoryFilters())
    val filters: StateFlow<CategoryFilters> = _filters.asStateFlow()

    fun setFilters(filters: CategoryFilters) {
        _filters.value = filters
    }

    /** The area the current results cover, or null before the first search. */
    private val _searchedArea = MutableStateFlow<GeoBounds?>(null)
    val searchedArea: StateFlow<GeoBounds?> = _searchedArea.asStateFlow()

    /** How many results the map was last fitted to. It refits as more arrive, until the user moves the map. */
    var fittedCount = 0

    private var center: LatLng? = null
    private var loaded: LoadedPlaces? = null
    private var job: Job? = null

    /** Searches [bounds], grown to at least [MIN_RADIUS_METERS] around [center] so a close zoom still finds enough places. */
    fun search(
        center: LatLng,
        bounds: GeoBounds,
        loaded: LoadedPlaces?,
    ) {
        val area = bounds.coveringAtLeast(center, MIN_RADIUS_METERS)
        this.center = center
        this.loaded = loaded
        _searchedArea.value = area
        _results.value = LoadState.Loading
        job?.cancel()
        job =
            viewModelScope.launch {
                try {
                    nearby.search(category, center, area, loaded).collect { _results.value = LoadState.Loaded(it) }
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    if (_results.value !is LoadState.Loaded) _results.value = LoadState.Failed
                }
            }
    }

    /** Treats [bounds] as searched, for when the camera moved to show the results already found. */
    fun showing(bounds: GeoBounds) {
        _searchedArea.value = bounds
    }

    fun retry() {
        val center = center ?: return
        val bounds = _searchedArea.value ?: return
        search(center, bounds, loaded)
    }
}

private const val MIN_RADIUS_METERS = 1000.0
