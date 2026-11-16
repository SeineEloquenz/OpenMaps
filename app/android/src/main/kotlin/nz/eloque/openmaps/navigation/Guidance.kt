package nz.eloque.openmaps.navigation

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.merge
import nz.eloque.openmaps.core.model.Announcement
import nz.eloque.openmaps.core.model.Navigator
import nz.eloque.openmaps.core.model.TripGuide

/** True while a street or public transport trip is guided. */
fun guidanceActive(
    navigator: Navigator,
    guide: TripGuide,
): Flow<Boolean> = combine(navigator.state, guide.state) { street, transit -> street != null || transit != null }.distinctUntilChanged()

fun guidanceAnnouncements(
    navigator: Navigator,
    guide: TripGuide,
): Flow<Announcement> = merge(navigator.announcements, guide.announcements)
