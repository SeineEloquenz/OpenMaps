package nz.eloque.openmaps.watch

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.WorkerParameters
import nz.eloque.openmaps.core.model.TransitRepository
import nz.eloque.openmaps.core.model.TripWatchStore
import nz.eloque.openmaps.core.model.TripWatcher
import nz.eloque.openmaps.core.model.check
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import kotlin.time.Clock

class TripCheckWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params),
    KoinComponent {
    private val store: TripWatchStore by inject()
    private val transit: TransitRepository by inject()
    private val watcher: TripWatcher by inject()

    override suspend fun doWork(): Result {
        val id = inputData.getString(KEY_ID) ?: return Result.success()
        val trip = store.trip(id) ?: return Result.success()
        val (updated, changes) = trip.check(transit, Clock.System.now())
        store.save(updated)
        if (changes.isNotEmpty()) notifyChanges(applicationContext, updated, changes)
        // The running work holds the unique name, so the next check must append rather than replace it.
        val more = (watcher as WorkManagerTripWatcher).schedule(updated, ExistingWorkPolicy.APPEND_OR_REPLACE)
        if (!more) store.delete(id)
        return Result.success()
    }

    companion object {
        const val KEY_ID = "id"
    }
}
