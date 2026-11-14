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

/** Reminders are kept in preferences so [restore] can set their alarms again after the system dropped them. */
class AndroidReminderScheduler(
    private val context: Context,
) : ReminderScheduler {
    private val alarms = context.getSystemService(AlarmManager::class.java)
    private val prefs = context.getSharedPreferences("reminders", Context.MODE_PRIVATE)
    private val _scheduled = MutableStateFlow(loadPending().associate { it.id to it.at })

    override val scheduled: StateFlow<Map<String, Instant>> = _scheduled.asStateFlow()

    override val canScheduleExact: Boolean
        get() = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarms.canScheduleExactAlarms()

    override fun schedule(reminder: Reminder) {
        setAlarm(reminder)
        prefs
            .edit()
            .putLong(AT + reminder.id, reminder.at.toEpochMilliseconds())
            .putString(TITLE + reminder.id, reminder.title)
            .putString(TEXT + reminder.id, reminder.text)
            .apply()
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

    /** Sets the alarms of all pending reminders again. A reboot or force stop removes them from the system. */
    fun restore() {
        loadPending().forEach(::setAlarm)
    }

    internal fun forget(id: String) {
        forgetStored(id)
        _scheduled.update { it - id }
    }

    private fun setAlarm(reminder: Reminder) {
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

    /** Reminders whose time has passed are dropped. */
    private fun loadPending(): List<Reminder> {
        val now = Clock.System.now().toEpochMilliseconds()
        val all =
            prefs.all.mapNotNull { (key, value) ->
                val id = key.removePrefix(AT).takeIf { key.startsWith(AT) } ?: return@mapNotNull null
                val at = value as? Long ?: return@mapNotNull null
                Reminder(
                    id,
                    Instant.fromEpochMilliseconds(at),
                    prefs.getString(TITLE + id, "").orEmpty(),
                    prefs.getString(TEXT + id, "").orEmpty(),
                )
            }
        val (pending, past) = all.partition { it.at.toEpochMilliseconds() > now }
        past.forEach { forgetStored(it.id) }
        return pending
    }

    private fun forgetStored(id: String) {
        prefs
            .edit()
            .remove(AT + id)
            .remove(TITLE + id)
            .remove(TEXT + id)
            .apply()
    }

    private companion object {
        const val AT = "at:"
        const val TITLE = "title:"
        const val TEXT = "text:"
    }
}
