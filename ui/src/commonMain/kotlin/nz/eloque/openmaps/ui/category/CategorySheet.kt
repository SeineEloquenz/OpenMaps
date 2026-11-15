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
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Directions
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Refresh
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import nz.eloque.openmaps.core.model.GeoBounds
import nz.eloque.openmaps.core.model.OpeningHours
import nz.eloque.openmaps.core.model.PlaceDetails
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
import nz.eloque.openmaps.ui.resources.Res
import nz.eloque.openmaps.ui.resources.call
import nz.eloque.openmaps.ui.resources.category_none
import nz.eloque.openmaps.ui.resources.charging_attribution
import nz.eloque.openmaps.ui.resources.directions
import nz.eloque.openmaps.ui.resources.search_this_area
import nz.eloque.openmaps.ui.resources.website
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf
import kotlin.math.abs

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
    val profile = key.category.profile
    val origin = map.userLocation ?: map.center
    val shown =
        all.filter { profile.allows(filters, details[it], now) }.sortedBy { it.location.distanceTo(origin) }

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
            FilterRow(profile, filters, details.values, onChange = viewModel::setFilters)
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
                profile = profile,
                place = place,
                details = details[place],
                distance = formatDistance(place.location.distanceTo(origin), unit),
                onOpen = { onOpenPlace(place) },
                onDirections = { onDirections(place) },
            )
            HorizontalDivider(Modifier.padding(horizontal = 16.dp))
        }
        if (viewModel.fromChargingDatabase && shown.isNotEmpty()) {
            item {
                val uriHandler = LocalUriHandler.current
                Text(
                    stringResource(Res.string.charging_attribution),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.clickable { uriHandler.openUri(OPEN_CHARGE_MAP_URL) }.padding(16.dp),
                )
            }
        }
    }
}

private const val OPEN_CHARGE_MAP_URL = "https://openchargemap.org"

@Composable
private fun FilterRow(
    profile: CategoryProfile,
    filters: CategoryFilters,
    details: Collection<PlaceDetails>,
    onChange: (CategoryFilters) -> Unit,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 4.dp),
    ) {
        profile.filters.forEach { filter ->
            when (filter) {
                is ResultFilter.Toggle -> {
                    val on = filter.id in filters.on
                    FilterChip(
                        selected = on,
                        onClick = { onChange(filters.copy(on = if (on) filters.on - filter.id else filters.on + filter.id)) },
                        label = { Text(filter.text()) },
                        leadingIcon =
                            filter.icon?.let { icon ->
                                { Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp)) }
                            },
                    )
                }
                is ResultFilter.Choice -> {
                    val values = details.flatMap(filter.values).distinct().sortedBy(filter.display)
                    val chosen = filters.choices[filter.id]
                    if (values.isNotEmpty() || chosen != null) {
                        ChoiceChip(filter, values, chosen) { value ->
                            onChange(
                                filters.copy(
                                    choices =
                                        if (value ==
                                            null
                                        ) {
                                            filters.choices - filter.id
                                        } else {
                                            filters.choices + (filter.id to value)
                                        },
                                ),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ChoiceChip(
    filter: ResultFilter.Choice,
    values: List<String>,
    chosen: String?,
    onChoose: (String?) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    Box {
        FilterChip(
            selected = chosen != null,
            onClick = { open = true },
            label = { Text(chosen?.let(filter.display) ?: stringResource(filter.label)) },
            trailingIcon = { Icon(Icons.Default.ArrowDropDown, contentDescription = null, modifier = Modifier.size(18.dp)) },
        )
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(filter.anyLabel)) },
                onClick = {
                    onChoose(null)
                    open = false
                },
            )
            values.forEach { value ->
                DropdownMenuItem(
                    text = { Text(filter.display(value)) },
                    onClick = {
                        onChoose(value)
                        open = false
                    },
                )
            }
        }
    }
}

@Composable
private fun PlaceResult(
    profile: CategoryProfile,
    place: SearchResult.Place,
    details: PlaceDetails?,
    distance: String,
    onOpen: () -> Unit,
    onDirections: () -> Unit,
) {
    val uriHandler = LocalUriHandler.current
    val facts = details?.let(profile.facts).orEmpty().map { stringResource(it) }
    Column(
        Modifier.fillMaxWidth().clickable(onClick = onOpen).padding(vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(place.name, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(horizontal = 16.dp))
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 16.dp)) {
            Text(
                (listOfNotNull(profile.kind(place, details)?.takeIf { it.isNotBlank() }, distance) + facts).joinToString(" · "),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
            details?.let(profile.icons).orEmpty().forEach { icon ->
                Icon(
                    icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 6.dp).size(16.dp),
                )
            }
        }
        if (profile.showsDescription) {
            details?.description?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }
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
