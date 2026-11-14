package nz.eloque.openmaps.ui.place

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import nz.eloque.openmaps.core.model.LatLng
import nz.eloque.openmaps.core.model.OsmId
import nz.eloque.openmaps.core.model.PlaceDetails
import nz.eloque.openmaps.core.model.PlaceDetailsSource
import nz.eloque.openmaps.core.model.TransitRepository
import nz.eloque.openmaps.ui.components.LoadState
import nz.eloque.openmaps.ui.components.NearbyStop
import nz.eloque.openmaps.ui.components.loadOnce
import nz.eloque.openmaps.ui.components.nearbyStops
import nz.eloque.openmaps.ui.components.refreshing

class PlaceViewModel(
    private val source: PlaceDetailsSource,
    transit: TransitRepository,
    private val osmId: OsmId?,
    location: LatLng,
) : ViewModel() {
    val nearby: StateFlow<LoadState<List<NearbyStop>>> =
        refreshing { transit.nearbyStops(location, maxStops = 2, maxLines = 3) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LoadState.Loading)

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
