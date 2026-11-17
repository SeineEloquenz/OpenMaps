package nz.eloque.openmaps.ui.guidance

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsWalk
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import nz.eloque.openmaps.core.model.Leg
import nz.eloque.openmaps.core.model.SettingsStore
import nz.eloque.openmaps.core.model.TripGuidance
import nz.eloque.openmaps.core.model.TripGuide
import nz.eloque.openmaps.core.model.TripStage
import nz.eloque.openmaps.ui.components.clockTime
import nz.eloque.openmaps.ui.components.formatDistance
import nz.eloque.openmaps.ui.components.icon
import nz.eloque.openmaps.ui.resources.Res
import nz.eloque.openmaps.ui.resources.guide_at_risk
import nz.eloque.openmaps.ui.resources.guide_cancelled
import nz.eloque.openmaps.ui.resources.guide_platform_changed
import nz.eloque.openmaps.ui.resources.guide_ride
import nz.eloque.openmaps.ui.resources.guide_stops_left
import nz.eloque.openmaps.ui.resources.guide_wait
import nz.eloque.openmaps.ui.resources.guide_wait_detail
import nz.eloque.openmaps.ui.resources.guide_wait_platform
import nz.eloque.openmaps.ui.resources.guide_walk_detail
import nz.eloque.openmaps.ui.resources.guide_walk_end
import nz.eloque.openmaps.ui.resources.guide_walk_to
import nz.eloque.openmaps.ui.resources.navigation_arrived
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject

/** What to do now on a public transport trip, at the top of the screen. */
@Composable
fun TransitGuidanceBanner(modifier: Modifier = Modifier) {
    val state by koinInject<TripGuide>().state.collectAsStateWithLifecycle()
    val trip = state ?: return
    val colors = MaterialTheme.colorScheme
    val step = trip.step()
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = colors.primaryContainer, contentColor = colors.onPrimaryContainer),
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(step.icon, contentDescription = null, modifier = Modifier.size(40.dp))
            Column(Modifier.padding(start = 16.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(step.title, style = MaterialTheme.typography.headlineSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                step.detail?.let { Text(it, style = MaterialTheme.typography.bodyLarge) }
                if (step.cancelled) Text(stringResource(Res.string.guide_cancelled), color = colors.error)
                step.movedFrom?.let { Text(stringResource(Res.string.guide_platform_changed, it), color = colors.error) }
                if (trip.connectionAtRisk) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Icon(Icons.Default.Warning, contentDescription = null, tint = colors.error, modifier = Modifier.size(18.dp))
                        Text(stringResource(Res.string.guide_at_risk), color = colors.error, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
    }
}

private class Step(
    val icon: ImageVector,
    val title: String,
    val detail: String?,
    val cancelled: Boolean = false,
    /** The planned platform when the vehicle leaves from another one. */
    val movedFrom: String? = null,
)

@Composable
private fun TripGuidance.step(): Step {
    val unit by koinInject<SettingsStore>().distanceUnit.collectAsStateWithLifecycle()
    val legs = itinerary.legs
    val leg = legs.getOrNull(progress.legIndex)
    return when (progress.stage) {
        TripStage.WALK -> {
            val vehicle = legs.getOrNull(progress.legIndex + 1) as? Leg.Transit
            val distance = progress.metersLeft?.let { formatDistance(it, unit) }
            if (vehicle == null) {
                Step(Icons.AutoMirrored.Filled.DirectionsWalk, stringResource(Res.string.guide_walk_end), distance)
            } else {
                Step(
                    Icons.AutoMirrored.Filled.DirectionsWalk,
                    stringResource(Res.string.guide_walk_to, vehicle.from.name),
                    stringResource(Res.string.guide_walk_detail, distance.orEmpty(), vehicle.line.label, clockTime(vehicle.start.actual)),
                    vehicle.isCancelled,
                    vehicle.from.scheduledPlatform.takeIf { vehicle.from.platformChanged },
                )
            }
        }
        TripStage.WAIT -> {
            val vehicle = leg as Leg.Transit
            val platform = vehicle.from.platform
            Step(
                vehicle.mode.icon,
                stringResource(Res.string.guide_wait, vehicle.line.label, vehicle.headsign.orEmpty()),
                if (platform != null) {
                    stringResource(Res.string.guide_wait_platform, clockTime(vehicle.start.actual), platform)
                } else {
                    stringResource(Res.string.guide_wait_detail, clockTime(vehicle.start.actual))
                },
                vehicle.isCancelled,
                vehicle.from.scheduledPlatform.takeIf { vehicle.from.platformChanged },
            )
        }
        TripStage.RIDE -> {
            val vehicle = leg as Leg.Transit
            Step(
                vehicle.mode.icon,
                stringResource(Res.string.guide_ride, vehicle.to.name),
                pluralStringResource(Res.plurals.guide_stops_left, progress.stopsLeft, progress.stopsLeft, clockTime(vehicle.end.actual)),
                vehicle.isCancelled,
            )
        }
        TripStage.ARRIVED -> Step(Icons.Default.Flag, stringResource(Res.string.navigation_arrived), null)
    }
}
