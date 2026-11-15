package nz.eloque.openmaps.ui.sharing

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import nz.eloque.openmaps.core.model.BookingLinks
import nz.eloque.openmaps.core.model.GeoBounds
import nz.eloque.openmaps.core.model.LatLng
import nz.eloque.openmaps.core.model.SettingsStore
import nz.eloque.openmaps.core.model.SharingSnapshot
import nz.eloque.openmaps.core.model.VehicleKind
import nz.eloque.openmaps.map.MapController
import nz.eloque.openmaps.map.SharingPin
import nz.eloque.openmaps.ui.components.BookButton
import nz.eloque.openmaps.ui.components.LoadState
import nz.eloque.openmaps.ui.components.LoadStateContent
import nz.eloque.openmaps.ui.components.formatDistance
import nz.eloque.openmaps.ui.components.icon
import nz.eloque.openmaps.ui.components.label
import nz.eloque.openmaps.ui.resources.Res
import nz.eloque.openmaps.ui.resources.category_shared_vehicles
import nz.eloque.openmaps.ui.resources.search_this_area
import nz.eloque.openmaps.ui.resources.sharing_available
import nz.eloque.openmaps.ui.resources.sharing_bikes
import nz.eloque.openmaps.ui.resources.sharing_cars
import nz.eloque.openmaps.ui.resources.sharing_free_docks
import nz.eloque.openmaps.ui.resources.sharing_none
import nz.eloque.openmaps.ui.resources.sharing_scooters
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import kotlin.math.abs

/** A station or a freely parked vehicle, as one row of the list. */
private data class SharingEntry(
    val id: String,
    val location: LatLng,
    val operator: String,
    val kind: VehicleKind,
    val electric: Boolean,
    val stationName: String?,
    val available: Int?,
    val freeDocks: Int?,
    val booking: BookingLinks,
)

private fun SharingSnapshot.entries(): List<SharingEntry> =
    stations.map { station ->
        SharingEntry(
            id = station.id,
            location = station.location,
            operator = station.operator,
            kind = station.kinds.firstOrNull() ?: VehicleKind.BICYCLE,
            electric = false,
            stationName = station.name,
            available = station.available,
            freeDocks = station.freeDocks,
            booking = station.booking,
        )
    } +
        vehicles.map { vehicle ->
            SharingEntry(
                id = vehicle.id,
                location = vehicle.location,
                operator = vehicle.operator,
                kind = vehicle.kind,
                electric = vehicle.electric,
                stationName = null,
                available = null,
                freeDocks = null,
                booking = vehicle.booking,
            )
        }

private val SharingFilter.kinds: Set<VehicleKind>
    get() =
        when (this) {
            SharingFilter.BIKES -> setOf(VehicleKind.BICYCLE, VehicleKind.CARGO_BICYCLE, VehicleKind.OTHER)
            SharingFilter.SCOOTERS -> setOf(VehicleKind.SCOOTER, VehicleKind.MOPED)
            SharingFilter.CARS -> setOf(VehicleKind.CAR)
        }

/** Shared bikes, scooters and cars around the map center, with their stations, also drawn on the map. */
@Composable
fun SharingSheet(map: MapController) {
    val viewModel = koinViewModel<SharingViewModel>()
    val snapshot by viewModel.snapshot.collectAsStateWithLifecycle()
    val filters by viewModel.filters.collectAsStateWithLifecycle()
    val searchedArea by viewModel.searchedArea.collectAsStateWithLifecycle()
    val unit by koinInject<SettingsStore>().distanceUnit.collectAsState()
    var selected by rememberSaveable { mutableStateOf<String?>(null) }

    val origin = map.userLocation ?: map.center
    val kinds = filters.flatMap { it.kinds }.toSet()
    val all =
        (snapshot as? LoadState.Loaded)
            ?.value
            ?.entries()
            .orEmpty()
            .filter { it.kind in kinds }
            .sortedBy { it.location.distanceTo(origin) }
    val listed = (all.filter { it.id == selected } + all.filterNot { it.id == selected }).take(MAX_LISTED)

    LaunchedEffect(viewModel) {
        if (viewModel.searchedArea.value != null) return@LaunchedEffect
        val bounds = snapshotFlow { map.visibleBounds }.filterNotNull().first()
        viewModel.load(map.center, bounds)
    }
    DisposableEffect(all) {
        map.sharing =
            all.map { SharingPin(it.id, it.location, it.kind, it.electric, station = it.stationName != null, count = it.available) }
        map.onSharingClick = { selected = it }
        onDispose {
            map.sharing = emptyList()
            map.onSharingClick = null
        }
    }
    LaunchedEffect(all.isNotEmpty()) {
        if (all.isEmpty() || viewModel.fitted) return@LaunchedEffect
        viewModel.fitted = true
        map.fit(all.take(FIT_COUNT).map { it.location } + origin)
        map.visibleBounds?.let(viewModel::showing)
    }

    val visible = map.visibleBounds
    val moved = !map.isMoving && visible != null && searchedArea?.let { visible.differsFrom(it) } == true

    LazyColumn(Modifier.fillMaxWidth()) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                Text(
                    stringResource(Res.string.category_shared_vehicles),
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.weight(1f),
                )
                if (moved) {
                    FilledTonalButton(onClick = { map.visibleBounds?.let { viewModel.load(map.center, it) } }) {
                        Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                        Text(stringResource(Res.string.search_this_area), modifier = Modifier.padding(start = 8.dp))
                    }
                }
            }
        }
        item {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 4.dp),
            ) {
                SharingFilter.entries.forEach { filter ->
                    FilterChip(
                        selected = filter in filters,
                        onClick = { viewModel.toggle(filter) },
                        label = { Text(stringResource(filter.label)) },
                    )
                }
            }
        }
        item {
            LoadStateContent(snapshot, onRetry = { viewModel.retry(map.center) }, modifier = Modifier.fillMaxWidth()) {
                if (all.isEmpty()) {
                    Text(
                        stringResource(Res.string.sharing_none),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(16.dp),
                    )
                }
            }
        }
        items(listed, key = { it.id }) { entry ->
            SharingRow(entry, formatDistance(entry.location.distanceTo(origin), unit), highlighted = entry.id == selected)
            HorizontalDivider(Modifier.padding(horizontal = 16.dp))
        }
    }
}

@Composable
private fun SharingRow(
    entry: SharingEntry,
    distance: String,
    highlighted: Boolean,
) {
    val kind = stringResource(entry.kind.label(entry.electric))
    val counts =
        listOfNotNull(
            entry.available?.let { stringResource(Res.string.sharing_available, it) },
            entry.freeDocks?.let { stringResource(Res.string.sharing_free_docks, it) },
        )
    Row(
        Modifier
            .fillMaxWidth()
            .background(if (highlighted) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Box(
            Modifier.size(40.dp).background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                entry.kind.icon(entry.electric),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(22.dp),
            )
        }
        Column(Modifier.padding(start = 16.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(entry.stationName ?: kind, style = MaterialTheme.typography.titleMedium)
            Text(
                (listOf(entry.operator, distance) + counts).joinToString(" · "),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            BookButton(entry.operator, entry.booking, Modifier.padding(top = 6.dp))
        }
    }
}

private val SharingFilter.label
    get() =
        when (this) {
            SharingFilter.BIKES -> Res.string.sharing_bikes
            SharingFilter.SCOOTERS -> Res.string.sharing_scooters
            SharingFilter.CARS -> Res.string.sharing_cars
        }

/** True once the view has panned or zoomed by more than a fifth of [other]'s size. */
private fun GeoBounds.differsFrom(other: GeoBounds): Boolean {
    val height = other.north - other.south
    val width = other.east - other.west
    return abs((north + south) - (other.north + other.south)) / 2 > height * CHANGE ||
        abs((east + west) - (other.east + other.west)) / 2 > width * CHANGE ||
        abs((north - south) - height) > height * CHANGE
}

private const val CHANGE = 0.2
private const val FIT_COUNT = 8
private const val MAX_LISTED = 40
