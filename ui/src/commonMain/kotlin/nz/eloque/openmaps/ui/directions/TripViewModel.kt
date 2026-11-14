package nz.eloque.openmaps.ui.directions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import nz.eloque.openmaps.core.model.Itinerary
import nz.eloque.openmaps.core.model.TransitRepository
import nz.eloque.openmaps.ui.components.LoadState
import nz.eloque.openmaps.ui.components.refreshing

@OptIn(ExperimentalCoroutinesApi::class)
class TripViewModel(
    private val transit: TransitRepository,
    private val tripId: String,
) : ViewModel() {
    private val retries = MutableStateFlow(0)

    val trip: StateFlow<LoadState<Itinerary>> =
        retries
            .flatMapLatest { refreshing { transit.trip(tripId) } }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LoadState.Loading)

    fun retry() {
        retries.value++
    }
}
