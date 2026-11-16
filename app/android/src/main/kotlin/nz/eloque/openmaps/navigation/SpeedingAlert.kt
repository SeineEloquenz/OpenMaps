package nz.eloque.openmaps.navigation

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import nz.eloque.openmaps.core.model.Navigator
import nz.eloque.openmaps.core.model.SettingsStore
import kotlin.math.PI
import kotlin.math.min
import kotlin.math.sin

/**
 * Plays a short rising two-tone sound when a trip starts going clearly above the speed limit, if the user turned the
 * warning on. It plays as navigation guidance, so the ringer switch does not silence it.
 */
class SpeedingAlert(
    navigator: Navigator,
    settings: SettingsStore,
    scope: CoroutineScope,
) {
    private var lastSound = 0L
    private val samples = pling()

    init {
        scope.launch {
            combine(navigator.state, settings.speedingAlert) { state, enabled -> enabled && state?.speeding == true }
                .distinctUntilChanged()
                .collect { speeding -> if (speeding) play() }
        }
    }

    private fun play() {
        val now = System.currentTimeMillis()
        if (now - lastSound < PAUSE_MILLIS) return
        lastSound = now
        val track =
            AudioTrack
                .Builder()
                .setAudioAttributes(
                    AudioAttributes
                        .Builder()
                        .setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build(),
                ).setAudioFormat(
                    AudioFormat
                        .Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(SAMPLE_RATE)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build(),
                ).setTransferMode(AudioTrack.MODE_STATIC)
                .setBufferSizeInBytes(samples.size * 2)
                .build()
        track.write(samples, 0, samples.size)
        track.setNotificationMarkerPosition(samples.size)
        track.setPlaybackPositionUpdateListener(
            object : AudioTrack.OnPlaybackPositionUpdateListener {
                override fun onMarkerReached(track: AudioTrack) = track.release()

                override fun onPeriodicNotification(track: AudioTrack) = Unit
            },
        )
        track.play()
    }

    private companion object {
        const val PAUSE_MILLIS = 20_000L
        const val SAMPLE_RATE = 44_100
        const val TONE_SECONDS = 0.14
        const val FADE_SECONDS = 0.01
        const val LOW_HZ = 880.0
        const val HIGH_HZ = 1320.0
        const val AMPLITUDE = 0.6

        fun pling(): ShortArray {
            val perTone = (SAMPLE_RATE * TONE_SECONDS).toInt()
            val fade = (SAMPLE_RATE * FADE_SECONDS).toInt()
            return ShortArray(perTone * 2) { i ->
                val hz = if (i < perTone) LOW_HZ else HIGH_HZ
                val inTone = i % perTone
                val envelope = min(1.0, min(inTone, perTone - inTone).toDouble() / fade)
                (sin(2 * PI * hz * i / SAMPLE_RATE) * envelope * AMPLITUDE * Short.MAX_VALUE).toInt().toShort()
            }
        }
    }
}
