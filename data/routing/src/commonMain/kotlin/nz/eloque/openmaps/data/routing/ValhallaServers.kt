package nz.eloque.openmaps.data.routing

import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import nz.eloque.openmaps.core.model.TransportMode
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

/**
 * The Valhalla server for each mode: [car] for driving, [active] for walking and cycling. Requests to [active] are
 * spaced at least [activeInterval] apart, as public servers ask.
 */
internal class ValhallaServers(
    private val car: () -> String?,
    private val active: () -> String?,
    private val activeInterval: Duration = 1.seconds,
) {
    private val spacing = Mutex()
    private var lastActive: TimeSource.Monotonic.ValueTimeMark? = null

    fun url(mode: TransportMode): String? = (if (mode == TransportMode.CAR) car() else active())?.takeIf { it.isNotBlank() }?.trimEnd('/')

    suspend fun <T> call(
        mode: TransportMode,
        request: suspend (url: String) -> T,
    ): T {
        val url = checkNotNull(url(mode)) { "No Valhalla server set for $mode" }
        if (mode != TransportMode.CAR) {
            spacing.withLock {
                lastActive?.elapsedNow()?.let { if (it < activeInterval) delay(activeInterval - it) }
                lastActive = TimeSource.Monotonic.markNow()
            }
        }
        return request(url)
    }
}
