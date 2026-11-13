package nz.eloque.openmaps.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.produceState
import kotlinx.coroutines.delay
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

/** The current time, updated often enough for minute countdowns. */
@Composable
fun rememberNow(): State<Instant> =
    produceState(Clock.System.now()) {
        while (true) {
            delay(15.seconds)
            value = Clock.System.now()
        }
    }
