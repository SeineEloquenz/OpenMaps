package nz.eloque.openmaps.ui.guidance

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import nz.eloque.openmaps.core.model.LatLng
import nz.eloque.openmaps.core.model.NavigationState
import nz.eloque.openmaps.core.model.Navigator
import nz.eloque.openmaps.core.model.SettingsStore
import nz.eloque.openmaps.core.model.TransportMode
import nz.eloque.openmaps.core.model.TravelMode
import nz.eloque.openmaps.ui.components.LoadState
import nz.eloque.openmaps.ui.components.loadOnce
import nz.eloque.openmaps.ui.navigation.NavigationKey

/** Runs one trip for as long as its screen is open. */
class NavigationViewModel(
    private val navigator: Navigator,
    private val settings: SettingsStore,
    private val key: NavigationKey,
) : ViewModel() {
    val state: StateFlow<NavigationState?> = navigator.state

    private val _start = MutableStateFlow<LoadState<Unit>?>(null)

    /** Null until a start position is known. */
    val start: StateFlow<LoadState<Unit>?> = _start.asStateFlow()

    /** Starts the trip once, from [userLocation] when the trip has no start of its own. */
    fun begin(userLocation: LatLng?) {
        if (_start.value != null && _start.value != LoadState.Failed) return
        val from = key.from?.location ?: userLocation ?: return
        _start.value = LoadState.Loading
        viewModelScope.launch {
            _start.value =
                loadOnce {
                    navigator.start(from, key.to.location, key.mode.transportMode, settings.routeOptions.value, key.simulate)
                }
        }
    }

    override fun onCleared() {
        navigator.stop()
    }
}

val TravelMode.transportMode: TransportMode
    get() =
        when (this) {
            TravelMode.CAR -> TransportMode.CAR
            TravelMode.BIKE -> TransportMode.BIKE
            TravelMode.WALK, TravelMode.TRANSIT -> TransportMode.WALK
        }
