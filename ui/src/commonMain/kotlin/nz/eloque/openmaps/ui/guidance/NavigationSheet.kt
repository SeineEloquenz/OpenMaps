package nz.eloque.openmaps.ui.guidance

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.filter
import nz.eloque.openmaps.core.model.LatLng
import nz.eloque.openmaps.core.model.NavigationState
import nz.eloque.openmaps.core.model.NavigationStatus
import nz.eloque.openmaps.core.model.SettingsStore
import nz.eloque.openmaps.core.model.TransportMode
import nz.eloque.openmaps.core.model.VoiceGuidance
import nz.eloque.openmaps.core.model.VoiceStatus
import nz.eloque.openmaps.map.MapController
import nz.eloque.openmaps.map.MapLine
import nz.eloque.openmaps.map.NavigationPuck
import nz.eloque.openmaps.ui.components.LoadState
import nz.eloque.openmaps.ui.components.clockTime
import nz.eloque.openmaps.ui.components.formatDistance
import nz.eloque.openmaps.ui.components.formatDuration
import nz.eloque.openmaps.ui.navigation.NavigationKey
import nz.eloque.openmaps.ui.resources.Res
import nz.eloque.openmaps.ui.resources.navigation_arrived
import nz.eloque.openmaps.ui.resources.navigation_end
import nz.eloque.openmaps.ui.resources.navigation_finding_route
import nz.eloque.openmaps.ui.resources.navigation_mute
import nz.eloque.openmaps.ui.resources.navigation_no_route
import nz.eloque.openmaps.ui.resources.navigation_recenter
import nz.eloque.openmaps.ui.resources.navigation_simulated
import nz.eloque.openmaps.ui.resources.navigation_summary
import nz.eloque.openmaps.ui.resources.navigation_unmute
import nz.eloque.openmaps.ui.resources.retry
import nz.eloque.openmaps.ui.resources.voice_no_engine
import nz.eloque.openmaps.ui.resources.voice_no_language
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds

/** The running trip: time and distance left, and the buttons to end it or bring the camera back to the user. */
@Composable
fun NavigationSheet(
    key: NavigationKey,
    map: MapController,
    onEnd: () -> Unit,
) {
    val viewModel = koinViewModel<NavigationViewModel> { parametersOf(key) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val start by viewModel.start.collectAsStateWithLifecycle()
    val unit by koinInject<SettingsStore>().distanceUnit.collectAsStateWithLifecycle()
    var following by rememberSaveable { mutableStateOf(true) }
    val voice = koinInject<VoiceGuidance>()
    val muted by voice.muted.collectAsStateWithLifecycle()
    val voiceStatus by voice.status.collectAsStateWithLifecycle()

    val userLocation = map.userLocation
    LaunchedEffect(userLocation != null) { viewModel.begin(userLocation) }
    LaunchedEffect(map) { if (!key.simulate && !map.isLocationPermissionGranted) map.requestLocationPermission() }
    LaunchedEffect(map) { snapshotFlow { map.isMovedByUser }.filter { it }.collect { following = false } }
    ShowTripOnMap(map, state, following)
    val arrived = state?.status == NavigationStatus.ARRIVED
    LaunchedEffect(arrived) {
        if (arrived) {
            delay(END_AFTER_ARRIVAL)
            onEnd()
        }
    }

    Column(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        val trip = state
        when {
            start == LoadState.Failed -> {
                Text(stringResource(Res.string.navigation_no_route), style = MaterialTheme.typography.titleMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilledTonalButton(onClick = { viewModel.begin(map.userLocation) }) { Text(stringResource(Res.string.retry)) }
                    EndButton(onEnd)
                }
            }
            trip == null -> {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    CircularProgressIndicator(Modifier.size(24.dp))
                    Text(stringResource(Res.string.navigation_finding_route), style = MaterialTheme.typography.titleMedium)
                }
                EndButton(onEnd)
            }
            else ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        if (trip.status == NavigationStatus.ARRIVED) {
                            Text(stringResource(Res.string.navigation_arrived), style = MaterialTheme.typography.headlineSmall)
                        } else {
                            Text(
                                formatDuration(trip.remainingTime),
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                            )
                            Text(
                                stringResource(
                                    Res.string.navigation_summary,
                                    formatDistance(trip.remainingMeters, unit),
                                    clockTime(Clock.System.now() + trip.remainingTime),
                                ),
                                style = MaterialTheme.typography.bodyLarge,
                            )
                        }
                        val problem = voiceStatus.problem
                        when {
                            problem != null ->
                                Text(
                                    stringResource(problem),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error,
                                )
                            key.simulate ->
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
                    IconButton(onClick = { voice.setMuted(!muted) }, enabled = voiceStatus == VoiceStatus.READY) {
                        Icon(
                            if (muted ||
                                voiceStatus != VoiceStatus.READY
                            ) {
                                Icons.AutoMirrored.Filled.VolumeOff
                            } else {
                                Icons.AutoMirrored.Filled.VolumeUp
                            },
                            contentDescription = stringResource(if (muted) Res.string.navigation_unmute else Res.string.navigation_mute),
                        )
                    }
                    EndButton(onEnd)
                }
        }
    }
}

private val VoiceStatus.problem
    get() =
        when (this) {
            VoiceStatus.READY -> null
            VoiceStatus.NO_ENGINE -> Res.string.voice_no_engine
            VoiceStatus.NO_LANGUAGE -> Res.string.voice_no_language
        }

@Composable
internal fun EndButton(onEnd: () -> Unit) {
    Button(
        onClick = onEnd,
        colors =
            ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.error,
                contentColor = MaterialTheme.colorScheme.onError,
            ),
    ) {
        Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(18.dp))
        Text(stringResource(Res.string.navigation_end), modifier = Modifier.padding(start = 8.dp))
    }
}

/** The route split into the part still ahead and the part already driven, the arrow at the user, and the camera behind it. */
@Composable
private fun ShowTripOnMap(
    map: MapController,
    state: NavigationState?,
    following: Boolean,
) {
    val ahead = MaterialTheme.colorScheme.primary
    val drawn = remember { arrayOfNulls<List<MapLine>>(1) }
    DisposableEffect(map) {
        onDispose {
            map.puck = null
            // The next screen may already have drawn its own lines by the time this one closes.
            if (map.lines === drawn[0]) map.lines = emptyList()
        }
    }
    LaunchedEffect(state?.route) {
        val route = state?.route ?: return@LaunchedEffect
        if (state.position == null) map.fit(route)
    }
    // Keyed on the position too, so the line returns when the previous screen clears the map's lines while closing.
    LaunchedEffect(state?.route, state?.travelledMeters?.toInt()?.div(LINE_STEP_METERS), state?.position == null) {
        val trip = state ?: return@LaunchedEffect
        val (done, left) = split(trip.route, trip.travelledMeters)
        val lines = listOfNotNull(MapLine(done, TravelledColor, LineWidth).takeIf { done.size >= 2 }, MapLine(left, ahead, LineWidth))
        drawn[0] = lines
        map.lines = lines
    }
    LaunchedEffect(state?.position, state?.bearing, following) {
        val trip = state ?: return@LaunchedEffect
        val position = trip.position ?: return@LaunchedEffect
        map.puck = NavigationPuck(position, trip.bearing)
        if (following && trip.status != NavigationStatus.ARRIVED) {
            map.follow(position, trip.bearing, zoom(trip.mode), tilt(trip.mode))
        }
    }
}

/** [route] cut [meters] along it, into the part before and the part after. */
internal fun split(
    route: List<LatLng>,
    meters: Double,
): Pair<List<LatLng>, List<LatLng>> {
    if (route.size < 2 || meters <= 0) return emptyList<LatLng>() to route
    var walked = 0.0
    for (i in 0 until route.lastIndex) {
        val a = route[i]
        val b = route[i + 1]
        val length = a.distanceTo(b)
        if (walked + length >= meters) {
            val t = if (length == 0.0) 0.0 else (meters - walked) / length
            val cut = LatLng(a.latitude + (b.latitude - a.latitude) * t, a.longitude + (b.longitude - a.longitude) * t)
            return (route.subList(0, i + 1) + cut) to (listOf(cut) + route.subList(i + 1, route.size))
        }
        walked += length
    }
    return route to emptyList()
}

private fun zoom(mode: TransportMode) = if (mode == TransportMode.CAR) 16.5 else 17.5

private fun tilt(mode: TransportMode) = if (mode == TransportMode.CAR) 50.0 else 30.0

private const val LINE_STEP_METERS = 10
internal val END_AFTER_ARRIVAL = 30.seconds
private val TravelledColor = Color(0xFF9E9E9E)
private val LineWidth = 8.dp
