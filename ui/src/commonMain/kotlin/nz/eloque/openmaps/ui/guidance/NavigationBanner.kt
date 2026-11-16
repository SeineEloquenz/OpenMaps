package nz.eloque.openmaps.ui.guidance

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.RoundaboutRight
import androidx.compose.material.icons.filled.Straight
import androidx.compose.material.icons.filled.TurnLeft
import androidx.compose.material.icons.filled.TurnRight
import androidx.compose.material.icons.filled.TurnSharpLeft
import androidx.compose.material.icons.filled.TurnSharpRight
import androidx.compose.material.icons.filled.TurnSlightLeft
import androidx.compose.material.icons.filled.TurnSlightRight
import androidx.compose.material.icons.filled.UTurnLeft
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import nz.eloque.openmaps.core.model.ManeuverType
import nz.eloque.openmaps.core.model.NavigationStatus
import nz.eloque.openmaps.core.model.Navigator
import nz.eloque.openmaps.core.model.SettingsStore
import nz.eloque.openmaps.ui.components.formatDistance
import nz.eloque.openmaps.ui.resources.Res
import nz.eloque.openmaps.ui.resources.navigation_arrived
import nz.eloque.openmaps.ui.resources.navigation_off_route
import nz.eloque.openmaps.ui.resources.navigation_rerouting
import nz.eloque.openmaps.ui.resources.navigation_then
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject

/** The next maneuver at the top of the screen while navigating. */
@Composable
fun NavigationBanner(modifier: Modifier = Modifier) {
    val state by koinInject<Navigator>().state.collectAsStateWithLifecycle()
    val unit by koinInject<SettingsStore>().distanceUnit.collectAsStateWithLifecycle()
    val trip = state ?: return
    val colors = MaterialTheme.colorScheme
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = colors.primaryContainer, contentColor = colors.onPrimaryContainer),
    ) {
        val status =
            when (trip.status) {
                NavigationStatus.REROUTING -> stringResource(Res.string.navigation_rerouting)
                NavigationStatus.OFF_ROUTE -> stringResource(Res.string.navigation_off_route)
                NavigationStatus.ARRIVED -> stringResource(Res.string.navigation_arrived)
                NavigationStatus.NAVIGATING -> null
            }
        val instruction = trip.instruction
        if (status != null || instruction == null) {
            Text(status.orEmpty(), style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(16.dp))
            return@Card
        }
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(instruction.maneuver.icon, contentDescription = null, modifier = Modifier.size(48.dp))
                Text(
                    formatDistance(trip.metersToManeuver, unit),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
            }
            Column(Modifier.padding(start = 16.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    instruction.text,
                    style = MaterialTheme.typography.headlineSmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                instruction.secondary?.let {
                    Text(it, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
        if (instruction.lanes.isNotEmpty()) LaneRow(instruction.lanes, Modifier.padding(start = 16.dp, end = 16.dp, bottom = 12.dp))
        trip.then?.takeIf { trip.metersToManeuver < THEN_METERS }?.let { then ->
            Row(
                Modifier.padding(start = 16.dp, end = 16.dp, bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(stringResource(Res.string.navigation_then), style = MaterialTheme.typography.bodyMedium)
                Icon(then.maneuver.icon, contentDescription = null, modifier = Modifier.size(20.dp))
            }
        }
    }
}

val ManeuverType.icon: ImageVector
    get() =
        when (this) {
            ManeuverType.DEPART -> Icons.Default.Navigation
            ManeuverType.ARRIVE -> Icons.Default.Flag
            ManeuverType.STRAIGHT, ManeuverType.OTHER -> Icons.Default.Straight
            ManeuverType.SLIGHT_LEFT -> Icons.Default.TurnSlightLeft
            ManeuverType.LEFT -> Icons.Default.TurnLeft
            ManeuverType.SHARP_LEFT -> Icons.Default.TurnSharpLeft
            ManeuverType.SLIGHT_RIGHT -> Icons.Default.TurnSlightRight
            ManeuverType.RIGHT -> Icons.Default.TurnRight
            ManeuverType.SHARP_RIGHT -> Icons.Default.TurnSharpRight
            ManeuverType.U_TURN -> Icons.Default.UTurnLeft
            ManeuverType.ROUNDABOUT -> Icons.Default.RoundaboutRight
        }

private const val THEN_METERS = 300.0
