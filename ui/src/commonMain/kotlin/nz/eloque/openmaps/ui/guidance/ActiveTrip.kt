package nz.eloque.openmaps.ui.guidance

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.navigation3.runtime.NavKey
import nz.eloque.openmaps.core.model.TravelMode
import nz.eloque.openmaps.ui.navigation.Endpoint
import nz.eloque.openmaps.ui.navigation.NavigationKey

/** The screen of the trip being guided, kept beyond the screen itself so a reopened app can show it again. */
class ActiveTrip {
    var key: NavKey? by mutableStateOf(null)

    /** Marks a car trip started outside the phone's screens, such as from Android Auto, so the phone shows it too. */
    fun drivingTo(
        name: String,
        latitude: Double,
        longitude: Double,
    ) {
        key = NavigationKey(from = null, to = Endpoint(name, latitude, longitude), mode = TravelMode.CAR)
    }
}
