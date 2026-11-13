package nz.eloque.openmaps.ui.stop

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import nz.eloque.openmaps.core.model.TransitRepository
import nz.eloque.openmaps.ui.components.DepartureGroup
import nz.eloque.openmaps.ui.components.LoadState
import nz.eloque.openmaps.ui.components.groupDepartures
import nz.eloque.openmaps.ui.components.refreshing

@OptIn(ExperimentalCoroutinesApi::class)
class StopViewModel(
    private val transit: TransitRepository,
    private val stopId: String,
) : ViewModel() {
    private val retries = MutableStateFlow(0)

    val departures: StateFlow<LoadState<List<DepartureGroup>>> =
        retries
            .flatMapLatest {
                refreshing { groupDepartures(transit.departures(stopId, count = 60).departures) }
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LoadState.Loading)

    fun retry() {
        retries.value++
    }
}
