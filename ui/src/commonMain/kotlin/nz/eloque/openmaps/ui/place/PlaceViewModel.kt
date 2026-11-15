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
import nz.eloque.openmaps.core.model.MediaLinks
import nz.eloque.openmaps.core.model.OsmId
import nz.eloque.openmaps.core.model.PlaceDetails
import nz.eloque.openmaps.core.model.PlaceDetailsSource
import nz.eloque.openmaps.core.model.PlaceMedia
import nz.eloque.openmaps.core.model.PlaceMediaSource
import nz.eloque.openmaps.core.model.TransitRepository
import nz.eloque.openmaps.ui.components.LoadState
import nz.eloque.openmaps.ui.components.NearbyStop
import nz.eloque.openmaps.ui.components.loadOnce
import nz.eloque.openmaps.ui.components.nearbyStops
import nz.eloque.openmaps.ui.components.refreshing

class PlaceViewModel(
    private val source: PlaceDetailsSource,
    private val mediaSource: PlaceMediaSource,
    transit: TransitRepository,
    private val osmId: OsmId?,
    private val location: LatLng,
    /** False for dropped pins, which are not a place anyone photographs or writes about. */
    private val withMedia: Boolean,
) : ViewModel() {
    val nearby: StateFlow<LoadState<List<NearbyStop>>> =
        refreshing { transit.nearbyStops(location, maxStops = 2, maxLines = 3) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LoadState.Loading)

    private val _details = MutableStateFlow<LoadState<PlaceDetails?>>(LoadState.Loading)
    val details: StateFlow<LoadState<PlaceDetails?>> = _details.asStateFlow()

    private val _media = MutableStateFlow<PlaceMedia?>(null)
    val media: StateFlow<PlaceMedia?> = _media.asStateFlow()

    init {
        load()
    }

    fun load() {
        _details.value = if (osmId == null) LoadState.Loaded(null) else LoadState.Loading
        viewModelScope.launch {
            if (osmId != null) _details.value = loadOnce { source.details(osmId) }
            val details = _details.value as? LoadState.Loaded ?: return@launch
            if (withMedia && _media.value == null) {
                _media.value =
                    runCatching { mediaSource.media(details.value?.links ?: MediaLinks(), location, details.value?.bounds) }.getOrNull()
            }
        }
    }
}
