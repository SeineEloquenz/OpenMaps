package nz.eloque.openmaps.navigation

import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Handler
import android.os.Looper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import nz.eloque.openmaps.core.model.Navigator
import nz.eloque.openmaps.core.model.SettingsStore

/** Beeps when a trip starts going clearly above the speed limit, if the user turned the warning on. */
class SpeedingAlert(
    navigator: Navigator,
    settings: SettingsStore,
    scope: CoroutineScope,
) {
    private var lastBeep = 0L

    init {
        scope.launch {
            combine(navigator.state, settings.speedingAlert) { state, enabled -> enabled && state?.speeding == true }
                .distinctUntilChanged()
                .collect { speeding -> if (speeding) beep() }
        }
    }

    private fun beep() {
        val now = System.currentTimeMillis()
        if (now - lastBeep < PAUSE_MILLIS) return
        lastBeep = now
        val tone = ToneGenerator(AudioManager.STREAM_NOTIFICATION, VOLUME)
        tone.startTone(ToneGenerator.TONE_PROP_BEEP2, TONE_MILLIS)
        Handler(Looper.getMainLooper()).postDelayed({ tone.release() }, TONE_MILLIS + RELEASE_DELAY_MILLIS)
    }

    private companion object {
        const val PAUSE_MILLIS = 20_000L
        const val VOLUME = 90
        const val TONE_MILLIS = 300
        const val RELEASE_DELAY_MILLIS = 200L
    }
}
