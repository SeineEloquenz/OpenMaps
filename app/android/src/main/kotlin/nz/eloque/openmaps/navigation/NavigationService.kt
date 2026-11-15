package nz.eloque.openmaps.navigation

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import nz.eloque.openmaps.MainActivity
import nz.eloque.openmaps.R
import nz.eloque.openmaps.core.model.NavigationState
import nz.eloque.openmaps.core.model.NavigationStatus
import nz.eloque.openmaps.core.model.Navigator
import nz.eloque.openmaps.core.model.SettingsStore
import nz.eloque.openmaps.ui.components.formatDistance
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/** Keeps a running trip going while the app is in the background, with the next instruction in a notification. */
class NavigationService :
    Service(),
    KoinComponent {
    private val navigator: Navigator by inject()
    private val settings: SettingsStore by inject()
    private val scope = MainScope()

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL, getString(R.string.navigation_channel), NotificationManager.IMPORTANCE_LOW),
        )
        try {
            startForeground(ID, notification(navigator.state.value), ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
        } catch (_: SecurityException) {
            // Without location permission, as on a simulated trip, the trip runs in the foreground only.
            stopSelf()
            return
        }
        scope.launch {
            navigator.state
                .map { it?.let(::text) }
                .distinctUntilChanged()
                .collect { text ->
                    if (text == null) {
                        stopSelf()
                    } else {
                        manager.notify(ID, notification(navigator.state.value))
                    }
                }
        }
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun notification(state: NavigationState?): Notification {
        val open =
            PendingIntent.getActivity(
                this,
                0,
                Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        val (title, body) = state?.let(::text) ?: (getString(R.string.navigation_channel) to null)
        return Notification
            .Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(body)
            .setCategory(Notification.CATEGORY_NAVIGATION)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(open)
            .build()
    }

    private fun text(state: NavigationState): Pair<String, String> {
        val unit = settings.distanceUnit.value
        val minutes = ((state.remainingTime.inWholeSeconds + 30) / 60).coerceAtLeast(1)
        val left = getString(R.string.navigation_left, formatDistance(state.remainingMeters, unit), minutes)
        val title =
            when (state.status) {
                NavigationStatus.ARRIVED -> getString(R.string.navigation_arrived)
                NavigationStatus.REROUTING -> getString(R.string.navigation_rerouting)
                NavigationStatus.OFF_ROUTE -> getString(R.string.navigation_off_route)
                NavigationStatus.NAVIGATING ->
                    state.instruction?.let { getString(R.string.navigation_next, formatDistance(state.metersToManeuver, unit), it.text) }
                        ?: getString(R.string.navigation_channel)
            }
        return title to left
    }

    companion object {
        private const val CHANNEL = "navigation"
        private const val ID = 1

        fun start(context: Context) {
            context.startForegroundService(Intent(context, NavigationService::class.java))
        }
    }
}
