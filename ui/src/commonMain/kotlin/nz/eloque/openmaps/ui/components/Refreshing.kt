package nz.eloque.openmaps.ui.components

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/** Loads, then reloads every [interval]. A failed reload keeps the last loaded value. */
fun <T> refreshing(
    interval: Duration = 30.seconds,
    load: suspend () -> T,
): Flow<LoadState<T>> =
    flow {
        emit(LoadState.Loading)
        var last: LoadState.Loaded<T>? = null
        while (true) {
            try {
                last = LoadState.Loaded(load())
                emit(last)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                emit(last ?: LoadState.Failed)
            }
            delay(interval)
        }
    }

suspend fun <T> loadOnce(load: suspend () -> T): LoadState<T> =
    try {
        LoadState.Loaded(load())
    } catch (e: CancellationException) {
        throw e
    } catch (_: Exception) {
        LoadState.Failed
    }
