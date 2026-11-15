package nz.eloque.openmaps.ui.category

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Accessible
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Deck
import androidx.compose.material.icons.filled.Directions
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.TakeoutDining
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import nz.eloque.openmaps.core.model.GeoBounds
import nz.eloque.openmaps.core.model.OpenStatus
import nz.eloque.openmaps.core.model.OpeningHours
import nz.eloque.openmaps.core.model.PlaceDetails
import nz.eloque.openmaps.core.model.PlaceFeature
import nz.eloque.openmaps.core.model.SearchResult
import nz.eloque.openmaps.core.model.SettingsStore
import nz.eloque.openmaps.map.MapController
import nz.eloque.openmaps.ui.components.LoadState
import nz.eloque.openmaps.ui.components.LoadStateContent
import nz.eloque.openmaps.ui.components.formatDistance
import nz.eloque.openmaps.ui.components.rememberNow
import nz.eloque.openmaps.ui.navigation.CategoryKey
import nz.eloque.openmaps.ui.place.OpenStatusText
import nz.eloque.openmaps.ui.place.telUri
import nz.eloque.openmaps.ui.place.toCategoryLabel
import nz.eloque.openmaps.ui.resources.Res
import nz.eloque.openmaps.ui.resources.call
import nz.eloque.openmaps.ui.resources.category_none
import nz.eloque.openmaps.ui.resources.cuisine
import nz.eloque.openmaps.ui.resources.directions
import nz.eloque.openmaps.ui.resources.filter_any_cuisine
import nz.eloque.openmaps.ui.resources.filter_open_now
import nz.eloque.openmaps.ui.resources.filter_wheelchair
import nz.eloque.openmaps.ui.resources.search_this_area
import nz.eloque.openmaps.ui.resources.website
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf
import kotlin.math.abs
import kotlin.time.Instant

private const val FIT_COUNT = 10

/** True once the view has panned or zoomed by more than a fifth of [other]'s size. */
private fun GeoBounds.differsFrom(other: GeoBounds): Boolean {
    val height = other.north - other.south
    val width = other.east - other.west
    return abs((north + south) - (other.north + other.south)) / 2 > height * CHANGE ||
        abs((east + west) - (other.east + other.west)) / 2 > width * CHANGE ||
        abs((north - south) - height) > height * CHANGE
}

private const val CHANGE = 0.2

/** Places of one category in the visible map area, also drawn on the map. */
@Composable
fun CategorySheet(
    key: CategoryKey,
    map: MapController,
    onOpenPlace: (SearchResult.Place) -> Unit,
    onDirections: (SearchResult.Place) -> Unit,
) {
    val viewModel = koinViewModel<CategoryViewModel> { parametersOf(key.category) }
    val results by viewModel.results.collectAsStateWithLifecycle()
    val details by viewModel.details.collectAsStateWithLifecycle()
    val filters by viewModel.filters.collectAsStateWithLifecycle()
    val searchedArea by viewModel.searchedArea.collectAsStateWithLifecycle()
    val unit by koinInject<SettingsStore>().distanceUnit.collectAsState()
    val now by rememberNow()
    val all = (results as? LoadState.Loaded)?.value.orEmpty()
    val shown = all.filter { filters.allows(it.osmId?.let(details::get), now) }

    LaunchedEffect(viewModel) {
        if (viewModel.searchedArea.value != null) return@LaunchedEffect
        val bounds = snapshotFlow { map.visibleBounds }.filterNotNull().first()
        viewModel.search(map.center, bounds)
    }
    LaunchedEffect(shown) { map.results = shown }
    LaunchedEffect(results) {
        val places = (results as? LoadState.Loaded)?.value.orEmpty()
        if (places.isNotEmpty() && !viewModel.fitted) {
            viewModel.fitted = true
            val nearest = places.take(FIT_COUNT).map { it.location }
            if (nearest.size == 1) map.show(nearest.single()) else map.fit(nearest)
            map.visibleBounds?.let(viewModel::showing)
        }
    }

    val visible = map.visibleBounds
    val moved = !map.isMoving && visible != null && searchedArea?.let { visible.differsFrom(it) } == true
    val origin = map.userLocation ?: map.center

    LazyColumn(Modifier.fillMaxWidth()) {
        item {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            ) {
                Text(
                    stringResource(key.category.label),
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.weight(1f),
                )
                if (moved) {
                    FilledTonalButton(onClick = { map.visibleBounds?.let { viewModel.search(map.center, it) } }) {
                        Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                        Text(stringResource(Res.string.search_this_area), modifier = Modifier.padding(start = 8.dp))
                    }
                }
            }
        }
        item {
            FilterRow(filters, cuisines = details.values.flatMap { it.cuisine }.distinct().sorted(), onChange = viewModel::setFilters)
        }
        item {
            LoadStateContent(results, onRetry = viewModel::retry, modifier = Modifier.fillMaxWidth()) {
                if (shown.isEmpty()) {
                    Text(
                        stringResource(Res.string.category_none),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(16.dp),
                    )
                }
            }
        }
        items(shown, key = { "${it.osmId ?: it.name}-${it.location}" }) { place ->
            PlaceResult(
                place = place,
                details = place.osmId?.let(details::get),
                distance = formatDistance(place.location.distanceTo(origin), unit),
                onOpen = { onOpenPlace(place) },
                onDirections = { onDirections(place) },
            )
            HorizontalDivider(Modifier.padding(horizontal = 16.dp))
        }
    }
}

private fun CategoryFilters.allows(
    details: PlaceDetails?,
    now: Instant,
): Boolean {
    if (openNow) {
        val hours = details?.openingHours?.let(OpeningHours::parse) ?: return false
        val local = now.toLocalDateTime(TimeZone.currentSystemDefault())
        if (hours.status(local.dayOfWeek.ordinal, local.hour * 60 + local.minute) !is OpenStatus.Open) return false
    }
    if (cuisine != null && details?.cuisine?.contains(cuisine) != true) return false
    if (wheelchair && details?.wheelchair != "yes") return false
    return true
}

@Composable
private fun FilterRow(
    filters: CategoryFilters,
    cuisines: List<String>,
    onChange: (CategoryFilters) -> Unit,
) {
    var choosingCuisine by remember { mutableStateOf(false) }
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 4.dp),
    ) {
        FilterChip(
            selected = filters.openNow,
            onClick = { onChange(filters.copy(openNow = !filters.openNow)) },
            label = { Text(stringResource(Res.string.filter_open_now)) },
        )
        if (cuisines.isNotEmpty() || filters.cuisine != null) {
            Box {
                FilterChip(
                    selected = filters.cuisine != null,
                    onClick = { choosingCuisine = true },
                    label = { Text(filters.cuisine?.toCategoryLabel() ?: stringResource(Res.string.cuisine)) },
                    trailingIcon = { Icon(Icons.Default.ArrowDropDown, contentDescription = null, modifier = Modifier.size(18.dp)) },
                )
                DropdownMenu(expanded = choosingCuisine, onDismissRequest = { choosingCuisine = false }) {
                    DropdownMenuItem(
                        text = { Text(stringResource(Res.string.filter_any_cuisine)) },
                        onClick = {
                            onChange(filters.copy(cuisine = null))
                            choosingCuisine = false
                        },
                    )
                    cuisines.forEach { cuisine ->
                        DropdownMenuItem(
                            text = { Text(cuisine.toCategoryLabel()) },
                            onClick = {
                                onChange(filters.copy(cuisine = cuisine))
                                choosingCuisine = false
                            },
                        )
                    }
                }
            }
        }
        FilterChip(
            selected = filters.wheelchair,
            onClick = { onChange(filters.copy(wheelchair = !filters.wheelchair)) },
            label = { Text(stringResource(Res.string.filter_wheelchair)) },
            leadingIcon = { Icon(Icons.AutoMirrored.Filled.Accessible, contentDescription = null, modifier = Modifier.size(18.dp)) },
        )
    }
}

@Composable
private fun PlaceResult(
    place: SearchResult.Place,
    details: PlaceDetails?,
    distance: String,
    onOpen: () -> Unit,
    onDirections: () -> Unit,
) {
    val uriHandler = LocalUriHandler.current
    val kind =
        details?.cuisine?.take(2)?.joinToString(", ") { it.toCategoryLabel() }?.takeIf { it.isNotEmpty() }
            ?: place.category?.toCategoryLabel()
    Column(
        Modifier.fillMaxWidth().clickable(onClick = onOpen).padding(vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(place.name, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(horizontal = 16.dp))
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 16.dp)) {
            Text(
                listOfNotNull(kind, distance).joinToString(" · "),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            FeatureIcons(details)
        }
        details?.openingHours?.let(OpeningHours::parse)?.let { OpenStatusText(it, Modifier.padding(horizontal = 16.dp)) }
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(top = 6.dp),
        ) {
            ResultAction(Icons.Default.Directions, Res.string.directions, primary = true, onClick = onDirections)
            details?.phone?.let { phone -> ResultAction(Icons.Default.Call, Res.string.call) { uriHandler.openUri(phone.telUri()) } }
            details?.website?.let { url -> ResultAction(Icons.Default.Language, Res.string.website) { uriHandler.openUri(url) } }
        }
    }
}

@Composable
private fun FeatureIcons(details: PlaceDetails?) {
    val icons =
        listOfNotNull(
            Icons.AutoMirrored.Filled.Accessible.takeIf { details?.wheelchair == "yes" },
            Icons.Default.Deck.takeIf { details?.features?.contains(PlaceFeature.OUTDOOR_SEATING) == true },
            Icons.Default.TakeoutDining.takeIf { details?.features?.contains(PlaceFeature.TAKEAWAY) == true },
        )
    icons.forEach { icon ->
        Icon(
            icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 6.dp).size(16.dp),
        )
    }
}

@Composable
private fun ResultAction(
    icon: ImageVector,
    label: StringResource,
    primary: Boolean = false,
    onClick: () -> Unit,
) {
    val content: @Composable () -> Unit = {
        Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
        Text(stringResource(label), modifier = Modifier.padding(start = 6.dp))
    }
    val padding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
    if (primary) {
        Button(onClick = onClick, contentPadding = padding, modifier = Modifier.height(36.dp)) { content() }
    } else {
        OutlinedButton(onClick = onClick, contentPadding = padding, modifier = Modifier.height(36.dp)) { content() }
    }
}
