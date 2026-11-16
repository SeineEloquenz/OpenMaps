package nz.eloque.openmaps.ui.guidance

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.StateFlow
import nz.eloque.openmaps.core.model.Itinerary
import nz.eloque.openmaps.core.model.TripGuidance
import nz.eloque.openmaps.core.model.TripGuide
import nz.eloque.openmaps.ui.directions.ItineraryCache
import nz.eloque.openmaps.ui.navigation.TransitGuidanceKey

/** Guides one public transport trip for as long as its screen is open. */
class TransitGuidanceViewModel(
    private val guide: TripGuide,
    cache: ItineraryCache,
    key: TransitGuidanceKey,
) : ViewModel() {
    /** Null when the itinerary is no longer cached, as after the app was restarted. */
    val itinerary: Itinerary? = cache[key.itineraryId]

    val state: StateFlow<TripGuidance?> = guide.state

    init {
        itinerary?.let { guide.start(it, key.simulate) }
    }

    override fun onCleared() {
        guide.stop()
    }
}
