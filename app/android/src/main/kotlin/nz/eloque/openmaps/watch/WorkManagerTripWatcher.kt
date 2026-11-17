package nz.eloque.openmaps.watch

import android.app.NotificationManager
import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import nz.eloque.openmaps.core.model.TripWatchStore
import nz.eloque.openmaps.core.model.TripWatcher
import nz.eloque.openmaps.core.model.WatchedTrip
import nz.eloque.openmaps.core.model.nextCheck
import kotlin.time.Clock
import kotlin.time.toJavaDuration

/** One unique work chain per trip, each check enqueueing the next. WorkManager keeps them across reboots. */
class WorkManagerTripWatcher(
    private val context: Context,
    private val store: TripWatchStore,
    private val scope: CoroutineScope,
) : TripWatcher {
    private val work = WorkManager.getInstance(context)

    override val watched: StateFlow<Set<String>> =
        store.trips.map { trips -> trips.map { it.id }.toSet() }.stateIn(scope, SharingStarted.Eagerly, emptySet())

    override fun watch(trip: WatchedTrip) {
        scope.launch {
            store.save(trip)
            schedule(trip, ExistingWorkPolicy.REPLACE)
        }
    }

    override fun stop(id: String) {
        work.cancelUniqueWork(workName(id))
        context.getSystemService(NotificationManager::class.java).cancel(notificationId(id))
        scope.launch { store.delete(id) }
    }

    /** Returns false once the trip needs no more checks. */
    internal fun schedule(
        trip: WatchedTrip,
        policy: ExistingWorkPolicy,
    ): Boolean {
        val now = Clock.System.now()
        val next = trip.nextCheck(now) ?: return false
        val request =
            OneTimeWorkRequestBuilder<TripCheckWorker>()
                .setInitialDelay((next - now).toJavaDuration())
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .setInputData(workDataOf(TripCheckWorker.KEY_ID to trip.id))
                .build()
        work.enqueueUniqueWork(workName(trip.id), policy, request)
        return true
    }

    private fun workName(id: String) = "trip-watch:$id"
}

internal fun notificationId(tripId: String) = "trip-watch:$tripId".hashCode()
