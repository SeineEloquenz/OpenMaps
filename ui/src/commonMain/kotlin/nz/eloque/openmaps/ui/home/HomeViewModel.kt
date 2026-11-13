package nz.eloque.openmaps.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import nz.eloque.openmaps.core.model.LatLng
import nz.eloque.openmaps.core.model.TransitRepository
import nz.eloque.openmaps.ui.components.DepartureGroup
import nz.eloque.openmaps.ui.components.LoadState
import nz.eloque.openmaps.ui.components.groupDepartures
import nz.eloque.openmaps.ui.components.refreshing

data class NearbyStop(
    val id: String?,
    val name: String,
    val location: LatLng,
    val groups: List<DepartureGroup>,
)

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModel(
    private val transit: TransitRepository,
) : ViewModel() {
    private val location = MutableStateFlow<LatLng?>(null)
    private val retries = MutableStateFlow(0)

    /** Null until a location is known. */
    val nearby: StateFlow<LoadState<List<NearbyStop>>?> =
        combine(location, retries) { location, _ -> location }
            .flatMapLatest { location ->
                if (location == null) {
                    flowOf(null)
                } else {
                    refreshing { loadNearby(location) }
                }
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Small movements are ignored so departures don't reload with every location fix. */
    fun setLocation(newLocation: LatLng) {
        val current = location.value
        if (current == null || current.distanceTo(newLocation) > RELOAD_DISTANCE_METERS) {
            location.value = newLocation
        }
    }

    fun retry() {
        retries.value++
    }

    private suspend fun loadNearby(location: LatLng): List<NearbyStop> =
        transit
            .departuresNear(location, radiusMeters = SEARCH_RADIUS_METERS, count = 60)
            .departures
            .groupBy { it.stopName }
            .values
            .sortedBy { it.first().stopLocation.distanceTo(location) }
            .take(MAX_STOPS)
            .map { departures ->
                val first = departures.first()
                NearbyStop(first.stopId, first.stopName, first.stopLocation, groupDepartures(departures).take(MAX_LINES))
            }

    private companion object {
        const val RELOAD_DISTANCE_METERS = 150.0
        const val SEARCH_RADIUS_METERS = 500
        const val MAX_STOPS = 4
        const val MAX_LINES = 5
    }
}
