package nz.eloque.openmaps.ui.saved

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Directions
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import nz.eloque.openmaps.core.model.Favourite
import nz.eloque.openmaps.core.model.PlacesStore
import nz.eloque.openmaps.core.model.SavedRoute
import nz.eloque.openmaps.core.model.SearchResult
import nz.eloque.openmaps.ui.components.icon
import nz.eloque.openmaps.ui.resources.Res
import nz.eloque.openmaps.ui.resources.cancel
import nz.eloque.openmaps.ui.resources.delete
import nz.eloque.openmaps.ui.resources.favourites
import nz.eloque.openmaps.ui.resources.name
import nz.eloque.openmaps.ui.resources.nothing_saved
import nz.eloque.openmaps.ui.resources.ok
import nz.eloque.openmaps.ui.resources.rename
import nz.eloque.openmaps.ui.resources.saved
import nz.eloque.openmaps.ui.resources.saved_routes
import nz.eloque.openmaps.ui.resources.your_location
import nz.eloque.openmaps.ui.search.titleRes
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject

/** Lists saved places and routes. Tapping a place opens it, tapping a route plans it again. */
@Composable
fun SavedSheet(
    onOpenPlace: (SearchResult) -> Unit,
    onOpenRoute: (SavedRoute) -> Unit,
) {
    val store = koinInject<PlacesStore>()
    val favourites by store.favourites.collectAsStateWithLifecycle(emptyList())
    val routes by store.savedRoutes.collectAsStateWithLifecycle(emptyList())
    val scope = rememberCoroutineScope()
    var renaming by remember { mutableStateOf<Favourite?>(null) }
    val transparent = ListItemDefaults.colors(containerColor = Color.Transparent)

    LazyColumn(Modifier.fillMaxWidth()) {
        item {
            Text(
                stringResource(Res.string.saved),
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
        }
        if (favourites.isEmpty() && routes.isEmpty()) {
            item {
                Text(
                    stringResource(Res.string.nothing_saved),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp),
                )
            }
        }
        if (favourites.isNotEmpty()) item { SectionTitle(Res.string.favourites) }
        items(favourites, key = { "favourite-${it.id}" }) { favourite ->
            val label = favourite.label.titleRes?.let { stringResource(it) }
            ListItem(
                headlineContent = { Text(label ?: favourite.displayName) },
                supportingContent = { Text(if (label != null) favourite.displayName else favourite.place.area.orEmpty()) },
                leadingContent = { Icon(favourite.label.icon, contentDescription = null) },
                trailingContent = {
                    Row {
                        IconButton(onClick = { renaming = favourite }) {
                            Icon(Icons.Default.Edit, contentDescription = stringResource(Res.string.rename))
                        }
                        IconButton(onClick = { scope.launch { store.removeFavourite(favourite.id) } }) {
                            Icon(Icons.Default.Delete, contentDescription = stringResource(Res.string.delete))
                        }
                    }
                },
                colors = transparent,
                modifier = Modifier.clickable { onOpenPlace(favourite.place) },
            )
        }
        if (routes.isNotEmpty()) item { SectionTitle(Res.string.saved_routes) }
        items(routes, key = { "route-${it.id}" }) { route ->
            val here = stringResource(Res.string.your_location)
            ListItem(
                headlineContent = { Text(route.to?.name ?: here) },
                supportingContent = { Text(route.from?.name ?: here) },
                leadingContent = { Icon(Icons.Default.Directions, contentDescription = null) },
                trailingContent = {
                    IconButton(onClick = { scope.launch { store.removeRoute(route.id) } }) {
                        Icon(Icons.Default.Delete, contentDescription = stringResource(Res.string.delete))
                    }
                },
                colors = transparent,
                modifier = Modifier.clickable { onOpenRoute(route) },
            )
        }
    }

    renaming?.let { favourite ->
        var text by remember(favourite.id) { mutableStateOf(favourite.displayName) }
        AlertDialog(
            onDismissRequest = { renaming = null },
            title = { Text(stringResource(Res.string.rename)) },
            text = {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text(stringResource(Res.string.name)) },
                    placeholder = { Text(favourite.place.name) },
                    singleLine = true,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        renaming = null
                        scope.launch { store.renameFavourite(favourite.id, text.takeIf { it.trim() != favourite.place.name }) }
                    },
                ) { Text(stringResource(Res.string.ok)) }
            },
            dismissButton = { TextButton(onClick = { renaming = null }) { Text(stringResource(Res.string.cancel)) } },
        )
    }
}

@Composable
private fun SectionTitle(title: StringResource) {
    Text(
        stringResource(title),
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 4.dp),
    )
}
