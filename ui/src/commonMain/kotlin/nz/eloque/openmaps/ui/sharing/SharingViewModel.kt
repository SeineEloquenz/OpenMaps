package nz.eloque.openmaps.ui.sharing

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import nz.eloque.openmaps.core.model.GeoBounds
import nz.eloque.openmaps.core.model.LatLng
import nz.eloque.openmaps.core.model.SharedMobility
import nz.eloque.openmaps.core.model.SharingSnapshot
import nz.eloque.openmaps.ui.components.LoadState
import nz.eloque.openmaps.ui.components.loadOnce
import kotlin.math.cos

/** Groups of vehicle kinds offered as filters. */
enum class SharingFilter { BIKES, SCOOTERS, CARS }

class SharingViewModel(
    private val sharing: SharedMobility,
) : ViewModel() {
    private val _snapshot = MutableStateFlow<LoadState<SharingSnapshot>>(LoadState.Loading)
    val snapshot: StateFlow<LoadState<SharingSnapshot>> = _snapshot.asStateFlow()

    private val _searchedArea = MutableStateFlow<GeoBounds?>(null)
    val searchedArea: StateFlow<GeoBounds?> = _searchedArea.asStateFlow()

    private val _filters = MutableStateFlow(SharingFilter.entries.toSet())
    val filters: StateFlow<Set<SharingFilter>> = _filters.asStateFlow()

    var fitted = false
    private var job: Job? = null

    fun toggle(filter: SharingFilter) {
        _filters.value = if (filter in _filters.value) _filters.value - filter else _filters.value + filter
    }

    /** Loads what is available in [bounds], kept between [MIN_RADIUS_METERS] and [MAX_RADIUS_METERS] around [center]. */
    fun load(
        center: LatLng,
        bounds: GeoBounds,
    ) {
        val area = bounds.coveringAtLeast(center, MIN_RADIUS_METERS).within(center, MAX_RADIUS_METERS)
        _searchedArea.value = area
        _snapshot.value = LoadState.Loading
        job?.cancel()
        job = viewModelScope.launch { _snapshot.value = loadOnce { sharing.available(area) } }
    }

    fun showing(bounds: GeoBounds) {
        _searchedArea.value = bounds
    }

    fun retry(center: LatLng) {
        _searchedArea.value?.let { load(center, it) }
    }
}

/** These bounds, cut down to at most [radiusMeters] from [center], so a zoomed-out map does not ask for a whole city's vehicles. */
private fun GeoBounds.within(
    center: LatLng,
    radiusMeters: Double,
): GeoBounds {
    val latitude = radiusMeters / METERS_PER_DEGREE
    val longitude = latitude / cos(center.latitude * kotlin.math.PI / 180)
    return GeoBounds(
        south = maxOf(south, center.latitude - latitude),
        west = maxOf(west, center.longitude - longitude),
        north = minOf(north, center.latitude + latitude),
        east = minOf(east, center.longitude + longitude),
    )
}

private const val MIN_RADIUS_METERS = 500.0
private const val MAX_RADIUS_METERS = 1500.0
private const val METERS_PER_DEGREE = 111_320.0
