package nz.eloque.openmaps.ui.category

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import nz.eloque.openmaps.core.model.GeoBounds
import nz.eloque.openmaps.core.model.SearchResult
import nz.eloque.openmaps.core.model.SettingsStore
import nz.eloque.openmaps.map.MapController
import nz.eloque.openmaps.ui.components.LoadState
import nz.eloque.openmaps.ui.components.LoadStateContent
import nz.eloque.openmaps.ui.components.formatDistance
import nz.eloque.openmaps.ui.navigation.CategoryKey
import nz.eloque.openmaps.ui.place.toCategoryLabel
import nz.eloque.openmaps.ui.resources.Res
import nz.eloque.openmaps.ui.resources.category_none
import nz.eloque.openmaps.ui.resources.search_this_area
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
) {
    val viewModel = koinViewModel<CategoryViewModel> { parametersOf(key.category) }
    val results by viewModel.results.collectAsStateWithLifecycle()
    val searchedArea by viewModel.searchedArea.collectAsStateWithLifecycle()
    val unit by koinInject<SettingsStore>().distanceUnit.collectAsState()

    LaunchedEffect(viewModel) {
        if (viewModel.searchedArea.value != null) return@LaunchedEffect
        val bounds = snapshotFlow { map.visibleBounds }.filterNotNull().first()
        viewModel.search(map.center, bounds)
    }
    LaunchedEffect(results) {
        val places = (results as? LoadState.Loaded)?.value.orEmpty()
        map.results = places
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
            LoadStateContent(results, onRetry = viewModel::retry, modifier = Modifier.fillMaxWidth()) { places ->
                if (places.isEmpty()) {
                    Text(
                        stringResource(Res.string.category_none),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(16.dp),
                    )
                }
            }
        }
        items((results as? LoadState.Loaded)?.value.orEmpty(), key = { "${it.osmId ?: it.name}-${it.location}" }) { place ->
            val distance = formatDistance(place.location.distanceTo(origin), unit)
            ListItem(
                headlineContent = { Text(place.name) },
                supportingContent = {
                    Text(listOfNotNull(place.category?.toCategoryLabel(), distance, place.area).joinToString(" · "))
                },
                leadingContent = {
                    Box(
                        Modifier.size(40.dp).background(MaterialTheme.colorScheme.secondaryContainer, CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            key.category.icon,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                modifier = Modifier.clickable { onOpenPlace(place) },
            )
        }
    }
}
