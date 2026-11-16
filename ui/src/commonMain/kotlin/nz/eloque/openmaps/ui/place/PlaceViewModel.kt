package nz.eloque.openmaps.ui.place

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import nz.eloque.openmaps.core.model.FuelPrices
import nz.eloque.openmaps.core.model.GeoBounds
import nz.eloque.openmaps.core.model.LatLng
import nz.eloque.openmaps.core.model.MediaLinks
import nz.eloque.openmaps.core.model.PlaceDetails
import nz.eloque.openmaps.core.model.PlaceDetailsSource
import nz.eloque.openmaps.core.model.PlaceId
import nz.eloque.openmaps.core.model.PlaceMedia
import nz.eloque.openmaps.core.model.PlaceMediaSource
import nz.eloque.openmaps.core.model.PricedStation
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
    private val fuel: FuelPrices?,
    private val id: PlaceId?,
    private val location: LatLng,
    /** False for dropped pins, which are not a place anyone photographs or writes about. */
    private val withMedia: Boolean,
    private val gasStation: Boolean,
) : ViewModel() {
    val nearby: StateFlow<LoadState<List<NearbyStop>>> =
        refreshing { transit.nearbyStops(location, maxStops = 2, maxLines = 3) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LoadState.Loading)

    private val _details = MutableStateFlow<LoadState<PlaceDetails?>>(LoadState.Loading)
    val details: StateFlow<LoadState<PlaceDetails?>> = _details.asStateFlow()

    private val _media = MutableStateFlow<PlaceMedia?>(null)
    val media: StateFlow<PlaceMedia?> = _media.asStateFlow()

    private val _prices = MutableStateFlow<PricedStation?>(null)

    /** The station's current pump prices, for gas stations in a country with a price source. */
    val prices: StateFlow<PricedStation?> = _prices.asStateFlow()

    /** A web page about the place for sharing, or null when its source has none. */
    val pageUrl: String? = id?.let(source::pageUrl)

    init {
        load()
        if (gasStation && fuel != null) {
            viewModelScope.launch {
                val around =
                    GeoBounds(
                        location.latitude,
                        location.longitude,
                        location.latitude,
                        location.longitude,
                    ).coveringAtLeast(location, SEARCH_METERS)
                _prices.value =
                    runCatching { fuel.stations(location, around) }
                        .getOrNull()
                        ?.minByOrNull { it.location.distanceTo(location) }
                        ?.takeIf { it.location.distanceTo(location) <= MATCH_METERS }
            }
        }
    }

    fun load() {
        _details.value = if (id == null) LoadState.Loaded(null) else LoadState.Loading
        viewModelScope.launch {
            if (id != null) _details.value = loadOnce { source.details(id) }
            val details = _details.value as? LoadState.Loaded ?: return@launch
            if (withMedia && _media.value == null) {
                _media.value =
                    runCatching { mediaSource.media(details.value?.links ?: MediaLinks(), location, details.value?.bounds) }.getOrNull()
            }
        }
    }
}

private const val SEARCH_METERS = 250.0
private const val MATCH_METERS = 200.0
