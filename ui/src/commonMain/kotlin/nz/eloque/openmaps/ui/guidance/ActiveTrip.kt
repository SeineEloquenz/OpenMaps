package nz.eloque.openmaps.ui.guidance

import androidx.navigation3.runtime.NavKey

/** The screen of the trip being guided, kept beyond the screen itself so a reopened app can show it again. */
class ActiveTrip {
    var key: NavKey? = null
}
