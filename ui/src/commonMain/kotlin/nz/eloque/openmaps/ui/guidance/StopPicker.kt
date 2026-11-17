package nz.eloque.openmaps.ui.guidance

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.launch
import nz.eloque.openmaps.core.model.DistanceUnit
import nz.eloque.openmaps.core.model.NavigationState
import nz.eloque.openmaps.core.model.NearbyPlace
import nz.eloque.openmaps.core.model.NearbyPlaces
import nz.eloque.openmaps.core.model.NearbyResults
import nz.eloque.openmaps.core.model.PlaceCategory
import nz.eloque.openmaps.core.model.SettingsStore
import nz.eloque.openmaps.core.model.TransportMode
import nz.eloque.openmaps.ui.category.icon
import nz.eloque.openmaps.ui.category.label
import nz.eloque.openmaps.ui.components.formatDistance
import nz.eloque.openmaps.ui.components.formatPrice
import nz.eloque.openmaps.ui.resources.Res
import nz.eloque.openmaps.ui.resources.stop_ahead
import nz.eloque.openmaps.ui.resources.stop_failed
import nz.eloque.openmaps.ui.resources.stop_none
import nz.eloque.openmaps.ui.resources.stop_picker_title
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject

private val StopCategories =
    listOf(
        PlaceCategory.GAS_STATIONS,
        PlaceCategory.CHARGING,
        PlaceCategory.PARKING,
        PlaceCategory.RESTAURANTS,
        PlaceCategory.CAFES,
        PlaceCategory.TOILETS,
        PlaceCategory.SUPERMARKETS,
    )

/** Finds places of a category along the route ahead of [trip], and adds the picked one as the next stop. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun StopPicker(
    trip: NavigationState,
    unit: DistanceUnit,
    onAdd: suspend (NearbyPlace) -> Boolean,
    onDismiss: () -> Unit,
) {
    val nearby = koinInject<NearbyPlaces>()
    val preferredFuel by koinInject<SettingsStore>().preferredFuel.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var category by rememberSaveable { mutableStateOf(StopCategories.first()) }
    var failed by remember { mutableStateOf(false) }
    var adding by remember { mutableStateOf(false) }
    val corridor = if (trip.mode == TransportMode.CAR) CAR_CORRIDOR_METERS else CORRIDOR_METERS
    // Fixed when the picker opens, so results do not reshuffle while the trip moves on.
    val ahead = remember { firstMeters(split(trip.route, trip.travelledMeters).second, SEARCH_AHEAD_METERS) }
    val results by produceState<List<AlongRoute<NearbyPlace>>?>(null, category) {
        value = null
        if (ahead.size < 2) {
            value = emptyList()
            return@produceState
        }
        // Searches return the places nearest their center, so one search over the whole stretch would only find those close by.
        val found = arrayOfNulls<List<NearbyPlace>>(SEARCH_SECTIONS)
        sections(ahead, SEARCH_SECTIONS)
            .mapIndexed { index, section ->
                nearby
                    .search(category, section.first(), boundsAround(section, corridor), loaded = null)
                    .catch { emit(NearbyResults(emptyList())) }
                    .map { index to it.places }
            }.merge()
            .collect { (index, places) ->
                found[index] = places
                val all = found.filterNotNull().flatten().distinctBy { it.place.location }
                value = alongRoute(ahead, all, corridor) { it.place.location }
            }
    }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Text(
            stringResource(Res.string.stop_picker_title),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
        LazyRow(
            contentPadding = PaddingValues(16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(StopCategories) { option ->
                FilterChip(
                    selected = option == category,
                    onClick = { category = option },
                    label = { Text(stringResource(option.label)) },
                    leadingIcon = { Icon(option.icon, contentDescription = null, modifier = Modifier.size(18.dp)) },
                )
            }
        }
        if (failed) {
            Text(
                stringResource(Res.string.stop_failed),
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
        }
        val found = results
        when {
            found == null || adding ->
                Row(Modifier.fillMaxWidth().padding(24.dp), horizontalArrangement = Arrangement.Center) { CircularProgressIndicator() }
            found.isEmpty() ->
                Text(
                    stringResource(Res.string.stop_none, formatDistance(SEARCH_AHEAD_METERS, unit)),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp),
                )
            else ->
                LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
                    items(found, key = { it.item.place.location.toString() + it.item.place.name }) { result ->
                        val details = result.item.details
                        val price =
                            details?.fuelPrices?.let { prices -> prices.firstOrNull { it.fuel == preferredFuel } ?: prices.firstOrNull() }
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .clickable(enabled = !adding) {
                                    adding = true
                                    failed = false
                                    scope.launch {
                                        if (onAdd(result.item)) onDismiss() else failed = true
                                        adding = false
                                    }
                                }.padding(horizontal = 16.dp, vertical = 12.dp),
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    result.item.place.name.ifBlank { stringResource(category.label) },
                                    style = MaterialTheme.typography.titleMedium,
                                    modifier = Modifier.weight(1f),
                                )
                                price?.let {
                                    Text(
                                        "${it.fuel.label} ${formatPrice(it.price, it.currency)}",
                                        style = MaterialTheme.typography.titleSmall,
                                        color = MaterialTheme.colorScheme.primary,
                                    )
                                }
                            }
                            Text(
                                stringResource(
                                    Res.string.stop_ahead,
                                    formatDistance(result.ahead, unit),
                                    formatDistance(result.offRoute, unit),
                                ),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        HorizontalDivider()
                    }
                }
        }
    }
}

private const val SEARCH_AHEAD_METERS = 30_000.0
private const val SEARCH_SECTIONS = 3
private const val CAR_CORRIDOR_METERS = 1_000.0
private const val CORRIDOR_METERS = 300.0
