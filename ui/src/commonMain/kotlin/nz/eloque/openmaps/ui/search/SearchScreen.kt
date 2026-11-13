package nz.eloque.openmaps.ui.search

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Place
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import nz.eloque.compose_kit.dialog.FullscreenDialog
import nz.eloque.openmaps.core.model.LatLng
import nz.eloque.openmaps.core.model.SearchResult
import nz.eloque.openmaps.core.model.TransportMode
import nz.eloque.openmaps.ui.components.LoadStateContent
import nz.eloque.openmaps.ui.components.icon
import nz.eloque.openmaps.ui.resources.Res
import nz.eloque.openmaps.ui.resources.back
import nz.eloque.openmaps.ui.resources.clear
import nz.eloque.openmaps.ui.resources.no_results
import nz.eloque.openmaps.ui.resources.search_placeholder
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun SearchScreen(
    near: LatLng?,
    onDismiss: () -> Unit,
    onSelect: (SearchResult) -> Unit,
) {
    val viewModel = koinViewModel<SearchViewModel>()
    viewModel.near = near
    val query by viewModel.query.collectAsStateWithLifecycle()
    val results by viewModel.results.collectAsStateWithLifecycle()
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }

    FullscreenDialog(onDismiss = onDismiss, contentAlignment = Alignment.TopStart) {
        Surface(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize().safeDrawingPadding().imePadding()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(Res.string.back))
                    }
                    TextField(
                        value = query,
                        onValueChange = viewModel::setQuery,
                        placeholder = { Text(stringResource(Res.string.search_placeholder)) },
                        singleLine = true,
                        modifier = Modifier.weight(1f).focusRequester(focus),
                        colors =
                            TextFieldDefaults.colors(
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent,
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent,
                            ),
                    )
                    if (query.isNotEmpty()) {
                        IconButton(onClick = { viewModel.setQuery("") }) {
                            Icon(Icons.Default.Clear, contentDescription = stringResource(Res.string.clear))
                        }
                    }
                }
                HorizontalDivider()
                results?.let { state ->
                    LoadStateContent(state, onRetry = {
                        viewModel.setQuery(query)
                    }, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                        SearchResults(it, onSelect)
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchResults(
    results: List<SearchResult>,
    onSelect: (SearchResult) -> Unit,
) {
    if (results.isEmpty()) {
        Text(
            stringResource(Res.string.no_results),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.fillMaxWidth().padding(24.dp),
        )
        return
    }
    LazyColumn {
        items(results) { result ->
            ListItem(
                headlineContent = { Text(result.name) },
                supportingContent = result.area?.let { { Text(it) } },
                leadingContent = { Icon(result.icon, contentDescription = null) },
                modifier = Modifier.clickable { onSelect(result) },
            )
        }
    }
}

private val SearchResult.icon
    get() =
        when (this) {
            is SearchResult.Stop -> StopIconPriority.firstOrNull { it in modes }?.icon ?: Icons.Default.Place
            is SearchResult.Address -> Icons.Default.Home
            is SearchResult.Place -> Icons.Default.Place
        }

private val StopIconPriority =
    listOf(
        TransportMode.HIGHSPEED_RAIL,
        TransportMode.LONG_DISTANCE_RAIL,
        TransportMode.REGIONAL_RAIL,
        TransportMode.SUBURBAN,
        TransportMode.SUBWAY,
        TransportMode.TRAM,
        TransportMode.FERRY,
        TransportMode.BUS,
        TransportMode.COACH,
    )
