package nz.eloque.openmaps.ui.category

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import nz.eloque.openmaps.core.model.CategorySearch
import nz.eloque.openmaps.core.model.GeoBounds
import nz.eloque.openmaps.core.model.LatLng
import nz.eloque.openmaps.core.model.PlaceCategory
import nz.eloque.openmaps.core.model.SearchResult
import nz.eloque.openmaps.ui.components.LoadState
import nz.eloque.openmaps.ui.components.loadOnce

class CategoryViewModel(
    private val search: CategorySearch,
    private val category: PlaceCategory,
) : ViewModel() {
    private val _results = MutableStateFlow<LoadState<List<SearchResult.Place>>>(LoadState.Loading)
    val results: StateFlow<LoadState<List<SearchResult.Place>>> = _results.asStateFlow()

    /** The area the current results cover, or null before the first search. */
    private val _searchedArea = MutableStateFlow<GeoBounds?>(null)
    val searchedArea: StateFlow<GeoBounds?> = _searchedArea.asStateFlow()

    /** Whether the map has been fitted to the first results. Later searches leave the camera alone. */
    var fitted = false

    private var center: LatLng? = null
    private var job: Job? = null

    fun search(
        center: LatLng,
        bounds: GeoBounds,
    ) {
        this.center = center
        _searchedArea.value = bounds
        _results.value = LoadState.Loading
        job?.cancel()
        job = viewModelScope.launch { _results.value = loadOnce { search.search(category, center, bounds) } }
    }

    /** Treats [bounds] as searched, for when the camera moved to show the results already found. */
    fun showing(bounds: GeoBounds) {
        _searchedArea.value = bounds
    }

    fun retry() {
        val center = center ?: return
        val bounds = _searchedArea.value ?: return
        search(center, bounds)
    }
}
