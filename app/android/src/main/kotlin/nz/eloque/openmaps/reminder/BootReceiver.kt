package nz.eloque.openmaps.reminder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import nz.eloque.openmaps.core.platform.ReminderScheduler
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/** Sets reminder alarms again after a reboot or an app update, both of which remove them. */
class BootReceiver :
    BroadcastReceiver(),
    KoinComponent {
    private val scheduler: ReminderScheduler by inject()

    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED && intent.action != Intent.ACTION_MY_PACKAGE_REPLACED) return
        (scheduler as? AndroidReminderScheduler)?.restore()
    }
}
