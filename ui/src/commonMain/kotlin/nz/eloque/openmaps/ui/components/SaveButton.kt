package nz.eloque.openmaps.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
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
import nz.eloque.openmaps.ui.resources.save
import nz.eloque.openmaps.ui.resources.save_as_favourite
import nz.eloque.openmaps.ui.resources.save_as_home
import nz.eloque.openmaps.ui.resources.save_as_work
import nz.eloque.openmaps.ui.resources.saved
import nz.eloque.openmaps.ui.search.titleRes
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject

/** Saves [place] as home, work or a favourite. When already saved, a tap removes it. */
@Composable
fun SaveButton(
    place: SearchResult,
    modifier: Modifier = Modifier,
) {
    val store = koinInject<PlacesStore>()
    val favourites by store.favourites.collectAsState(emptyList())
    val saved = favourites.firstOrNull { it.place.storageKey == place.storageKey }
    val scope = rememberCoroutineScope()
    var menuOpen by remember { mutableStateOf(false) }

    Box(modifier) {
        if (saved != null) {
            FilledTonalButton(onClick = { scope.launch { store.removeFavourite(saved.id) } }) {
                Icon(saved.label.icon, contentDescription = null)
                Text(
                    saved.label.titleRes?.let { stringResource(it) } ?: stringResource(Res.string.saved),
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
                DropdownMenuItem(
                    text = { Text(stringResource(text)) },
                    leadingIcon = { Icon(label.icon, contentDescription = null) },
                    onClick = {
                        menuOpen = false
                        scope.launch { store.saveFavourite(place, label) }
                    },
                )
            }
        }
    }
}
