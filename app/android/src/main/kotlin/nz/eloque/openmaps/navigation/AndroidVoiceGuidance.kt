package nz.eloque.openmaps.navigation

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.VibrationEffect
import android.os.Vibrator
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import nz.eloque.openmaps.core.model.Announcement
import nz.eloque.openmaps.core.model.VoiceGuidance
import nz.eloque.openmaps.core.model.VoiceStatus
import java.util.Locale

/**
 * Speaks announcements through the platform speech engine, ducking other audio meanwhile, and vibrates for urgent ones.
 * Holds the engine only while [active].
 */
class AndroidVoiceGuidance(
    private val context: Context,
    active: Flow<Boolean>,
    announcements: Flow<Announcement>,
    scope: CoroutineScope,
) : VoiceGuidance {
    private val preferences = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
    private val _muted = MutableStateFlow(preferences.getBoolean(MUTED, false))
    override val muted: StateFlow<Boolean> = _muted.asStateFlow()
    private val _status = MutableStateFlow(VoiceStatus.READY)
    override val status: StateFlow<VoiceStatus> = _status.asStateFlow()

    private val audio = context.getSystemService(AudioManager::class.java)
    private val attributes =
        AudioAttributes
            .Builder()
            .setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
            .build()
    private val focus =
        AudioFocusRequest
            .Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
            .setAudioAttributes(attributes)
            .build()
    private var engine: TextToSpeech? = null
    private var ready = false

    init {
        scope.launch {
            active.collect { running -> if (running) open() else close() }
        }
        scope.launch {
            announcements.collect {
                if (it.urgent) vibrate()
                speak(it)
            }
        }
    }

    override fun setMuted(muted: Boolean) {
        _muted.value = muted
        preferences.edit().putBoolean(MUTED, muted).apply()
        if (muted) engine?.stop()
    }

    private fun open() {
        if (engine != null) return
        _status.value = VoiceStatus.READY
        engine =
            TextToSpeech(context) { result ->
                ready = result == TextToSpeech.SUCCESS
                if (!ready) _status.value = VoiceStatus.NO_ENGINE
            }.apply {
                setAudioAttributes(attributes)
                setOnUtteranceProgressListener(
                    object : UtteranceProgressListener() {
                        override fun onStart(utteranceId: String?) = Unit

                        override fun onDone(utteranceId: String?) {
                            audio.abandonAudioFocusRequest(focus)
                        }

                        @Deprecated("Deprecated in the platform API")
                        override fun onError(utteranceId: String?) {
                            audio.abandonAudioFocusRequest(focus)
                        }
                    },
                )
            }
    }

    private fun vibrate() {
        context.getSystemService(Vibrator::class.java)?.vibrate(VibrationEffect.createWaveform(URGENT_PATTERN, -1))
    }

    private fun close() {
        engine?.shutdown()
        engine = null
        ready = false
    }

    private fun speak(announcement: Announcement) {
        val tts = engine ?: return
        if (!ready || _muted.value) return
        val locale = announcement.language?.let(Locale::forLanguageTag) ?: Locale.getDefault()
        val support = tts.setLanguage(locale)
        if (support == TextToSpeech.LANG_MISSING_DATA || support == TextToSpeech.LANG_NOT_SUPPORTED) {
            _status.value = VoiceStatus.NO_LANGUAGE
            return
        }
        _status.value = VoiceStatus.READY
        audio.requestAudioFocus(focus)
        tts.speak(announcement.text, TextToSpeech.QUEUE_FLUSH, null, UTTERANCE)
    }

    private companion object {
        const val UTTERANCE = "navigation"
        const val PREFERENCES = "voice"
        const val MUTED = "muted"
        val URGENT_PATTERN = longArrayOf(0, 300, 150, 300)
    }
}
