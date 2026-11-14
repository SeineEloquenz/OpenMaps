package nz.eloque.openmaps.ui.directions

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material.icons.filled.TripOrigin
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import nz.eloque.openmaps.core.model.Itinerary
import nz.eloque.openmaps.core.model.Leg
import nz.eloque.openmaps.core.model.PlacesStore
import nz.eloque.openmaps.core.model.SettingsStore
import nz.eloque.openmaps.map.MapController
import nz.eloque.openmaps.ui.components.LineChip
import nz.eloque.openmaps.ui.components.LoadState
import nz.eloque.openmaps.ui.components.LoadStateContent
import nz.eloque.openmaps.ui.components.clockTime
import nz.eloque.openmaps.ui.components.formatDuration
import nz.eloque.openmaps.ui.components.icon
import nz.eloque.openmaps.ui.navigation.DirectionsField
import nz.eloque.openmaps.ui.navigation.DirectionsKey
import nz.eloque.openmaps.ui.navigation.Endpoint
import nz.eloque.openmaps.ui.navigation.toRouteEnd
import nz.eloque.openmaps.ui.resources.Res
import nz.eloque.openmaps.ui.resources.allow_location
import nz.eloque.openmaps.ui.resources.departs_from
import nz.eloque.openmaps.ui.resources.earlier
import nz.eloque.openmaps.ui.resources.later
import nz.eloque.openmaps.ui.resources.needs_location_for_route
import nz.eloque.openmaps.ui.resources.no_routes
import nz.eloque.openmaps.ui.resources.no_transfers
import nz.eloque.openmaps.ui.resources.one_transfer
import nz.eloque.openmaps.ui.resources.platform
import nz.eloque.openmaps.ui.resources.remove_saved_route
import nz.eloque.openmaps.ui.resources.route_options
import nz.eloque.openmaps.ui.resources.save_route
import nz.eloque.openmaps.ui.resources.swap
import nz.eloque.openmaps.ui.resources.transfers
import nz.eloque.openmaps.ui.resources.walk_only
import nz.eloque.openmaps.ui.resources.your_location
import nz.eloque.openmaps.ui.settings.RouteOptionsEditor
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DirectionsSheet(
    directions: DirectionsKey,
    map: MapController,
    onEditField: (DirectionsField) -> Unit,
    onSwap: () -> Unit,
    onOpenRoute: (Itinerary) -> Unit,
) {
    val viewModel = koinViewModel<DirectionsViewModel> { parametersOf(directions) }
    val result by viewModel.result.collectAsStateWithLifecycle()
    val time by viewModel.time.collectAsStateWithLifecycle()
    val userLocation = map.userLocation
    LaunchedEffect(userLocation) { userLocation?.let(viewModel::setUserLocation) }

    val loaded = (result as? LoadState.Loaded)?.value
    ShowItineraryOnMap(map, loaded?.itineraries?.firstOrNull() ?: loaded?.direct)

    val settings = koinInject<SettingsStore>()
    val options by settings.routeOptions.collectAsStateWithLifecycle()
    var editingOptions by rememberSaveable { mutableStateOf(false) }
    if (editingOptions) {
        ModalBottomSheet(onDismissRequest = { editingOptions = false }) {
            RouteOptionsEditor(options, settings::setRouteOptions, Modifier.padding(bottom = 24.dp))
        }
    }

    LazyColumn(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            EndpointsCard(directions.from, directions.to, onEditField, onSwap)
            SaveRouteButton(directions)
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                TimeChoiceRow(time, viewModel::setTime)
                FilterChip(
                    selected = !options.isDefault,
                    onClick = { editingOptions = true },
                    label = { Text(stringResource(Res.string.route_options)) },
                    leadingIcon = { Icon(Icons.Default.Tune, contentDescription = null, modifier = Modifier.size(18.dp)) },
                )
            }
        }
        when (val state = result) {
            null ->
                item {
                    if (!map.isLocationPermissionGranted) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(stringResource(Res.string.needs_location_for_route), color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Button(onClick = map::requestLocationPermission) { Text(stringResource(Res.string.allow_location)) }
                        }
                    } else {
                        LoadStateContent(LoadState.Loading, onRetry = {}) {}
                    }
                }
            is LoadState.Loaded -> {
                val value = state.value
                if (value.itineraries.isEmpty() && value.direct == null) {
                    item { Text(stringResource(Res.string.no_routes), color = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
                value.direct?.let { direct -> item { DirectCard(direct, onClick = { onOpenRoute(direct) }) } }
                if (value.earlierCursor != null && value.itineraries.isNotEmpty()) {
                    item { TextButton(onClick = viewModel::loadEarlier) { Text(stringResource(Res.string.earlier)) } }
                }
                items(value.itineraries, key = { it.id }) { ItineraryCard(it, onClick = { onOpenRoute(it) }) }
                if (value.laterCursor != null && value.itineraries.isNotEmpty()) {
                    item { TextButton(onClick = viewModel::loadLater) { Text(stringResource(Res.string.later)) } }
                }
            }
            else -> item { LoadStateContent(state, onRetry = viewModel::plan) {} }
        }
    }
}

@Composable
private fun EndpointsCard(
    from: Endpoint?,
    to: Endpoint?,
    onEditField: (DirectionsField) -> Unit,
    onSwap: () -> Unit,
) {
    OutlinedCard(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                EndpointRow(from, Icons.Default.TripOrigin) { onEditField(DirectionsField.FROM) }
                HorizontalDivider(Modifier.padding(start = 48.dp))
                EndpointRow(to, Icons.Default.Place) { onEditField(DirectionsField.TO) }
            }
            IconButton(onClick = onSwap) {
                Icon(Icons.Default.SwapVert, contentDescription = stringResource(Res.string.swap))
            }
        }
    }
}

@Composable
private fun EndpointRow(
    endpoint: Endpoint?,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            if (endpoint == null) Icons.Default.MyLocation else icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(20.dp),
        )
        Text(
            endpoint?.name ?: stringResource(Res.string.your_location),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(start = 16.dp),
        )
    }
}

@Composable
private fun ItineraryCard(
    itinerary: Itinerary,
    onClick: () -> Unit,
) {
    val firstTransit = itinerary.legs.filterIsInstance<Leg.Transit>().firstOrNull()
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "${clockTime(itinerary.start)} – ${clockTime(itinerary.end)}",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                Text(formatDuration(itinerary.duration), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
            LegSummary(itinerary.legs)
            firstTransit?.let { leg ->
                val departure = leg.start
                val delayed = departure.isRealTime && departure.delay.inWholeMinutes >= 1
                val stop = leg.from.platform?.let { "${leg.from.name} · ${stringResource(Res.string.platform, it)}" } ?: leg.from.name
                Text(
                    stringResource(Res.string.departs_from, clockTime(departure.actual), stop),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (delayed || leg.isCancelled) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                when (itinerary.transfers) {
                    0 -> stringResource(Res.string.no_transfers)
                    1 -> stringResource(Res.string.one_transfer)
                    else -> stringResource(Res.string.transfers, itinerary.transfers)
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun DirectCard(
    itinerary: Itinerary,
    onClick: () -> Unit,
) {
    val mode = itinerary.legs.firstOrNull()?.mode
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            mode?.let { Icon(it.icon, contentDescription = null) }
            Text(stringResource(Res.string.walk_only), modifier = Modifier.weight(1f).padding(start = 12.dp))
            Text(formatDuration(itinerary.duration), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun LegSummary(legs: List<Leg>) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
        itemVerticalAlignment = Alignment.CenterVertically,
    ) {
        legs.forEachIndexed { index, leg ->
            if (index > 0) {
                Icon(
                    Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.size(16.dp),
                )
            }
            when (leg) {
                is Leg.Transit -> LineChip(leg.line, minWidth = 0.dp)
                is Leg.Street ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(leg.mode.icon, contentDescription = null, modifier = Modifier.size(20.dp))
                        Text(
                            ((leg.end.actual - leg.start.actual).inWholeMinutes).coerceAtLeast(1).toString(),
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }
            }
        }
    }
}

/** Saves the start and destination to plan again later. When already saved, a tap removes it. */
@Composable
private fun SaveRouteButton(directions: DirectionsKey) {
    val store = koinInject<PlacesStore>()
    val routes by store.savedRoutes.collectAsStateWithLifecycle(emptyList())
    val scope = rememberCoroutineScope()
    val from = directions.from?.toRouteEnd()
    val to = directions.to?.toRouteEnd()
    val saved = routes.firstOrNull { it.from == from && it.to == to }
    TextButton(
        onClick = {
            scope.launch { if (saved != null) store.removeRoute(saved.id) else store.saveRoute(from, to) }
        },
    ) {
        Icon(if (saved != null) Icons.Default.Bookmark else Icons.Outlined.BookmarkBorder, contentDescription = null)
        Text(
            stringResource(if (saved != null) Res.string.remove_saved_route else Res.string.save_route),
            modifier = Modifier.padding(start = 8.dp),
        )
    }
}
