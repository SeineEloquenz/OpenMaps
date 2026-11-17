package nz.eloque.openmaps.ui.tracks

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import nz.eloque.openmaps.core.model.LocationFix
import nz.eloque.openmaps.core.model.LocationUpdates
import nz.eloque.openmaps.core.model.Track
import nz.eloque.openmaps.core.model.TrackPoint
import nz.eloque.openmaps.core.model.TrackStore
import nz.eloque.openmaps.ui.components.formatDate
import nz.eloque.openmaps.ui.resources.Res
import nz.eloque.openmaps.ui.resources.recorded_track
import org.jetbrains.compose.resources.getString
import kotlin.time.Clock
import kotlin.time.Instant

data class Recording(
    val startedAt: Instant,
    val points: List<TrackPoint>,
) {
    val distanceMeters: Double
        get() = Track(0, "", startedAt, points).distanceMeters
}

/** Records the user's way as a track, one recording at a time, for as long as the app runs. */
class TrackRecorder(
    private val location: LocationUpdates,
    private val store: TrackStore,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main),
    private val now: () -> Instant = { Clock.System.now() },
) {
    private val _recording = MutableStateFlow<Recording?>(null)

    /** Null while not recording. */
    val recording: StateFlow<Recording?> = _recording.asStateFlow()

    private var job: Job? = null

    fun start() {
        if (job != null) return
        _recording.value = Recording(now(), emptyList())
        job = scope.launch { location.updates().catch { }.collect(::add) }
    }

    /** Saves what was recorded and returns its id, null when it was too short to keep. */
    suspend fun stop(): Long? {
        val recording = finish() ?: return null
        if (recording.points.size < 2) return null
        val date = recording.startedAt.toLocalDateTime(TimeZone.currentSystemDefault())
        val time = "${date.hour.toString().padStart(2, '0')}:${date.minute.toString().padStart(2, '0')}"
        return store.save(getString(Res.string.recorded_track, formatDate(date.date), time), recording.points, recording.startedAt)
    }

    fun discard() {
        finish()
    }

    private fun finish(): Recording? {
        job?.cancel()
        job = null
        return _recording.value.also { _recording.value = null }
    }

    private fun add(fix: LocationFix) {
        if ((fix.accuracyMeters ?: 0f) > MAX_ACCURACY_METERS) return
        val point = TrackPoint(fix.location, fix.altitude, Instant.fromEpochMilliseconds(fix.timeMillis))
        _recording.update { current ->
            val last = current?.points?.lastOrNull()
            when {
                current == null -> null
                last != null && last.location.distanceTo(point.location) < MIN_STEP_METERS -> current
                else -> current.copy(points = current.points + point)
            }
        }
    }
}

private const val MAX_ACCURACY_METERS = 30f

// Keeps GPS jitter while standing still out of the track.
private const val MIN_STEP_METERS = 5.0
