package nz.eloque.openmaps.ui.directions

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.dp
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atTime
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import nz.eloque.openmaps.ui.components.dayTime
import nz.eloque.openmaps.ui.resources.Res
import nz.eloque.openmaps.ui.resources.arrive
import nz.eloque.openmaps.ui.resources.arrive_by
import nz.eloque.openmaps.ui.resources.cancel
import nz.eloque.openmaps.ui.resources.depart
import nz.eloque.openmaps.ui.resources.depart_at
import nz.eloque.openmaps.ui.resources.leave_now
import nz.eloque.openmaps.ui.resources.ok
import org.jetbrains.compose.resources.stringResource
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant

@Composable
fun TimeChoiceRow(
    choice: TimeChoice,
    onChoose: (TimeChoice) -> Unit,
) {
    var picking by remember { mutableStateOf<Boolean?>(null) }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterChip(
            selected = choice == TimeChoice.Now,
            onClick = { onChoose(TimeChoice.Now) },
            label = { Text(stringResource(Res.string.leave_now)) },
        )
        FilterChip(
            selected = choice is TimeChoice.DepartAt,
            onClick = { picking = false },
            label = {
                Text(
                    (choice as? TimeChoice.DepartAt)?.let { stringResource(Res.string.depart_at, dayTime(it.time)) }
                        ?: stringResource(Res.string.depart),
                )
            },
        )
        FilterChip(
            selected = choice is TimeChoice.ArriveBy,
            onClick = { picking = true },
            label = {
                Text(
                    (choice as? TimeChoice.ArriveBy)?.let { stringResource(Res.string.arrive_by, dayTime(it.time)) }
                        ?: stringResource(Res.string.arrive),
                )
            },
        )
    }
    picking?.let { arriveBy ->
        TimePickerDialog(
            onDismiss = { picking = null },
            onPick = { time ->
                picking = null
                onChoose(if (arriveBy) TimeChoice.ArriveBy(time) else TimeChoice.DepartAt(time))
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimePickerDialog(
    onDismiss: () -> Unit,
    onPick: (Instant) -> Unit,
) {
    val zone = TimeZone.currentSystemDefault()
    val now = Clock.System.now().toLocalDateTime(zone)
    val state = rememberTimePickerState(initialHour = now.hour, initialMinute = now.minute)
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = { onPick(nextOccurrence(LocalTime(state.hour, state.minute), zone)) }) {
                Text(stringResource(Res.string.ok))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(Res.string.cancel)) } },
        text = { TimePicker(state) },
    )
}

/** Today at [time], or tomorrow when that is more than an hour ago. */
private fun nextOccurrence(
    time: LocalTime,
    zone: TimeZone,
): Instant {
    val now = Clock.System.now()
    val today = now.toLocalDateTime(zone).date.atTime(time).toInstant(zone)
    return if (today < now - 1.hours) today + 1.days else today
}
