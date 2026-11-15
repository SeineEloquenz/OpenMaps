package nz.eloque.openmaps.ui.directions

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material.icons.filled.DirectionsTransit
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
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import nz.eloque.openmaps.core.model.DistanceUnit
import nz.eloque.openmaps.core.model.Itinerary
import nz.eloque.openmaps.core.model.Leg
import nz.eloque.openmaps.core.model.PlacesStore
import nz.eloque.openmaps.core.model.SettingsStore
import nz.eloque.openmaps.core.model.TransportMode
import nz.eloque.openmaps.core.model.TravelMode
import nz.eloque.openmaps.map.MapController
import nz.eloque.openmaps.ui.components.LineChip
import nz.eloque.openmaps.ui.components.LoadState
import nz.eloque.openmaps.ui.components.LoadStateContent
import nz.eloque.openmaps.ui.components.clockTime
import nz.eloque.openmaps.ui.components.formatDistance
import nz.eloque.openmaps.ui.components.formatDuration
import nz.eloque.openmaps.ui.components.icon
import nz.eloque.openmaps.ui.components.timeRange
import nz.eloque.openmaps.ui.navigation.DirectionsField
import nz.eloque.openmaps.ui.navigation.DirectionsKey
import nz.eloque.openmaps.ui.navigation.Endpoint
import nz.eloque.openmaps.ui.navigation.toRouteEnd
import nz.eloque.openmaps.ui.resources.Res
import nz.eloque.openmaps.ui.resources.allow_location
import nz.eloque.openmaps.ui.resources.arrive_at
import nz.eloque.openmaps.ui.resources.departs_from
import nz.eloque.openmaps.ui.resources.earlier
import nz.eloque.openmaps.ui.resources.later
import nz.eloque.openmaps.ui.resources.mode_bike
import nz.eloque.openmaps.ui.resources.mode_car
import nz.eloque.openmaps.ui.resources.mode_transit
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
import nz.eloque.openmaps.ui.resources.via_street
import nz.eloque.openmaps.ui.resources.via_streets
import nz.eloque.openmaps.ui.resources.walk_only
import nz.eloque.openmaps.ui.resources.without_traffic
import nz.eloque.openmaps.ui.resources.your_location
import nz.eloque.openmaps.ui.settings.RouteOptionsEditor
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf
import kotlin.time.Duration

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
    val street by viewModel.street.collectAsStateWithLifecycle()
    val mode by viewModel.mode.collectAsStateWithLifecycle()
    val time by viewModel.time.collectAsStateWithLifecycle()
    val userLocation = map.userLocation
    LaunchedEffect(userLocation) { userLocation?.let(viewModel::setUserLocation) }

    val loaded = (result as? LoadState.Loaded)?.value
    val direct = (street as? LoadState.Loaded)?.value.orEmpty()
    val walk = loaded?.direct ?: direct[TransportMode.WALK]
    val routes =
        mapOf(
            TravelMode.CAR to direct[TransportMode.CAR],
            TravelMode.WALK to walk,
            TravelMode.BIKE to direct[TransportMode.BIKE],
        )
    var selectedId by rememberSaveable { mutableStateOf<String?>(null) }
    val selected = loaded?.itineraries?.let { list -> list.firstOrNull { it.id == selectedId } ?: list.firstOrNull() }
    if (mode == TravelMode.TRANSIT) {
        ShowItineraryOnMap(map, selected, fit = false)
        ShowRouteChoicesOnMap(map, loaded?.itineraries.orEmpty(), selected) { selectedId = it.id }
    } else {
        ShowItineraryOnMap(map, routes[mode])
    }

    val settings = koinInject<SettingsStore>()
    val options by settings.routeOptions.collectAsStateWithLifecycle()
    val unit by settings.distanceUnit.collectAsStateWithLifecycle()
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
            ModeTabs(
                selected = mode,
                durations =
                    routes.mapValues { it.value?.duration } +
                        (TravelMode.TRANSIT to loaded?.itineraries?.minOfOrNull { it.duration }),
                onSelect = viewModel::selectMode,
            )
        }
        if (mode == TravelMode.TRANSIT) {
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
        }
        val state = if (mode == TravelMode.TRANSIT || (mode == TravelMode.WALK && walk != null && street == null)) result else street
        when {
            viewModel.needsUserLocation && !map.isLocationPermissionGranted ->
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(stringResource(Res.string.needs_location_for_route), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Button(onClick = map::requestLocationPermission) { Text(stringResource(Res.string.allow_location)) }
                    }
                }
            state == null || state == LoadState.Loading -> item { LoadStateContent(LoadState.Loading, onRetry = {}) {} }
            state == LoadState.Failed -> item { LoadStateContent(LoadState.Failed, onRetry = viewModel::retry) {} }
            mode == TravelMode.TRANSIT && loaded != null -> {
                if (loaded.itineraries.isEmpty()) {
                    item { Text(stringResource(Res.string.no_routes), color = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
                if (loaded.earlierCursor != null && loaded.itineraries.isNotEmpty()) {
                    item { TextButton(onClick = viewModel::loadEarlier) { Text(stringResource(Res.string.earlier)) } }
                }
                items(loaded.itineraries, key = { it.id }) {
                    ItineraryCard(it, selected = it.id == selected?.id, onClick = { onOpenRoute(it) })
                }
                if (loaded.laterCursor != null && loaded.itineraries.isNotEmpty()) {
                    item { TextButton(onClick = viewModel::loadLater) { Text(stringResource(Res.string.later)) } }
                }
            }
            else ->
                item {
                    val route = routes[mode]
                    if (route == null) {
                        Text(stringResource(Res.string.no_routes), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else {
                        StreetRouteCard(route, mode, unit, onClick = { onOpenRoute(route) })
                    }
                }
        }
    }
}

@Composable
private fun ModeTabs(
    selected: TravelMode,
    durations: Map<TravelMode, Duration?>,
    onSelect: (TravelMode) -> Unit,
) {
    PrimaryTabRow(selectedTabIndex = TravelMode.entries.indexOf(selected), containerColor = Color.Transparent) {
        TravelMode.entries.forEach { mode ->
            Tab(
                selected = mode == selected,
                onClick = { onSelect(mode) },
                icon = { Icon(mode.icon, contentDescription = stringResource(mode.label)) },
                text = { Text(durations[mode]?.let { formatDuration(it) } ?: "", maxLines = 1) },
            )
        }
    }
}

@Composable
private fun StreetRouteCard(
    itinerary: Itinerary,
    mode: TravelMode,
    unit: DistanceUnit,
    onClick: () -> Unit,
) {
    val legs = itinerary.legs.filterIsInstance<Leg.Street>()
    val meters = legs.mapNotNull { it.distanceMeters }.takeIf { it.isNotEmpty() }?.sum()
    val streets = legs.flatMap { it.streets }.distinct().take(2)
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    formatDuration(itinerary.duration),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                )
                meters?.let { Text(formatDistance(it, unit), style = MaterialTheme.typography.titleMedium) }
            }
            if (streets.isNotEmpty()) {
                Text(
                    if (streets.size == 1) {
                        stringResource(Res.string.via_street, streets[0])
                    } else {
                        stringResource(Res.string.via_streets, streets[0], streets[1])
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                stringResource(if (mode == TravelMode.CAR) Res.string.without_traffic else Res.string.arrive_at, clockTime(itinerary.end)),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private val TravelMode.icon: ImageVector
    get() =
        when (this) {
            TravelMode.TRANSIT -> Icons.Default.DirectionsTransit
            TravelMode.CAR -> TransportMode.CAR.icon
            TravelMode.WALK -> TransportMode.WALK.icon
            TravelMode.BIKE -> TransportMode.BIKE.icon
        }

private val TravelMode.label: StringResource
    get() =
        when (this) {
            TravelMode.TRANSIT -> Res.string.mode_transit
            TravelMode.CAR -> Res.string.mode_car
            TravelMode.WALK -> Res.string.walk_only
            TravelMode.BIKE -> Res.string.mode_bike
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
    selected: Boolean,
    onClick: () -> Unit,
) {
    val firstTransit = itinerary.legs.filterIsInstance<Leg.Transit>().firstOrNull()
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        border = if (selected) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    timeRange(itinerary.start, itinerary.end),
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
