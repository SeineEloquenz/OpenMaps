package nz.eloque.openmaps.tracks

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
import nz.eloque.openmaps.core.model.SettingsStore
import nz.eloque.openmaps.ui.components.formatDistance
import nz.eloque.openmaps.ui.tracks.TrackRecorder
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/** Keeps a track recording while the app is in the background, with its length and a stop button in a notification. */
class RecordingService :
    Service(),
    KoinComponent {
    private val recorder: TrackRecorder by inject()
    private val settings: SettingsStore by inject()
    private val scope = MainScope()

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL, getString(R.string.recording_channel), NotificationManager.IMPORTANCE_LOW),
        )
        try {
            startForeground(ID, notification(0.0), ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
        } catch (_: SecurityException) {
            stopSelf()
            return
        }
        scope.launch {
            recorder.recording
                .map { it?.distanceMeters }
                .distinctUntilChanged()
                .collect { meters -> if (meters == null) stopSelf() else manager.notify(ID, notification(meters)) }
        }
    }

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int,
    ): Int {
        if (intent?.action == ACTION_STOP) scope.launch { recorder.stop() }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun notification(meters: Double): Notification {
        val open =
            PendingIntent.getActivity(
                this,
                0,
                Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        val stop =
            PendingIntent.getService(
                this,
                1,
                Intent(this, RecordingService::class.java).setAction(ACTION_STOP),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        return Notification
            .Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(getString(R.string.recording_title))
            .setContentText(formatDistance(meters, settings.distanceUnit.value))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(open)
            .addAction(Notification.Action.Builder(null, getString(R.string.recording_stop), stop).build())
            .build()
    }

    companion object {
        private const val CHANNEL = "recording"
        private const val ID = 2
        private const val ACTION_STOP = "nz.eloque.openmaps.STOP_RECORDING"

        fun start(context: Context) {
            context.startForegroundService(Intent(context, RecordingService::class.java))
        }
    }
}
