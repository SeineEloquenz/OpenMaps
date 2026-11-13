package nz.eloque.openmaps.core.platform

import kotlinx.coroutines.flow.StateFlow
import kotlin.time.Instant

data class Reminder(
    val id: String,
    val at: Instant,
    val title: String,
    val text: String,
)

/** Shows a local notification at a set time, without any server. */
interface ReminderScheduler {
    /** Pending reminders by id. */
    val scheduled: StateFlow<Map<String, Instant>>

    /** False when the system may deliver reminders a few minutes late until the user allows exact alarms. */
    val canScheduleExact: Boolean

    fun schedule(reminder: Reminder)

    fun cancel(id: String)

    fun openExactAlarmSettings()
}
