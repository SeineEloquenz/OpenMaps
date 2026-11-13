package nz.eloque.openmaps.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import nz.eloque.openmaps.core.model.Favourite
import nz.eloque.openmaps.core.model.Geocoder
import nz.eloque.openmaps.core.model.LatLng
import nz.eloque.openmaps.core.model.PlacesStore
import nz.eloque.openmaps.core.model.SearchResult
import nz.eloque.openmaps.core.model.storageKey
import nz.eloque.openmaps.ui.components.LoadState
import nz.eloque.openmaps.ui.components.loadOnce

data class Suggestions(
    val favourites: List<Favourite>,
    val recents: List<SearchResult>,
)

@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
class SearchViewModel(
    private val geocoder: Geocoder,
    private val places: PlacesStore,
) : ViewModel() {
    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    var near: LatLng? = null
    var stopsFirst: Boolean = false

    /** Shown before typing. Recents that are also favourites are left out. */
    val suggestions: StateFlow<Suggestions> =
        combine(places.favourites, places.recents) { favourites, recents ->
            val saved = favourites.map { it.place.storageKey }.toSet()
            Suggestions(favourites, recents.filterNot { it.storageKey in saved })
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), Suggestions(emptyList(), emptyList()))

    /** Null while the query is too short to search. */
    val results: StateFlow<LoadState<List<SearchResult>>?> =
        _query
            .debounce(DEBOUNCE_MILLIS)
            .flatMapLatest { query ->
                flow {
                    val trimmed = query.trim()
                    if (trimmed.length < MIN_QUERY_LENGTH) {
                        emit(null)
                    } else {
                        emit(LoadState.Loading)
                        emit(loadOnce { geocoder.search(trimmed, near, stopsFirst) })
                    }
                }
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun setQuery(value: String) {
        _query.value = value
    }

    fun remember(result: SearchResult) {
        viewModelScope.launch { places.addRecent(result) }
    }

    private companion object {
        const val DEBOUNCE_MILLIS = 300L
        const val MIN_QUERY_LENGTH = 2
    }
}
