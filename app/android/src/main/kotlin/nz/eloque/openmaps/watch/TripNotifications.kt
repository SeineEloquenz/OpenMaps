package nz.eloque.openmaps.watch

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.text.format.DateFormat
import nz.eloque.openmaps.MainActivity
import nz.eloque.openmaps.R
import nz.eloque.openmaps.core.model.TripChange
import nz.eloque.openmaps.core.model.TripWatcher
import nz.eloque.openmaps.core.model.WatchedTrip
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.util.Date
import kotlin.time.Duration
import kotlin.time.Instant

internal fun notifyChanges(
    context: Context,
    trip: WatchedTrip,
    changes: List<TripChange>,
) {
    val manager = context.getSystemService(NotificationManager::class.java)
    manager.createNotificationChannel(
        NotificationChannel(CHANNEL, context.getString(R.string.trip_updates_channel), NotificationManager.IMPORTANCE_HIGH),
    )
    val open =
        PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    val stop =
        PendingIntent.getBroadcast(
            context,
            notificationId(trip.id),
            Intent(context, StopWatchingReceiver::class.java).putExtra(StopWatchingReceiver.EXTRA_ID, trip.id),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    val text = changes.joinToString("\n") { context.describe(it) }
    val notification =
        Notification
            .Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.title(changes))
            .setContentText(text)
            .setStyle(Notification.BigTextStyle().bigText(text))
            .setSubText(trip.destination)
            .setCategory(Notification.CATEGORY_STATUS)
            .setContentIntent(open)
            .addAction(Notification.Action.Builder(null, context.getString(R.string.trip_updates_stop), stop).build())
            .setAutoCancel(true)
            .build()
    manager.notify(notificationId(trip.id), notification)
}

private fun Context.title(changes: List<TripChange>): String {
    val lines = changes.map { it.line }.distinct().joinToString(", ")
    val cancelled = changes.filterIsInstance<TripChange.Cancelled>()
    val departure = changes.filterIsInstance<TripChange.Departure>()
    val arrival = changes.filterIsInstance<TripChange.Arrival>()
    val platform = changes.any { it is TripChange.Platform }
    val res =
        when {
            cancelled.any { it.cancelled } -> R.string.trip_updates_cancelled
            cancelled.isNotEmpty() -> R.string.trip_updates_restored
            platform && (departure.isNotEmpty() || arrival.isNotEmpty()) -> R.string.trip_updates_changes
            departure.isNotEmpty() && arrival.isNotEmpty() -> R.string.trip_updates_delays
            departure.any { it.delay.isPositive() } -> R.string.trip_updates_delayed_departure
            departure.any { it.delay.isNegative() } -> R.string.trip_updates_earlier_departure
            arrival.any { it.delay.isPositive() } -> R.string.trip_updates_delayed_arrival
            platform -> R.string.trip_updates_platform
            else -> R.string.trip_updates_changes
        }
    return getString(res, lines)
}

private fun Context.describe(change: TripChange): String =
    when (change) {
        is TripChange.Cancelled ->
            getString(if (change.cancelled) R.string.trip_updates_cancelled else R.string.trip_updates_restored, change.line)
        is TripChange.Departure -> getString(R.string.trip_updates_leaves, change.stop, clock(change.time), offset(change.delay))
        is TripChange.Arrival -> getString(R.string.trip_updates_arrives, change.stop, clock(change.time), offset(change.delay))
        is TripChange.Platform ->
            getString(
                if (change.departure) R.string.trip_updates_departure_platform else R.string.trip_updates_arrival_platform,
                change.stop,
                change.platform,
            )
    }

private fun Context.clock(time: Instant) = DateFormat.getTimeFormat(this).format(Date(time.toEpochMilliseconds()))

private fun Context.offset(delay: Duration): String {
    val minutes = delay.inWholeMinutes
    return when {
        minutes > 0 -> "+$minutes"
        minutes < 0 -> "$minutes"
        else -> getString(R.string.trip_updates_on_time)
    }
}

private const val CHANNEL = "trip_updates"

class StopWatchingReceiver :
    BroadcastReceiver(),
    KoinComponent {
    private val watcher: TripWatcher by inject()

    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        intent.getStringExtra(EXTRA_ID)?.let(watcher::stop)
    }

    companion object {
        const val EXTRA_ID = "id"
    }
}
