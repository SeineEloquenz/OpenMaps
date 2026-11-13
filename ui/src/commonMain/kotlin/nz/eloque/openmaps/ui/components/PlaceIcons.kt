package nz.eloque.openmaps.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Work
import androidx.compose.ui.graphics.vector.ImageVector
import nz.eloque.openmaps.core.model.FavouriteLabel
import nz.eloque.openmaps.core.model.SearchResult
import nz.eloque.openmaps.core.model.TransportMode

val SearchResult.icon: ImageVector
    get() =
        when (this) {
            is SearchResult.Stop -> StopIconPriority.firstOrNull { it in modes }?.icon ?: Icons.Default.Place
            is SearchResult.Address -> Icons.Default.Home
            is SearchResult.Place -> Icons.Default.Place
        }

private val StopIconPriority =
    listOf(
        TransportMode.HIGHSPEED_RAIL,
        TransportMode.LONG_DISTANCE_RAIL,
        TransportMode.REGIONAL_RAIL,
        TransportMode.SUBURBAN,
        TransportMode.SUBWAY,
        TransportMode.TRAM,
        TransportMode.FERRY,
        TransportMode.BUS,
        TransportMode.COACH,
    )

val FavouriteLabel.icon: ImageVector
    get() =
        when (this) {
            FavouriteLabel.HOME -> Icons.Default.Home
            FavouriteLabel.WORK -> Icons.Default.Work
            FavouriteLabel.OTHER -> Icons.Default.Star
        }
