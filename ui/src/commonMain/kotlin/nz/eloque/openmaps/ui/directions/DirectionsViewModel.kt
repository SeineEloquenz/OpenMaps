package nz.eloque.openmaps.ui.directions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import nz.eloque.openmaps.core.model.Itinerary
import nz.eloque.openmaps.core.model.LatLng
import nz.eloque.openmaps.core.model.PlanRequest
import nz.eloque.openmaps.core.model.PlanResult
import nz.eloque.openmaps.core.model.PlanTarget
import nz.eloque.openmaps.core.model.SettingsStore
import nz.eloque.openmaps.core.model.TransitRepository
import nz.eloque.openmaps.core.model.TransportMode
import nz.eloque.openmaps.core.model.TravelMode
import nz.eloque.openmaps.ui.components.LoadState
import nz.eloque.openmaps.ui.components.loadOnce
import nz.eloque.openmaps.ui.navigation.DirectionsKey
import nz.eloque.openmaps.ui.navigation.Endpoint
import kotlin.time.Instant

sealed interface TimeChoice {
    data object Now : TimeChoice

    data class DepartAt(
        val time: Instant,
    ) : TimeChoice

    data class ArriveBy(
        val time: Instant,
    ) : TimeChoice
}

data class DirectionsResult(
    val itineraries: List<Itinerary>,
    val direct: Itinerary?,
    val earlierCursor: String?,
    val laterCursor: String?,
)

class DirectionsViewModel(
    private val transit: TransitRepository,
    private val cache: ItineraryCache,
    private val settings: SettingsStore,
    directions: DirectionsKey,
) : ViewModel() {
    private val from: Endpoint? = directions.from
    private val to: Endpoint? = directions.to

    private val _time = MutableStateFlow<TimeChoice>(TimeChoice.Now)
    val time: StateFlow<TimeChoice> = _time.asStateFlow()

    val mode: StateFlow<TravelMode> = settings.travelMode

    /** Public transport routes, null until asked for or while waiting for the user's location. */
    private val _result = MutableStateFlow<LoadState<DirectionsResult>?>(null)
    val result: StateFlow<LoadState<DirectionsResult>?> = _result.asStateFlow()

    /** Direct routes by mode, null until a tab needs them. */
    private val _street = MutableStateFlow<LoadState<Map<TransportMode, Itinerary>>?>(null)
    val street: StateFlow<LoadState<Map<TransportMode, Itinerary>>?> = _street.asStateFlow()

    private var userLocation: LatLng? = null
    private var planJob: Job? = null
    private var streetJob: Job? = null
    private var pageJob: Job? = null

    val needsUserLocation: Boolean get() = from == null || to == null

    init {
        if (!needsUserLocation) loadForMode()
        viewModelScope.launch { settings.routeOptions.drop(1).collect { reload() } }
    }

    fun setUserLocation(location: LatLng) {
        val first = userLocation == null
        userLocation = location
        if (first && needsUserLocation) loadForMode()
    }

    fun setTime(choice: TimeChoice) {
        _time.value = choice
        reload()
    }

    fun selectMode(mode: TravelMode) {
        settings.setTravelMode(mode)
        loadForMode()
    }

    fun reload() {
        pageJob?.cancel()
        planJob?.cancel()
        streetJob?.cancel()
        _result.value = null
        _street.value = null
        loadForMode()
    }

    fun retry() {
        if (_result.value == LoadState.Failed) _result.value = null
        if (_street.value == LoadState.Failed) _street.value = null
        loadForMode()
    }

    /** Asks only for what the open tab shows, to keep the load on the routing service low. */
    private fun loadForMode() {
        when (mode.value) {
            TravelMode.TRANSIT -> planTransit()
            TravelMode.WALK ->
                when (val transitState = _result.value) {
                    null, LoadState.Failed -> planStreet()
                    is LoadState.Loaded -> if (transitState.value.direct == null) planStreet()
                    LoadState.Loading -> Unit
                }
            TravelMode.CAR, TravelMode.BIKE -> planStreet()
        }
    }

    private fun planTransit() {
        if (_result.value != null) return
        val request = request(pageCursor = null) ?: return
        _result.value = LoadState.Loading
        planJob =
            viewModelScope.launch {
                _result.value = loadOnce { transit.plan(request).toDirections() }
                if (mode.value == TravelMode.WALK) loadForMode()
            }
    }

    private fun planStreet() {
        if (_street.value != null) return
        val walkKnown = (_result.value as? LoadState.Loaded)?.value?.direct != null
        val modes = setOf(TransportMode.CAR, TransportMode.BIKE) + if (walkKnown) emptySet() else setOf(TransportMode.WALK)
        val request = request(pageCursor = null)?.copy(directModes = modes, withTransit = false) ?: return
        _street.value = LoadState.Loading
        streetJob =
            viewModelScope.launch {
                _street.value =
                    loadOnce {
                        val direct = transit.plan(request).direct
                        cache.put(direct)
                        direct.filter { it.legs.isNotEmpty() }.distinctBy { it.legs.first().mode }.associateBy { it.legs.first().mode }
                    }
            }
    }

    fun loadEarlier() = page(earlier = true)

    fun loadLater() = page(earlier = false)

    private fun page(earlier: Boolean) {
        val current = (_result.value as? LoadState.Loaded)?.value ?: return
        val cursor = (if (earlier) current.earlierCursor else current.laterCursor) ?: return
        val request = request(cursor) ?: return
        if (pageJob?.isActive == true) return
        pageJob =
            viewModelScope.launch {
                try {
                    val page = transit.plan(request)
                    cache.put(page.itineraries)
                    _result.update { state ->
                        val loaded = (state as? LoadState.Loaded)?.value ?: return@update state
                        val known = loaded.itineraries.map { it.id }.toSet()
                        val added = page.itineraries.filterNot { it.id in known }
                        LoadState.Loaded(
                            if (earlier) {
                                loaded.copy(itineraries = added + loaded.itineraries, earlierCursor = page.earlierCursor)
                            } else {
                                loaded.copy(itineraries = loaded.itineraries + added, laterCursor = page.laterCursor)
                            },
                        )
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    // Paging failures leave the current results in place.
                }
            }
    }

    private fun request(pageCursor: String?): PlanRequest? {
        val fromTarget = from?.toTarget() ?: userLocation?.let { PlanTarget.Coordinates(it) } ?: return null
        val toTarget = to?.toTarget() ?: userLocation?.let { PlanTarget.Coordinates(it) } ?: return null
        val time = _time.value
        return PlanRequest(
            from = fromTarget,
            to = toTarget,
            time =
                when (time) {
                    TimeChoice.Now -> null
                    is TimeChoice.DepartAt -> time.time
                    is TimeChoice.ArriveBy -> time.time
                },
            arriveBy = time is TimeChoice.ArriveBy,
            pageCursor = pageCursor,
            options = settings.routeOptions.value,
        )
    }

    private fun PlanResult.toDirections(): DirectionsResult {
        cache.put(itineraries + direct)
        return DirectionsResult(itineraries, direct.firstOrNull(), earlierCursor, laterCursor)
    }
}

private fun Endpoint.toTarget(): PlanTarget = stopId?.let { PlanTarget.Stop(it) } ?: PlanTarget.Coordinates(location)
