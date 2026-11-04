package app.binelik.ai

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import app.binelik.data.Gender
import kotlinx.coroutines.launch
import java.util.Locale

/**
 * Voice with a text AI (DeepSeek): the phone's recogniser hears, the AI answers in text, and the
 * phone's own voice speaks it. Turn by turn (the phone doesn't listen while it speaks).
 */
class PhoneVoice(
    private val context: Context,
    private val languageTag: String,
    private val gender: Gender,
    private val voiceIndex: Int?,
    /** Called with what the user said; returns the reply, or null to stay silent (e.g. safety stop). */
    private val answer: suspend (String) -> String?,
    private val scope: kotlinx.coroutines.CoroutineScope,
    private val onEvent: (LiveEvent) -> Unit,
    private val onClosed: (error: String?) -> Unit,
) : Conversation {
    private val main = Handler(Looper.getMainLooper())
    private var tts: TextToSpeech? = null
    private var recognizer: SpeechRecognizer? = null
    @Volatile private var closed = false
    @Volatile private var speaking = false

    override var micMuted = false
        set(v) { field = v; val keep = pttHold; main.post { if (v) { if (keep) recognizer?.stopListening() else recognizer?.cancel() } else if (!speaking) listen() } }
    override var voiceMuted = false
        set(v) { field = v; if (v) tts?.stop() }

    /** Push to talk let go: stop listening but keep what was heard (stopListening, not cancel). */
    override fun release() {
        main.post { pttHold = true; micMuted = true; pttHold = false }
    }
    private var pttHold = false

    fun start() {
        if (!SpeechRecognizer.isRecognitionAvailable(context)) { onClosed("no-recognizer"); return }
        tts = TextToSpeech(context) { status ->
            if (status != TextToSpeech.SUCCESS) { finish("no-tts"); return@TextToSpeech }
            val t = tts ?: return@TextToSpeech
            setup(t, Locale.forLanguageTag(languageTag), gender, voiceIndex)
            t.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(id: String?) { speaking = true }
                override fun onDone(id: String?) { speaking = false; main.post { onEvent(LiveEvent.TurnComplete); listen() } }
                @Deprecated("old API") override fun onError(id: String?) { onDone(id) }
            })
            main.post {
                recognizer = SpeechRecognizer.createSpeechRecognizer(context).also { it.setRecognitionListener(listener) }
                onEvent(LiveEvent.Ready)
                listen()
            }
        }
    }

    private fun listen() {
        if (closed || micMuted || speaking) return
        val i = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE, languageTag)
            .putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        runCatching { recognizer?.startListening(i) }
    }

    private val listener = object : RecognitionListener {
        override fun onPartialResults(b: Bundle?) {
            b?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.let { onEvent(LiveEvent.HeardSoFar(it)) }
        }

        override fun onResults(b: Bundle?) {
            val text = b?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.trim().orEmpty()
            if (text.isEmpty()) { listen(); return }
            onEvent(LiveEvent.HeardSoFar(text))
            scope.launch {
                val reply = answer(text)
                if (closed) return@launch
                if (reply == null) { listen(); return@launch }
                onEvent(LiveEvent.Said(reply))
                if (voiceMuted) { onEvent(LiveEvent.TurnComplete); listen(); return@launch }
                speaking = true
                tts?.speak(reply, TextToSpeech.QUEUE_FLUSH, null, "r${System.nanoTime()}")
            }
        }

        override fun onError(error: Int) {
            if (closed) return
            when (error) {
                SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT, SpeechRecognizer.ERROR_RECOGNIZER_BUSY,
                SpeechRecognizer.ERROR_CLIENT -> main.postDelayed({ listen() }, 300)
                SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> finish("mic")
                else -> main.postDelayed({ listen() }, 1000)
            }
        }

        override fun onReadyForSpeech(p: Bundle?) = Unit
        override fun onBeginningOfSpeech() = Unit
        override fun onRmsChanged(v: Float) = Unit
        override fun onBufferReceived(b: ByteArray?) = Unit
        override fun onEndOfSpeech() = Unit
        override fun onEvent(t: Int, p: Bundle?) = Unit
    }

    private fun finish(error: String?) {
        if (closed) return
        closed = true
        main.post {
            runCatching { recognizer?.destroy() }; recognizer = null
            runCatching { tts?.stop(); tts?.shutdown() }; tts = null
        }
        onClosed(error)
    }

    override fun stop() = finish(null)

    override fun interrupt() {
        if (!speaking) return
        tts?.stop(); speaking = false
        main.post { onEvent(LiveEvent.TurnComplete); listen() }
    }

    companion object {
        /**
         * The phone's voice for the language: Android doesn't say which voices are male or female,
         * so the choice is by number, and the pitch leans the voice a little lower or higher.
         */
        /** How fast the phone's voice speaks (Settings). */
        @Volatile var rate = 1.05f

        fun setup(t: TextToSpeech, locale: Locale, gender: Gender, index: Int?) {
            t.language = locale
            val voices: List<Voice> = runCatching { t.voices.orEmpty() }.getOrDefault(emptySet())
                .filter { it.locale.language == locale.language && !it.isNetworkConnectionRequired }
                .sortedBy { it.name }
            if (voices.isNotEmpty()) t.voice = voices[(index ?: if (gender == Gender.MALE) 0 else voices.size - 1).mod(voices.size)]
            t.setPitch(if (gender == Gender.MALE) 0.85f else 1.12f)
            t.setSpeechRate(rate)
        }
    }
}
