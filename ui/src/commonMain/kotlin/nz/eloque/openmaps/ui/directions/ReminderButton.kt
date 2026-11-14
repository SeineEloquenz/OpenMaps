package nz.eloque.openmaps.ui.directions

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import kotlinx.coroutines.launch
import nz.eloque.openmaps.core.model.Itinerary
import nz.eloque.openmaps.core.model.Leg
import nz.eloque.openmaps.core.platform.Reminder
import nz.eloque.openmaps.core.platform.ReminderScheduler
import nz.eloque.openmaps.ui.components.clockTime
import nz.eloque.openmaps.ui.components.rememberNotificationPermissionRequest
import nz.eloque.openmaps.ui.resources.Res
import nz.eloque.openmaps.ui.resources.exact_timing_allow
import nz.eloque.openmaps.ui.resources.exact_timing_skip
import nz.eloque.openmaps.ui.resources.exact_timing_text
import nz.eloque.openmaps.ui.resources.exact_timing_title
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

/**
 * Schedules a notification some minutes before the time to leave. When one is set, a tap cancels it.
 * Exact timing is asked for first, since without it the system may deliver the reminder minutes late.
 */
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
    var awaitingExactTiming by rememberSaveable { mutableStateOf<Int?>(null) }

    fun schedule(leadMinutes: Int) {
        val at = itinerary.start - leadMinutes.minutes
        scope.launch {
            if (at <= Clock.System.now()) {
                snackbar.showSnackbar(getString(Res.string.reminder_too_late))
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
            snackbar.showSnackbar(
                if (scheduler.canScheduleExact) {
                    getString(Res.string.reminder_set, clockTime(at))
                } else {
                    getString(Res.string.reminder_may_be_late)
                },
            )
        }
    }

    fun start(leadMinutes: Int) {
        requestPermission { granted ->
            when {
                !granted -> scope.launch { snackbar.showSnackbar(getString(Res.string.reminder_needs_notifications)) }
                scheduler.canScheduleExact -> schedule(leadMinutes)
                else -> awaitingExactTiming = leadMinutes
            }
        }
    }

    // Coming back from the system settings with the permission granted finishes the pending reminder.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        val pending = awaitingExactTiming
        if (pending != null && scheduler.canScheduleExact) {
            awaitingExactTiming = null
            schedule(pending)
        }
    }

    awaitingExactTiming?.let { pending ->
        AlertDialog(
            onDismissRequest = { awaitingExactTiming = null },
            title = { Text(stringResource(Res.string.exact_timing_title)) },
            text = { Text(stringResource(Res.string.exact_timing_text)) },
            confirmButton = {
                TextButton(onClick = scheduler::openExactAlarmSettings) { Text(stringResource(Res.string.exact_timing_allow)) }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        awaitingExactTiming = null
                        schedule(pending)
                    },
                ) { Text(stringResource(Res.string.exact_timing_skip)) }
            },
        )
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
                        start(minutes)
                    },
                )
            }
        }
    }
}
