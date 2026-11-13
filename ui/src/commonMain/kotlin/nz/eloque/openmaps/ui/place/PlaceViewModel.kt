package nz.eloque.openmaps.ui.place

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import nz.eloque.openmaps.core.model.OsmId
import nz.eloque.openmaps.core.model.PlaceDetails
import nz.eloque.openmaps.core.model.PlaceDetailsSource
import nz.eloque.openmaps.ui.components.LoadState
import nz.eloque.openmaps.ui.components.loadOnce

class PlaceViewModel(
    private val source: PlaceDetailsSource,
    private val osmId: OsmId?,
) : ViewModel() {
    private val _details = MutableStateFlow<LoadState<PlaceDetails?>>(LoadState.Loading)
    val details: StateFlow<LoadState<PlaceDetails?>> = _details.asStateFlow()

    init {
        load()
    }

    fun load() {
        if (osmId == null) {
            _details.value = LoadState.Loaded(null)
            return
        }
        _details.value = LoadState.Loading
        viewModelScope.launch { _details.value = loadOnce { source.details(osmId) } }
    }
}
