package nz.eloque.openmaps.ui.stop

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import nz.eloque.openmaps.map.MapController
import nz.eloque.openmaps.ui.components.DepartureGroupRow
import nz.eloque.openmaps.ui.components.DirectionsButton
import nz.eloque.openmaps.ui.components.FocusOnMap
import nz.eloque.openmaps.ui.components.LoadState
import nz.eloque.openmaps.ui.components.LoadStateContent
import nz.eloque.openmaps.ui.components.rememberNow
import nz.eloque.openmaps.ui.navigation.StopKey
import nz.eloque.openmaps.ui.resources.Res
import nz.eloque.openmaps.ui.resources.departures
import nz.eloque.openmaps.ui.resources.no_departures
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

@Composable
fun StopSheet(
    stop: StopKey,
    map: MapController,
    onDirections: () -> Unit,
) {
    val viewModel = koinViewModel<StopViewModel> { parametersOf(stop.id) }
    val departures by viewModel.departures.collectAsStateWithLifecycle()
    val now by rememberNow()
    FocusOnMap(map, stop.location)

    LazyColumn(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        item {
            Text(stop.name, style = MaterialTheme.typography.headlineSmall)
            DirectionsButton(onDirections, Modifier.padding(top = 8.dp))
            Text(
                stringResource(Res.string.departures),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp, bottom = 8.dp),
            )
            HorizontalDivider()
        }
        when (val state = departures) {
            is LoadState.Loaded ->
                if (state.value.isEmpty()) {
                    item {
                        Text(
                            stringResource(Res.string.no_departures),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 16.dp),
                        )
                    }
                } else {
                    items(state.value) { DepartureGroupRow(it, now) }
                }
            else -> item { LoadStateContent(state, onRetry = viewModel::retry) {} }
        }
    }
}
