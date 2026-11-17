package nz.eloque.openmaps.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import nz.eloque.openmaps.core.model.FavouriteLabel
import nz.eloque.openmaps.core.model.PlacesStore
import nz.eloque.openmaps.core.model.SearchResult
import nz.eloque.openmaps.core.model.storageKey
import nz.eloque.openmaps.ui.resources.Res
import nz.eloque.openmaps.ui.resources.new_list
import nz.eloque.openmaps.ui.resources.save
import nz.eloque.openmaps.ui.resources.save_as_favourite
import nz.eloque.openmaps.ui.resources.save_as_home
import nz.eloque.openmaps.ui.resources.save_as_work
import nz.eloque.openmaps.ui.resources.saved
import nz.eloque.openmaps.ui.saved.ListDialog
import nz.eloque.openmaps.ui.saved.ListIcon
import nz.eloque.openmaps.ui.search.titleRes
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject

/** Saves [place] as home, work or a favourite, and in the user's lists. Ticked entries remove it again. */
@Composable
fun SaveButton(
    place: SearchResult,
    modifier: Modifier = Modifier,
) {
    val store = koinInject<PlacesStore>()
    val favourites by store.favourites.collectAsState(emptyList())
    val lists by store.lists.collectAsState(emptyList())
    val favourite = favourites.firstOrNull { it.place.storageKey == place.storageKey }
    val inLists = lists.filter { place in it }
    val scope = rememberCoroutineScope()
    var menuOpen by remember { mutableStateOf(false) }
    var naming by remember { mutableStateOf(false) }

    Box(modifier) {
        if (favourite != null || inLists.isNotEmpty()) {
            FilledTonalButton(onClick = { menuOpen = true }) {
                Icon(favourite?.label?.icon ?: ListIcon.of(inLists.firstOrNull()?.icon).vector, contentDescription = null)
                Text(
                    favourite?.label?.titleRes?.let { stringResource(it) } ?: inLists.singleOrNull()?.name
                        ?: stringResource(Res.string.saved),
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
        } else {
            OutlinedButton(onClick = { menuOpen = true }) {
                Icon(Icons.Outlined.BookmarkBorder, contentDescription = null)
                Text(stringResource(Res.string.save), modifier = Modifier.padding(start = 8.dp))
            }
        }
        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
            listOf(
                FavouriteLabel.HOME to Res.string.save_as_home,
                FavouriteLabel.WORK to Res.string.save_as_work,
                FavouriteLabel.OTHER to Res.string.save_as_favourite,
            ).forEach { (label, text) ->
                val ticked = favourite?.label == label
                DropdownMenuItem(
                    text = { Text(stringResource(text)) },
                    leadingIcon = { Icon(label.icon, contentDescription = null) },
                    trailingIcon = if (ticked) ({ Icon(Icons.Default.Check, contentDescription = null) }) else null,
                    onClick = {
                        menuOpen = false
                        scope.launch {
                            favourite?.let { store.removeFavourite(it.id) }
                            if (!ticked) store.saveFavourite(place, label)
                        }
                    },
                )
            }
            HorizontalDivider()
            lists.forEach { list ->
                val ticked = place in list
                DropdownMenuItem(
                    text = { Text(list.name) },
                    leadingIcon = { Icon(ListIcon.of(list.icon).vector, contentDescription = null) },
                    trailingIcon = if (ticked) ({ Icon(Icons.Default.Check, contentDescription = null) }) else null,
                    onClick = {
                        menuOpen = false
                        scope.launch { if (ticked) store.removeFromList(list.id, place) else store.addToList(list.id, place) }
                    },
                )
            }
            DropdownMenuItem(
                text = { Text(stringResource(Res.string.new_list)) },
                leadingIcon = { Icon(Icons.Default.Add, contentDescription = null) },
                onClick = {
                    menuOpen = false
                    naming = true
                },
            )
        }
    }

    if (naming) {
        ListDialog(
            initialName = "",
            initialIcon = ListIcon.LIST,
            onDismiss = { naming = false },
            onConfirm = { name, icon ->
                naming = false
                scope.launch { store.addToList(store.createList(name, icon.name), place) }
            },
        )
    }
}
