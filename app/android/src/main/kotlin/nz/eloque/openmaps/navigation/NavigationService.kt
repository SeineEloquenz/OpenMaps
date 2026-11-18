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
import androidx.core.app.NotificationCompat
import androidx.core.graphics.drawable.IconCompat
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import nz.eloque.openmaps.MainActivity
import nz.eloque.openmaps.R
import nz.eloque.openmaps.core.model.Leg
import nz.eloque.openmaps.core.model.ManeuverType
import nz.eloque.openmaps.core.model.NavigationState
import nz.eloque.openmaps.core.model.NavigationStatus
import nz.eloque.openmaps.core.model.Navigator
import nz.eloque.openmaps.core.model.SettingsStore
import nz.eloque.openmaps.core.model.TransportMode
import nz.eloque.openmaps.core.model.TripGuidance
import nz.eloque.openmaps.core.model.TripGuide
import nz.eloque.openmaps.core.model.TripStage
import nz.eloque.openmaps.core.model.VoiceGuidance
import nz.eloque.openmaps.ui.components.clockTime
import nz.eloque.openmaps.ui.components.formatDistance
import nz.eloque.openmaps.ui.guidance.ActiveTrip
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds

/** What the notification shows of a running trip. */
private data class Guidance(
    val title: String,
    val text: String?,
    /** The few characters shown in the status bar, such as the distance to the next turn. */
    val chip: String?,
    val icon: Int,
    val tracker: Int,
    /** Lengths along the trip, each with its colour or null for the default. */
    val segments: List<Pair<Int, Int?>>,
    val progress: Int,
)

/**
 * Keeps a running trip going while the app is in the background, with the next instruction in a notification.
 * On Android 16 and later the notification is a Live Update with the trip's progress and the next turn in the status bar.
 */
class NavigationService :
    Service(),
    KoinComponent {
    private val navigator: Navigator by inject()
    private val guide: TripGuide by inject()
    private val voice: VoiceGuidance by inject()
    private val activeTrip: ActiveTrip by inject()
    private val settings: SettingsStore by inject()
    private val scope = MainScope()

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        val manager = getSystemService(NotificationManager::class.java)
        manager.deleteNotificationChannel(OLD_CHANNEL)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL, getString(R.string.navigation_channel), NotificationManager.IMPORTANCE_DEFAULT).apply {
                setSound(null, null)
                enableVibration(false)
            },
        )
        try {
            startForeground(ID, notification(current(), voice.muted.value), ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
        } catch (_: SecurityException) {
            // Without location permission, as on a simulated trip, the trip runs in the foreground only.
            stopSelf()
            return
        }
        val ticks =
            flow {
                while (true) {
                    emit(Unit)
                    delay(TICK)
                }
            }
        scope.launch {
            combine(navigator.state, guide.state, ticks) { street, transit, _ -> street?.let(::guidance) ?: transit?.let(::guidance) }
                .combine(voice.muted) { guidance, muted -> guidance to muted }
                .distinctUntilChanged()
                .collect { (guidance, muted) ->
                    if (guidance == null) {
                        stopSelf()
                    } else {
                        manager.notify(ID, notification(guidance, muted))
                    }
                }
        }
    }

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int,
    ): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                navigator.stop()
                guide.stop()
                activeTrip.key = null
            }
            ACTION_MUTE -> voice.setMuted(!voice.muted.value)
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun current(): Guidance? = navigator.state.value?.let(::guidance) ?: guide.state.value?.let(::guidance)

    private fun notification(
        guidance: Guidance?,
        muted: Boolean,
    ): Notification {
        val open =
            PendingIntent.getActivity(
                this,
                0,
                Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        val builder =
            NotificationCompat
                .Builder(this, CHANNEL)
                .setCategory(Notification.CATEGORY_NAVIGATION)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setSilent(true)
                .setContentIntent(open)
                .setRequestPromotedOngoing(true)
                .addAction(
                    if (muted) R.drawable.car_volume_off else R.drawable.car_volume_up,
                    getString(if (muted) R.string.navigation_unmute else R.string.navigation_mute),
                    action(ACTION_MUTE),
                ).addAction(R.drawable.car_close, getString(R.string.navigation_stop), action(ACTION_STOP))
        if (guidance == null) {
            return builder.setSmallIcon(R.drawable.car_navigation).setContentTitle(getString(R.string.navigation_channel)).build()
        }
        builder
            .setSmallIcon(guidance.icon)
            .setContentTitle(guidance.title)
            .setContentText(guidance.text)
        guidance.chip?.let(builder::setShortCriticalText)
        val style =
            NotificationCompat
                .ProgressStyle()
                .setStyledByProgress(true)
                .setProgressTrackerIcon(IconCompat.createWithResource(this, guidance.tracker))
        guidance.segments.forEach { (length, color) ->
            style.addProgressSegment(NotificationCompat.ProgressStyle.Segment(length).apply { color?.let(::setColor) })
        }
        var position = 0
        guidance.segments.dropLast(1).forEach { (length, _) ->
            position += length
            style.addProgressPoint(NotificationCompat.ProgressStyle.Point(position))
        }
        return builder.setStyle(style.setProgress(guidance.progress)).build()
    }

    private fun action(name: String) =
        PendingIntent.getService(
            this,
            name.hashCode(),
            Intent(this, NavigationService::class.java).setAction(name),
            PendingIntent.FLAG_IMMUTABLE,
        )

    private fun guidance(trip: TripGuidance): Guidance {
        val legs = trip.itinerary.legs
        val leg = legs.getOrNull(trip.progress.legIndex)
        val next = legs.getOrNull(trip.progress.legIndex + 1) as? Leg.Transit
        val (title, text, chip) =
            when (trip.progress.stage) {
                TripStage.WALK ->
                    if (next == null) {
                        Triple(getString(R.string.guide_walk_end), null, null)
                    } else {
                        val leaves = clockTime(next.start.actual)
                        Triple(
                            getString(R.string.guide_walk_to, next.from.name),
                            getString(R.string.guide_leaves, next.line.label, leaves),
                            leaves,
                        )
                    }
                TripStage.WAIT -> {
                    val vehicle = leg as Leg.Transit
                    val leaves = clockTime(vehicle.start.actual)
                    Triple(
                        getString(R.string.guide_wait, vehicle.line.label, vehicle.headsign.orEmpty()),
                        getString(R.string.guide_leaves, vehicle.line.label, leaves),
                        leaves,
                    )
                }
                TripStage.RIDE -> {
                    val vehicle = leg as Leg.Transit
                    val stops = trip.progress.stopsLeft
                    Triple(
                        getString(R.string.guide_ride, vehicle.to.name),
                        resources.getQuantityString(R.plurals.guide_stops_left, stops, stops, clockTime(vehicle.end.actual)),
                        resources.getQuantityString(R.plurals.guide_stops_short, stops, stops),
                    )
                }
                TripStage.ARRIVED -> Triple(getString(R.string.navigation_arrived), null, null)
            }
        val icon =
            when (trip.progress.stage) {
                TripStage.WALK -> R.drawable.mode_walk
                TripStage.WAIT, TripStage.RIDE -> leg?.mode?.icon ?: R.drawable.car_navigation
                TripStage.ARRIVED -> R.drawable.car_flag
            }
        // Each segment runs from a leg's departure to the next one's, so waiting belongs to the leg before.
        val bounds = legs.map { it.start.actual } + listOfNotNull(legs.lastOrNull()?.end?.actual)
        val segments =
            bounds.zipWithNext().zip(legs) { (start, end), part ->
                (end - start).inWholeSeconds.toInt().coerceAtLeast(1) to (part as? Leg.Transit)?.line?.color?.toInt()
            }
        val elapsed = bounds.firstOrNull()?.let { (Clock.System.now() - it).inWholeSeconds.toInt() } ?: 0
        return Guidance(
            title = title,
            text = text,
            chip = chip,
            icon = icon,
            tracker = leg?.mode?.icon ?: R.drawable.mode_walk,
            segments = segments.ifEmpty { listOf(1 to null) },
            progress = elapsed.coerceIn(0, segments.sumOf { it.first }),
        )
    }

    private fun guidance(state: NavigationState): Guidance {
        val unit = settings.distanceUnit.value
        val minutes = ((state.remainingTime.inWholeSeconds + 30) / 60).coerceAtLeast(1)
        val arrival = clockTime(Clock.System.now() + state.remainingTime)
        val left = getString(R.string.navigation_left, formatDistance(state.remainingMeters, unit), minutes, arrival)
        val instruction = state.instruction?.takeIf { state.status == NavigationStatus.NAVIGATING }
        val title =
            when (state.status) {
                NavigationStatus.ARRIVED -> getString(R.string.navigation_arrived)
                NavigationStatus.REROUTING -> getString(R.string.navigation_rerouting)
                NavigationStatus.OFF_ROUTE -> getString(R.string.navigation_off_route)
                NavigationStatus.NAVIGATING ->
                    instruction?.let { getString(R.string.navigation_next, formatDistance(state.metersToManeuver, unit), it.text) }
                        ?: getString(R.string.navigation_channel)
            }
        val icon =
            when (state.status) {
                NavigationStatus.ARRIVED -> ManeuverType.ARRIVE.icon
                NavigationStatus.NAVIGATING -> instruction?.maneuver?.icon ?: state.mode.icon
                else -> state.mode.icon
            }
        val total = (state.travelledMeters + state.remainingMeters).toInt().coerceAtLeast(1)
        val step = (total / PROGRESS_STEPS).coerceAtLeast(1)
        return Guidance(
            title = title,
            text = listOfNotNull(instruction?.secondary, left).joinToString(" · "),
            chip = instruction?.let { formatDistance(state.metersToManeuver, unit) },
            icon = icon,
            tracker = if (state.mode == TransportMode.WALK) R.drawable.mode_walk else state.mode.icon,
            segments = listOf(total to null),
            progress = (state.travelledMeters.toInt() / step * step).coerceIn(0, total),
        )
    }

    companion object {
        private const val CHANNEL = "guidance"

        // A channel keeps the importance it was created with, so the quieter one from earlier versions is replaced.
        private const val OLD_CHANNEL = "navigation"
        private const val ID = 1
        private const val ACTION_STOP = "nz.eloque.openmaps.STOP_NAVIGATION"
        private const val ACTION_MUTE = "nz.eloque.openmaps.MUTE_NAVIGATION"
        private val TICK = 30.seconds

        // Each notification update redraws the shade, so street progress moves in steps rather than with every fix.
        private const val PROGRESS_STEPS = 200

        fun start(context: Context) {
            context.startForegroundService(Intent(context, NavigationService::class.java))
        }
    }
}
