package nz.eloque.openmaps.ui.guidance

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.filter
import nz.eloque.openmaps.core.model.Leg
import nz.eloque.openmaps.core.model.TripStage
import nz.eloque.openmaps.map.MapController
import nz.eloque.openmaps.map.NavigationPuck
import nz.eloque.openmaps.ui.components.clockTime
import nz.eloque.openmaps.ui.directions.ShowItineraryOnMap
import nz.eloque.openmaps.ui.navigation.Endpoint
import nz.eloque.openmaps.ui.navigation.TransitGuidanceKey
import nz.eloque.openmaps.ui.resources.Res
import nz.eloque.openmaps.ui.resources.guide_arrive
import nz.eloque.openmaps.ui.resources.guide_find_new
import nz.eloque.openmaps.ui.resources.navigation_recenter
import nz.eloque.openmaps.ui.resources.navigation_simulated
import nz.eloque.openmaps.ui.resources.route_expired
import nz.eloque.openmaps.ui.resources.your_location
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

/** The running public transport trip: arrival, a new connection when this one fails, and ending it. */
@Composable
fun TransitGuidanceSheet(
    key: TransitGuidanceKey,
    map: MapController,
    onEnd: () -> Unit,
    onReplan: (Endpoint) -> Unit,
) {
    val viewModel = koinViewModel<TransitGuidanceViewModel> { parametersOf(key) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    var following by rememberSaveable { mutableStateOf(true) }
    val itinerary = state?.itinerary ?: viewModel.itinerary

    ShowItineraryOnMap(map, itinerary, fit = false)
    LaunchedEffect(viewModel.itinerary) { viewModel.itinerary?.let { map.fit(it.legs.flatMap(Leg::geometry)) } }
    LaunchedEffect(map) { snapshotFlow { map.isMovedByUser }.filter { it }.collect { following = false } }
    DisposableEffect(map) { onDispose { map.puck = null } }
    LaunchedEffect(state?.position, following) {
        val position = state?.position ?: return@LaunchedEffect
        map.puck = NavigationPuck(position, null)
        if (following) map.follow(position, bearing = null, zoom = FOLLOW_ZOOM, tilt = 0.0)
    }

    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (itinerary == null) {
            Text(stringResource(Res.string.route_expired), color = MaterialTheme.colorScheme.onSurfaceVariant)
            EndButton(onEnd)
            return@Column
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    stringResource(Res.string.guide_arrive, clockTime(itinerary.end)),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                )
                if (key.simulate) {
                    Text(
                        stringResource(Res.string.navigation_simulated),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (!following) {
                FilledTonalIconButton(onClick = { following = true }) {
                    Icon(Icons.Default.MyLocation, contentDescription = stringResource(Res.string.navigation_recenter))
                }
            }
            EndButton(onEnd)
        }
        if (state?.connectionAtRisk == true && state?.progress?.stage != TripStage.ARRIVED) {
            val destination = itinerary.legs.last().to
            val name = key.destinationName ?: stringResource(Res.string.your_location)
            FilledTonalButton(onClick = { onReplan(Endpoint(name, destination.location.latitude, destination.location.longitude)) }) {
                Text(stringResource(Res.string.guide_find_new))
            }
        }
    }
}

private const val FOLLOW_ZOOM = 15.5
