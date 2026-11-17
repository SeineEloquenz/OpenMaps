package nz.eloque.openmaps.ui.saved

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import nz.eloque.openmaps.core.model.PlacesStore
import nz.eloque.openmaps.core.model.SearchResult
import nz.eloque.openmaps.map.MapController
import nz.eloque.openmaps.ui.resources.Res
import nz.eloque.openmaps.ui.resources.list_empty
import nz.eloque.openmaps.ui.resources.remove_from_list
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject

/** The places of one list, also marked on the map. */
@Composable
fun ListSheet(
    listId: Long,
    map: MapController,
    onOpenPlace: (SearchResult) -> Unit,
) {
    val store = koinInject<PlacesStore>()
    val lists by store.lists.collectAsStateWithLifecycle(null)
    val list = lists?.firstOrNull { it.id == listId } ?: return
    val scope = rememberCoroutineScope()
    LaunchedEffect(list.places) {
        map.results = list.places.map { it.asPlace() }
    }
    LaunchedEffect(listId) {
        if (list.places.isNotEmpty()) map.fit(list.places.map { it.location })
    }

    LazyColumn(Modifier.fillMaxWidth()) {
        item {
            Text(list.name, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(horizontal = 16.dp))
        }
        if (list.places.isEmpty()) {
            item {
                Text(
                    stringResource(Res.string.list_empty),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp),
                )
            }
        }
        items(list.places, key = { it.location.toString() + it.name }) { place ->
            ListItem(
                headlineContent = { Text(place.name) },
                supportingContent = place.area?.let { area -> { Text(area) } },
                leadingContent = { Icon(Icons.Outlined.Place, contentDescription = null) },
                trailingContent = {
                    IconButton(onClick = { scope.launch { store.removeFromList(listId, place) } }) {
                        Icon(Icons.Default.Close, contentDescription = stringResource(Res.string.remove_from_list))
                    }
                },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                modifier = Modifier.clickable { onOpenPlace(place) },
            )
        }
    }
}

private fun SearchResult.asPlace(): SearchResult.Place = this as? SearchResult.Place ?: SearchResult.Place(null, name, location, area, null)
