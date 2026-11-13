package nz.eloque.openmaps.ui.directions

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
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
import nz.eloque.openmaps.core.model.Itinerary
import nz.eloque.openmaps.core.model.Leg
import nz.eloque.openmaps.core.platform.Reminder
import nz.eloque.openmaps.core.platform.ReminderScheduler
import nz.eloque.openmaps.ui.components.clockTime
import nz.eloque.openmaps.ui.components.rememberNotificationPermissionRequest
import nz.eloque.openmaps.ui.resources.Res
import nz.eloque.openmaps.ui.resources.allow
import nz.eloque.openmaps.ui.resources.remind_me
import nz.eloque.openmaps.ui.resources.reminder_at
import nz.eloque.openmaps.ui.resources.reminder_before
import nz.eloque.openmaps.ui.resources.reminder_may_be_late
import nz.eloque.openmaps.ui.resources.reminder_needs_notifications
import nz.eloque.openmaps.ui.resources.reminder_set
import nz.eloque.openmaps.ui.resources.reminder_text_transit
import nz.eloque.openmaps.ui.resources.reminder_text_walk
import nz.eloque.openmaps.ui.resources.reminder_title
import nz.eloque.openmaps.ui.resources.reminder_too_late
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes

private val LeadTimes = listOf(5, 10, 15)

/** Schedules a notification some minutes before the time to leave. When one is set, a tap cancels it. */
@Composable
fun ReminderButton(
    itinerary: Itinerary,
    destinationName: String,
    snackbar: SnackbarHostState,
    modifier: Modifier = Modifier,
) {
    val scheduler = koinInject<ReminderScheduler>()
    val scheduled by scheduler.scheduled.collectAsState()
    val existing = scheduled[itinerary.id]
    val requestPermission = rememberNotificationPermissionRequest()
    val scope = rememberCoroutineScope()
    var menuOpen by remember { mutableStateOf(false) }

    fun schedule(leadMinutes: Int) {
        val at = itinerary.start - leadMinutes.minutes
        if (at <= Clock.System.now()) {
            scope.launch { snackbar.showSnackbar(getString(Res.string.reminder_too_late)) }
            return
        }
        requestPermission { granted ->
            scope.launch {
                if (!granted) {
                    snackbar.showSnackbar(getString(Res.string.reminder_needs_notifications))
                    return@launch
                }
                val transit = itinerary.legs.filterIsInstance<Leg.Transit>().firstOrNull()
                val text =
                    if (transit == null) {
                        getString(Res.string.reminder_text_walk)
                    } else {
                        getString(Res.string.reminder_text_transit, transit.line.label, transit.from.name, clockTime(transit.start.actual))
                    }
                scheduler.schedule(
                    Reminder(
                        id = itinerary.id,
                        at = at,
                        title = getString(Res.string.reminder_title, clockTime(itinerary.start), destinationName),
                        text = text,
                    ),
                )
                if (scheduler.canScheduleExact) {
                    snackbar.showSnackbar(getString(Res.string.reminder_set, clockTime(at)))
                } else {
                    val result = snackbar.showSnackbar(getString(Res.string.reminder_may_be_late), getString(Res.string.allow))
                    if (result == SnackbarResult.ActionPerformed) scheduler.openExactAlarmSettings()
                }
            }
        }
    }

    Box(modifier) {
        if (existing != null) {
            FilledTonalButton(onClick = { scheduler.cancel(itinerary.id) }) {
                Icon(Icons.Default.NotificationsActive, contentDescription = null)
                Text(stringResource(Res.string.reminder_at, clockTime(existing)), modifier = Modifier.padding(start = 8.dp))
            }
        } else {
            OutlinedButton(onClick = { menuOpen = true }) {
                Icon(Icons.Outlined.Notifications, contentDescription = null)
                Text(stringResource(Res.string.remind_me), modifier = Modifier.padding(start = 8.dp))
            }
        }
        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
            LeadTimes.forEach { minutes ->
                DropdownMenuItem(
                    text = { Text(stringResource(Res.string.reminder_before, minutes)) },
                    onClick = {
                        menuOpen = false
                        schedule(minutes)
                    },
                )
            }
        }
    }
}
