package nz.eloque.openmaps.ui.trips

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Directions
import androidx.compose.material.icons.filled.TrackChanges
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import nz.eloque.openmaps.core.model.FavouriteLabel
import nz.eloque.openmaps.core.model.PlacesStore
import nz.eloque.openmaps.core.model.SavedRoute
import nz.eloque.openmaps.core.model.SearchResult
import nz.eloque.openmaps.core.model.TripWatchStore
import nz.eloque.openmaps.core.model.TripWatcher
import nz.eloque.openmaps.core.model.WatchedTrip
import nz.eloque.openmaps.ui.components.clockTime
import nz.eloque.openmaps.ui.components.icon
import nz.eloque.openmaps.ui.resources.Res
import nz.eloque.openmaps.ui.resources.delete
import nz.eloque.openmaps.ui.resources.saved_routes
import nz.eloque.openmaps.ui.resources.trips
import nz.eloque.openmaps.ui.resources.trips_empty
import nz.eloque.openmaps.ui.resources.trips_not_set
import nz.eloque.openmaps.ui.resources.trips_watched
import nz.eloque.openmaps.ui.resources.trips_watched_leg
import nz.eloque.openmaps.ui.resources.trips_watched_stop
import nz.eloque.openmaps.ui.resources.your_location
import nz.eloque.openmaps.ui.saved.SectionTitle
import nz.eloque.openmaps.ui.search.titleRes
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject

/** Where the user goes: home and work, trips being watched and saved routes. */
@Composable
fun TripsSheet(
    onDirectionsTo: (SearchResult) -> Unit,
    onSetFavourite: (FavouriteLabel) -> Unit,
    onOpenRoute: (SavedRoute) -> Unit,
    onOpenWatched: (WatchedTrip) -> Unit,
) {
    val places = koinInject<PlacesStore>()
    val favourites by places.favourites.collectAsStateWithLifecycle(emptyList())
    val routes by places.savedRoutes.collectAsStateWithLifecycle(emptyList())
    val watched by koinInject<TripWatchStore>().trips.collectAsStateWithLifecycle(emptyList())
    val watcher = koinInject<TripWatcher>()
    val scope = rememberCoroutineScope()
    val transparent = ListItemDefaults.colors(containerColor = Color.Transparent)
    val here = stringResource(Res.string.your_location)

    LazyColumn(Modifier.fillMaxWidth()) {
        item {
            Text(
                stringResource(Res.string.trips),
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
        }
        items(listOf(FavouriteLabel.HOME, FavouriteLabel.WORK), key = { "label-$it" }) { label ->
            val favourite = favourites.firstOrNull { it.label == label }
            val title = label.titleRes?.let { stringResource(it) }.orEmpty()
            ListItem(
                headlineContent = { Text(title) },
                supportingContent = { Text(favourite?.displayName ?: stringResource(Res.string.trips_not_set)) },
                leadingContent = { Icon(if (favourite != null) label.icon else Icons.Default.Add, contentDescription = null) },
                colors = transparent,
                modifier =
                    Modifier.clickable {
                        if (favourite != null) onDirectionsTo(favourite.place) else onSetFavourite(label)
                    },
            )
        }
        if (watched.isNotEmpty()) item { SectionTitle(Res.string.trips_watched) }
        items(watched, key = { "watch-${it.id}" }) { trip ->
            WatchedTripItem(trip, onClick = { onOpenWatched(trip) }, onStop = { watcher.stop(trip.id) })
        }
        if (routes.isNotEmpty()) item { SectionTitle(Res.string.saved_routes) }
        items(routes, key = { "route-${it.id}" }) { route ->
            ListItem(
                headlineContent = { Text(route.to?.name ?: here) },
                supportingContent = { Text(route.from?.name ?: here) },
                leadingContent = { Icon(Icons.Default.Directions, contentDescription = null) },
                trailingContent = {
                    IconButton(onClick = { scope.launch { places.removeRoute(route.id) } }) {
                        Icon(Icons.Default.Delete, contentDescription = stringResource(Res.string.delete))
                    }
                },
                colors = transparent,
                modifier = Modifier.clickable { onOpenRoute(route) },
            )
        }
        if (watched.isEmpty() && routes.isEmpty()) {
            item {
                Text(
                    stringResource(Res.string.trips_empty),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp),
                )
            }
        }
    }
}

@Composable
private fun WatchedTripItem(
    trip: WatchedTrip,
    onClick: () -> Unit,
    onStop: () -> Unit,
) {
    val first = trip.legs.first()
    val late = trip.legs.any { it.cancelled || it.from.delay.inWholeMinutes != 0L || it.to.delay.inWholeMinutes != 0L }
    ListItem(
        headlineContent = { Text(trip.destination) },
        supportingContent = {
            Text(
                stringResource(Res.string.trips_watched_leg, first.line, clockTime(first.from.expected), first.from.name),
                color = if (late) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        leadingContent = { Icon(Icons.Default.TrackChanges, contentDescription = null) },
        trailingContent = {
            IconButton(onClick = onStop) {
                Icon(Icons.Default.Close, contentDescription = stringResource(Res.string.trips_watched_stop))
            }
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier.clickable(onClick = onClick),
    )
}
