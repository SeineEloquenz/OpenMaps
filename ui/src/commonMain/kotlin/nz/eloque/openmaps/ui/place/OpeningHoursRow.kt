package nz.eloque.openmaps.ui.place

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import nz.eloque.openmaps.core.model.OpenStatus
import nz.eloque.openmaps.core.model.OpeningHours
import nz.eloque.openmaps.ui.components.Weekdays
import nz.eloque.openmaps.ui.components.rememberNow
import nz.eloque.openmaps.ui.resources.Res
import nz.eloque.openmaps.ui.resources.closed_day
import nz.eloque.openmaps.ui.resources.closed_now
import nz.eloque.openmaps.ui.resources.closes_at
import nz.eloque.openmaps.ui.resources.open_24_hours
import nz.eloque.openmaps.ui.resources.open_now
import nz.eloque.openmaps.ui.resources.opening_hours
import nz.eloque.openmaps.ui.resources.opens_at
import nz.eloque.openmaps.ui.resources.opens_on
import org.jetbrains.compose.resources.stringResource

private val OpenColor = Color(0xFF2E9E5B)

/** Shows whether the place is open now and the week on tap. Text the parser does not understand is shown as is. */
@Composable
fun OpeningHoursRow(raw: String) {
    val hours = OpeningHours.parse(raw)
    val colors = ListItemDefaults.colors(containerColor = Color.Transparent)
    if (hours == null) {
        ListItem(
            headlineContent = { Text(raw) },
            overlineContent = { Text(stringResource(Res.string.opening_hours)) },
            leadingContent = { Icon(Icons.Default.Schedule, contentDescription = null) },
            colors = colors,
        )
        return
    }

    val now by rememberNow()
    val today = now.toLocalDateTime(TimeZone.currentSystemDefault()).dayOfWeek.ordinal
    var expanded by rememberSaveable { mutableStateOf(false) }

    ListItem(
        headlineContent = { OpenStatusText(hours) },
        overlineContent = { Text(stringResource(Res.string.opening_hours)) },
        leadingContent = { Icon(Icons.Default.Schedule, contentDescription = null) },
        trailingContent = { Icon(if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore, contentDescription = null) },
        colors = colors,
        modifier = Modifier.clickable { expanded = !expanded },
    )
    if (expanded) {
        Column(Modifier.fillMaxWidth().padding(start = 56.dp, bottom = 8.dp)) {
            hours.week.forEachIndexed { day, ranges ->
                Row(Modifier.padding(vertical = 2.dp)) {
                    Text(
                        stringResource(Weekdays[day]),
                        fontWeight = if (day == today) FontWeight.Bold else null,
                        modifier = Modifier.width(48.dp),
                    )
                    Text(
                        if (ranges.isEmpty()) {
                            stringResource(Res.string.closed_day)
                        } else {
                            ranges.joinToString(", ") { "${formatMinute(it.start)}–${formatMinute(it.end)}" }
                        },
                        fontWeight = if (day == today) FontWeight.Bold else null,
                    )
                }
            }
        }
    }
}

/** "Open" or "Closed" in colour, followed by when that changes. */
@Composable
fun OpenStatusText(
    hours: OpeningHours,
    modifier: Modifier = Modifier,
) {
    val now by rememberNow()
    val local = now.toLocalDateTime(TimeZone.currentSystemDefault())
    val today = local.dayOfWeek.ordinal
    val status = hours.status(today, local.hour * 60 + local.minute)
    Row(modifier) {
        Text(
            stringResource(if (status is OpenStatus.Open) Res.string.open_now else Res.string.closed_now),
            color = if (status is OpenStatus.Open) OpenColor else MaterialTheme.colorScheme.error,
            fontWeight = FontWeight.SemiBold,
        )
        statusDetail(status, today)?.let { Text(" · $it") }
    }
}

@Composable
private fun statusDetail(
    status: OpenStatus,
    today: Int,
): String? =
    when (status) {
        is OpenStatus.Open ->
            status.closesAt?.let { stringResource(Res.string.closes_at, formatMinute(it)) }
                ?: stringResource(Res.string.open_24_hours)
        is OpenStatus.Closed -> {
            val days = status.opensInDays
            val at = status.opensAt
            when {
                days == null || at == null -> null
                days == 0 -> stringResource(Res.string.opens_at, formatMinute(at))
                else -> stringResource(Res.string.opens_on, stringResource(Weekdays[(today + days) % 7]), formatMinute(at))
            }
        }
    }

private fun formatMinute(minute: Int): String {
    val wrapped = minute % 1440
    return "${(wrapped / 60).toString().padStart(2, '0')}:${(wrapped % 60).toString().padStart(2, '0')}"
}
