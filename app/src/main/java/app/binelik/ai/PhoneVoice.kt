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
    /**
     * 1.3: the microphone stays open while the phone speaks, and the character stops as soon as the
     * user talks over it (like Gemini Live). What the phone itself says is not taken as the user.
     */
    private val bargeIn: Boolean = false,
) : Conversation {
    private val main = Handler(Looper.getMainLooper())
    private var tts: TextToSpeech? = null
    private var recognizer: SpeechRecognizer? = null
    @Volatile private var closed = false
    @Volatile private var speaking = false
    /** What the phone is saying now (to tell its own voice from the user's). */
    @Volatile private var saying = ""
    /** The recogniser is running (so a second start isn't sent while it listens during speech). */
    @Volatile private var listening = false

    override var micMuted = false
        set(v) { field = v; val keep = pttHold; main.post { if (v) { if (keep) recognizer?.stopListening() else { recognizer?.cancel(); listening = false } } else if (!speaking) listen() } }
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
                override fun onStart(id: String?) { speaking = true; if (bargeIn) main.post { listen(duringSpeech = true) } }
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

    private fun listen(duringSpeech: Boolean = false) {
        if (closed || micMuted || (speaking && !(bargeIn && duringSpeech))) return
        val i = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE, languageTag)
            .putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        if (listening) return
        listening = runCatching { recognizer?.startListening(i) }.isSuccess
    }

    private val listener = object : RecognitionListener {
        override fun onPartialResults(b: Bundle?) {
            val text = b?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull() ?: return
            if (speaking) {
                // Talking over the character: it stops, and what the user says becomes the next turn.
                if (!userOver(text, saying)) return
                tts?.stop(); speaking = false
                onEvent(LiveEvent.Interrupted)
            }
            onEvent(LiveEvent.HeardSoFar(text))
        }

        override fun onResults(b: Bundle?) {
            listening = false
            val text = b?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.trim().orEmpty()
            // While the phone still speaks, what was heard is its own voice: listen again.
            if (speaking) { if (!userOver(text, saying)) { listen(duringSpeech = true); return }; tts?.stop(); speaking = false; onEvent(LiveEvent.Interrupted) }
            if (text.isEmpty()) { listen(); return }
            onEvent(LiveEvent.HeardSoFar(text))
            scope.launch {
                val reply = answer(text)
                if (closed) return@launch
                if (reply == null) { listen(); return@launch }
                onEvent(LiveEvent.Said(reply))
                if (voiceMuted) { onEvent(LiveEvent.TurnComplete); listen(); return@launch }
                speaking = true
                saying = reply
                tts?.speak(reply, TextToSpeech.QUEUE_FLUSH, null, "r${System.nanoTime()}")
            }
        }

        override fun onError(error: Int) {
            listening = false
            if (closed) return
            when (error) {
                SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT, SpeechRecognizer.ERROR_RECOGNIZER_BUSY,
                SpeechRecognizer.ERROR_CLIENT -> main.postDelayed({ listen(duringSpeech = speaking) }, 300)
                SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> finish("mic")
                else -> main.postDelayed({ listen(duringSpeech = speaking) }, 1000)
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
         * Is [heard] the user talking over the phone, and not the phone's own voice ([saying]) coming
         * back through the microphone? At least two words, and most of them not in what it says.
         */
        fun userOver(heard: String, saying: String): Boolean {
            fun words(x: String) = app.binelik.safety.Safety.normalize(x).split(' ').filter { it.length > 1 }
            val h = words(heard)
            if (h.size < 2) return false
            val said = words(saying).toSet()
            val mine = h.count { it !in said }
            return mine * 2 > h.size
        }

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
