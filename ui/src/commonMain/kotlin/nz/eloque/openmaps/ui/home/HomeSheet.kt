package nz.eloque.openmaps.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.LocalParking
import androidx.compose.material.icons.outlined.Bookmarks
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import nz.eloque.openmaps.core.model.Favourite
import nz.eloque.openmaps.core.model.FavouriteLabel
import nz.eloque.openmaps.core.model.ParkedCar
import nz.eloque.openmaps.core.model.PlacesStore
import nz.eloque.openmaps.core.model.SearchResult
import nz.eloque.openmaps.core.model.SettingsStore
import nz.eloque.openmaps.core.model.TravelMode
import nz.eloque.openmaps.map.MapController
import nz.eloque.openmaps.ui.components.LoadState
import nz.eloque.openmaps.ui.components.LoadStateContent
import nz.eloque.openmaps.ui.components.NearbyStopCard
import nz.eloque.openmaps.ui.components.clockTime
import nz.eloque.openmaps.ui.components.formatDistance
import nz.eloque.openmaps.ui.components.icon
import nz.eloque.openmaps.ui.components.rememberNow
import nz.eloque.openmaps.ui.navigation.StopKey
import nz.eloque.openmaps.ui.resources.Res
import nz.eloque.openmaps.ui.resources.allow_location
import nz.eloque.openmaps.ui.resources.clear
import nz.eloque.openmaps.ui.resources.nearby_departures
import nz.eloque.openmaps.ui.resources.nearby_needs_location
import nz.eloque.openmaps.ui.resources.no_departures
import nz.eloque.openmaps.ui.resources.parked_car
import nz.eloque.openmaps.ui.resources.parked_since
import nz.eloque.openmaps.ui.resources.save_parking
import nz.eloque.openmaps.ui.resources.saved
import nz.eloque.openmaps.ui.resources.walk_there
import nz.eloque.openmaps.ui.search.titleRes
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import kotlin.time.Clock

@Composable
fun HomeSheet(
    map: MapController,
    onOpenStop: (StopKey) -> Unit,
    onDirectionsTo: (SearchResult) -> Unit,
    onSetFavourite: (FavouriteLabel) -> Unit,
    onOpenSaved: () -> Unit,
    onOpenTrip: (tripId: String, stopName: String) -> Unit,
) {
    val viewModel = koinViewModel<HomeViewModel>()
    val nearby by viewModel.nearby.collectAsStateWithLifecycle()
    val favourites by koinInject<PlacesStore>().favourites.collectAsStateWithLifecycle(emptyList())
    val now by rememberNow()
    val settings = koinInject<SettingsStore>()
    val parked by settings.parkedCar.collectAsStateWithLifecycle()
    val unit by settings.distanceUnit.collectAsStateWithLifecycle()
    val carName = stringResource(Res.string.parked_car)
    val userLocation = map.userLocation
    LaunchedEffect(userLocation) { userLocation?.let(viewModel::setLocation) }

    LazyColumn(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        item {
            FavouritesRow(
                favourites,
                onDirectionsTo,
                onSetFavourite,
                onOpenSaved,
                onSaveParking =
                    userLocation?.takeIf {
                        parked == null
                    }?.let { here ->
                        { settings.setParkedCar(ParkedCar(here, Clock.System.now())) }
                    },
            )
        }
        parked?.let { car ->
            item {
                ParkedCarCard(
                    car,
                    distance = userLocation?.let { formatDistance(it.distanceTo(car.location), unit) },
                    onWalk = {
                        settings.setTravelMode(TravelMode.WALK)
                        onDirectionsTo(car.toSearchResult(carName))
                    },
                    onClear = { settings.setParkedCar(null) },
                )
            }
        }
        item {
            Text(
                stringResource(Res.string.nearby_departures),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(vertical = 8.dp),
            )
        }
        when (val state = nearby) {
            null ->
                item {
                    if (!map.isLocationPermissionGranted) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(stringResource(Res.string.nearby_needs_location), color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Button(onClick = map::requestLocationPermission) { Text(stringResource(Res.string.allow_location)) }
                        }
                    } else {
                        LoadStateContent(LoadState.Loading, onRetry = {}) {}
                    }
                }
            is LoadState.Loaded ->
                if (state.value.isEmpty()) {
                    item { Text(stringResource(Res.string.no_departures), color = MaterialTheme.colorScheme.onSurfaceVariant) }
                } else {
                    items(state.value, key = { it.name }) { stop ->
                        NearbyStopCard(stop, now, onOpenTrip = onOpenTrip, onClick = {
                            stop.id?.let { onOpenStop(StopKey(it, stop.name, stop.location.latitude, stop.location.longitude)) }
                        })
                    }
                }
            else -> item { LoadStateContent(state, onRetry = viewModel::retry) {} }
        }
    }
}

/** Home and work always show, so they can be set with one tap. Tapping a saved place plans a route there. */
@Composable
private fun FavouritesRow(
    favourites: List<Favourite>,
    onDirectionsTo: (SearchResult) -> Unit,
    onSetFavourite: (FavouriteLabel) -> Unit,
    onOpenSaved: () -> Unit,
    onSaveParking: (() -> Unit)?,
) {
    val missing = listOf(FavouriteLabel.HOME, FavouriteLabel.WORK).filter { label -> favourites.none { it.label == label } }
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(favourites, key = { it.id }) { favourite ->
            AssistChip(
                onClick = { onDirectionsTo(favourite.place) },
                label = { Text(favourite.label.titleRes?.let { stringResource(it) } ?: favourite.displayName, maxLines = 1) },
                leadingIcon = { Icon(favourite.label.icon, contentDescription = null) },
            )
        }
        items(missing) { label ->
            AssistChip(
                onClick = { onSetFavourite(label) },
                label = { Text(label.titleRes?.let { stringResource(it) }.orEmpty()) },
                leadingIcon = { Icon(Icons.Default.Add, contentDescription = null) },
            )
        }
        onSaveParking?.let { save ->
            item {
                AssistChip(
                    onClick = save,
                    label = { Text(stringResource(Res.string.save_parking)) },
                    leadingIcon = { Icon(Icons.Default.LocalParking, contentDescription = null) },
                )
            }
        }
        item {
            AssistChip(
                onClick = onOpenSaved,
                label = { Text(stringResource(Res.string.saved)) },
                leadingIcon = { Icon(Icons.Outlined.Bookmarks, contentDescription = null) },
            )
        }
    }
}

@Composable
private fun ParkedCarCard(
    car: ParkedCar,
    distance: String?,
    onWalk: () -> Unit,
    onClear: () -> Unit,
) {
    OutlinedCard(Modifier.fillMaxWidth().padding(top = 8.dp)) {
        Row(Modifier.padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.DirectionsCar, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Column(Modifier.weight(1f).padding(start = 16.dp)) {
                Text(stringResource(Res.string.parked_car), style = MaterialTheme.typography.titleSmall)
                Text(
                    listOfNotNull(stringResource(Res.string.parked_since, clockTime(car.since)), distance).joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            TextButton(onClick = onWalk) { Text(stringResource(Res.string.walk_there)) }
            IconButton(onClick = onClear) { Icon(Icons.Default.Close, contentDescription = stringResource(Res.string.clear)) }
        }
    }
}

private fun ParkedCar.toSearchResult(name: String) =
    SearchResult.Place(id = null, name = name, location = location, area = null, category = "parking")
