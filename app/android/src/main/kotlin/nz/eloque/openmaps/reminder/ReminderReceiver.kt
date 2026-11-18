package nz.eloque.openmaps.reminder

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import nz.eloque.openmaps.MainActivity
import nz.eloque.openmaps.R
import nz.eloque.openmaps.core.platform.ReminderScheduler
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class ReminderReceiver :
    BroadcastReceiver(),
    KoinComponent {
    private val scheduler: ReminderScheduler by inject()

    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        val id = intent.getStringExtra(EXTRA_ID) ?: return
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL, context.getString(R.string.reminders_channel), NotificationManager.IMPORTANCE_HIGH),
        )
        val open =
            PendingIntent.getActivity(
                context,
                0,
                Intent(context, MainActivity::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        val notification =
            Notification
                .Builder(context, CHANNEL)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(intent.getStringExtra(EXTRA_TITLE))
                .setContentText(intent.getStringExtra(EXTRA_TEXT))
                .setCategory(Notification.CATEGORY_REMINDER)
                .setContentIntent(open)
                .setAutoCancel(true)
                .build()
        manager.notify(id.hashCode(), notification)
        (scheduler as? AndroidReminderScheduler)?.forget(id)
    }

    companion object {
        const val EXTRA_ID = "id"
        const val EXTRA_TITLE = "title"
        const val EXTRA_TEXT = "text"
        private const val CHANNEL = "reminders"
    }
}
