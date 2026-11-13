package nz.eloque.openmaps.reminder

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import nz.eloque.openmaps.core.platform.Reminder
import nz.eloque.openmaps.core.platform.ReminderScheduler
import kotlin.time.Clock
import kotlin.time.Instant

/** Alarms do not survive a reboot. Reminders are short-lived, so they are not rescheduled. */
class AndroidReminderScheduler(
    private val context: Context,
) : ReminderScheduler {
    private val alarms = context.getSystemService(AlarmManager::class.java)
    private val prefs = context.getSharedPreferences("reminders", Context.MODE_PRIVATE)
    private val _scheduled = MutableStateFlow(loadPending())

    override val scheduled: StateFlow<Map<String, Instant>> = _scheduled.asStateFlow()

    override val canScheduleExact: Boolean
        get() = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarms.canScheduleExactAlarms()

    override fun schedule(reminder: Reminder) {
        val intent =
            Intent(context, ReminderReceiver::class.java)
                .putExtra(ReminderReceiver.EXTRA_ID, reminder.id)
                .putExtra(ReminderReceiver.EXTRA_TITLE, reminder.title)
                .putExtra(ReminderReceiver.EXTRA_TEXT, reminder.text)
        val millis = reminder.at.toEpochMilliseconds()
        if (canScheduleExact) {
            alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, millis, pendingIntent(reminder.id, intent))
        } else {
            alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, millis, pendingIntent(reminder.id, intent))
        }
        prefs.edit().putLong(reminder.id, millis).apply()
        _scheduled.update { it + (reminder.id to reminder.at) }
    }

    override fun cancel(id: String) {
        alarms.cancel(pendingIntent(id, Intent(context, ReminderReceiver::class.java)))
        forget(id)
    }

    override fun openExactAlarmSettings() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
        context.startActivity(
            Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:${context.packageName}"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }

    internal fun forget(id: String) {
        prefs.edit().remove(id).apply()
        _scheduled.update { it - id }
    }

    private fun pendingIntent(
        id: String,
        intent: Intent,
    ): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            id.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    private fun loadPending(): Map<String, Instant> {
        val now = Clock.System.now().toEpochMilliseconds()
        val all = prefs.all.mapNotNull { (id, value) -> (value as? Long)?.let { id to it } }
        val (pending, past) = all.partition { it.second > now }
        if (past.isNotEmpty()) {
            prefs.edit().apply { past.forEach { remove(it.first) } }.apply()
        }
        return pending.associate { it.first to Instant.fromEpochMilliseconds(it.second) }
    }
}
