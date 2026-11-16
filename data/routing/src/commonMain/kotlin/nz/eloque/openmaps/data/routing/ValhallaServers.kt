package nz.eloque.openmaps.data.routing

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import nz.eloque.openmaps.core.model.TransportMode
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

/**
 * The Valhalla servers for each mode, in the order to ask them: [car] and then [active] for driving, [active] alone
 * for walking and cycling. Requests to [active] are spaced at least [activeInterval] apart, as public servers ask.
 */
internal class ValhallaServers(
    private val car: () -> String?,
    private val active: () -> String?,
    private val activeInterval: Duration = 1.seconds,
) {
    private val spacing = Mutex()
    private var lastActive: TimeSource.Monotonic.ValueTimeMark? = null

    fun urls(mode: TransportMode): List<String> =
        (if (mode == TransportMode.CAR) listOf(car(), active()) else listOf(active())).mapNotNull(::clean).distinct()

    /** The first answer that is not null, asking each server of [mode] in turn. Throws the last failure when none answered. */
    suspend fun <T : Any> first(
        mode: TransportMode,
        request: suspend (url: String) -> T?,
    ): T? {
        var failure: Exception? = null
        for (url in urls(mode)) {
            try {
                at(url, request)?.let { return it }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                failure = e
            }
        }
        failure?.let { throw it }
        return null
    }

    /** Runs [request] against [url], waiting first when it is the spaced server. */
    suspend fun <T> at(
        url: String,
        request: suspend (url: String) -> T,
    ): T {
        if (url == clean(active())) {
            spacing.withLock {
                lastActive?.elapsedNow()?.let { if (it < activeInterval) delay(activeInterval - it) }
                lastActive = TimeSource.Monotonic.markNow()
            }
        }
        return request(url)
    }

    private fun clean(url: String?) = url?.takeIf { it.isNotBlank() }?.trimEnd('/')
}
