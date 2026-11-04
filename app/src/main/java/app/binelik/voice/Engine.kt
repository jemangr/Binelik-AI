package app.binelik.voice

import android.app.Application
import android.content.Context
import android.content.Intent
import android.speech.tts.TextToSpeech
import androidx.core.content.ContextCompat
import app.binelik.MainActivity
import app.binelik.ai.Ai
import app.binelik.ai.AiResult
import app.binelik.ai.Conversation
import app.binelik.ai.LiveEvent
import app.binelik.ai.LiveProtocol
import app.binelik.ai.LiveSession
import app.binelik.ai.PhoneVoice
import app.binelik.ai.Provider
import app.binelik.ai.Speaker
import app.binelik.ai.Turn
import app.binelik.data.Character
import app.binelik.data.Db
import app.binelik.data.Gender
import app.binelik.data.KeyVault
import app.binelik.data.Message
import app.binelik.data.Prompts
import app.binelik.data.Settings
import app.binelik.i18n.Lang
import app.binelik.i18n.Ui
import app.binelik.i18n.t
import app.binelik.safety.Safety
import app.binelik.safety.SafetyHit
import app.binelik.ui.ChatViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.Locale

enum class LiveState { OFF, CONNECTING, LISTENING, SPEAKING, ERROR }

/**
 * The voice conversation, for the whole app: the phone screen, the notification and Android Auto
 * all show and drive the same one. Gemini talks live; DeepSeek through the phone's own voice.
 * While it runs, [LiveService] keeps the microphone allowed with the screen off.
 */
object Engine {
    private lateinit var app: Application
    lateinit var settings: Settings; private set
    lateinit var keys: KeyVault; private set
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    val state = MutableStateFlow(LiveState.OFF)
    val micMuted = MutableStateFlow(false)
    val voiceMuted = MutableStateFlow(false)
    val error = MutableStateFlow<String?>(null)
    val heardLine = MutableStateFlow("")
    val saidLine = MutableStateFlow("")
    /** The safety card should show (the screen resets it). */
    val pause = MutableStateFlow(false)
    /** Bumped when the character or the settings change, for Android Auto to redraw. */
    val version = MutableStateFlow(0)

    private var conv: Conversation? = null
    private val heard = StringBuilder()
    private val said = StringBuilder()

    fun init(a: Application) {
        if (::app.isInitialized) return
        app = a
        settings = Settings(a)
        keys = KeyVault(a)
        Lang.en = settings.english
        Ui.scale = settings.textScale
        PhoneVoice.rate = settings.speechRate
        Speaker.gain = settings.voiceGain
    }

    val on get() = conv != null
    fun character(): Character = settings.character()

    /** Voice works with either AI: Gemini live, DeepSeek through the phone's voice. */
    fun canVoice() = keys.has(settings.provider)
    fun isLive() = settings.provider == Provider.GEMINI

    fun hasMic(c: Context) = ContextCompat.checkSelfPermission(c, android.Manifest.permission.RECORD_AUDIO) == android.content.pm.PackageManager.PERMISSION_GRANTED

    fun play() {
        if (conv != null) return
        val provider = settings.provider
        val key = keys.get(provider) ?: run { fail(t("Δεν υπάρχει κλειδί για το ${provider.label}. Βάλ' το από τις Ρυθμίσεις.", "There is no ${provider.label} key. Add it in the Settings.")); return }
        if (!hasMic(app)) { fail(t("Το Live θέλει το μικρόφωνο. Άνοιξε την εφαρμογή στο κινητό και πάτα Play.", "Live needs the microphone. Open the app on the phone and press Play.")); return }
        val c = character()
        val g = settings.gender(c)
        error.value = null
        state.value = LiveState.CONNECTING
        scope.launch {
            val dao = Db.get(app).messages()
            val recent = dao.forCharacter(c.id).first().filter { !it.pause }.map { it.fromUser to it.text }
            val system = Prompts.live(c, settings.intensity(c.id), settings.team, recent, settings.style())
            startedAt = System.currentTimeMillis()
            conv = if (provider == Provider.GEMINI) {
                LiveSession(app, key, scope, ::onEvent, ::onClosed).also {
                    it.micMuted = micMuted.value; it.voiceMuted = voiceMuted.value
                    it.start(system, LiveProtocol.Options(LiveProtocol.voiceFor(c, g, settings.voiceIndex(c.id)), Lang.tag, null))
                }
            } else {
                // The text AI answers each spoken turn, with the conversation so far.
                val turns = recent.map { Turn(it.first, it.second) }.toMutableList()
                val textSystem = Prompts.system(c, settings.intensity(c.id), settings.team, settings.style())
                PhoneVoice(app, Lang.tag, g, settings.voiceIndex(c.id), answer = { said0 ->
                    if (Safety.check(said0) != SafetyHit.NONE) { safetyStop(); null }
                    else {
                        turns += Turn(true, said0)
                        settings.used(provider, text = 1)
                        when (val r = Ai.reply(provider, key, textSystem, turns)) {
                            is AiResult.Ok -> {
                                if (r.text.startsWith(Prompts.PAUSE)) { safetyStop(); null }
                                else { turns += Turn(false, r.text); r.text }
                            }
                            is AiResult.Fail -> { fail(ChatViewModel.message(r.reason, provider)); stopAll(); null }
                        }
                    }
                }, scope = scope, onEvent = ::onEvent, onClosed = ::onClosed).also {
                    it.micMuted = micMuted.value; it.voiceMuted = voiceMuted.value; it.start()
                }
            }
            LiveService.start(app)
        }
    }

    private fun fail(msg: String) { error.value = msg; state.value = LiveState.ERROR }

    fun stop() {
        saveTurn()
        stopAll()
        if (state.value != LiveState.ERROR) state.value = LiveState.OFF
    }

    private var startedAt = 0L

    private fun stopAll() {
        if (startedAt > 0) { settings.used(settings.provider, liveSeconds = ((System.currentTimeMillis() - startedAt) / 1000).toInt()); startedAt = 0 }
        conv?.let { conv = null; it.stop() }
        LiveService.stop(app)
    }

    /** After a change of character, intensity or voice: a running conversation goes on with the new one. */
    fun changed() {
        version.value++
        if (conv != null) { stop(); play() }
    }

    /** Tap while it speaks: it stops and listens (the phone's voice; Gemini Live stops by itself when you talk). */
    fun interrupt() { conv?.interrupt() }

    fun toggleMic() { micMuted.value = !micMuted.value; conv?.micMuted = micMuted.value; version.value++ }
    fun toggleVoice() { voiceMuted.value = !voiceMuted.value; conv?.voiceMuted = voiceMuted.value; version.value++ }

    private fun onEvent(e: LiveEvent) {
        when (e) {
            LiveEvent.Ready -> state.value = LiveState.LISTENING
            is LiveEvent.Heard -> heardChanged(synchronized(heard) { heard.append(e.text).toString() })
            is LiveEvent.HeardSoFar -> heardChanged(synchronized(heard) { heard.setLength(0); heard.append(e.text).toString() })
            is LiveEvent.Said -> {
                val s = synchronized(said) { said.append(e.text).toString() }
                saidLine.value = s.trim()
                state.value = LiveState.SPEAKING
                val n = Safety.normalize(s)
                if (Prompts.livePauses.any { n.contains(Safety.normalize(it)) }) safetyStop()
            }
            is LiveEvent.Audio -> if (state.value != LiveState.SPEAKING) state.value = LiveState.SPEAKING
            LiveEvent.Interrupted, LiveEvent.TurnComplete -> { saveTurn(); if (conv != null) state.value = LiveState.LISTENING }
            // The server asks to reconnect: a new session continues the conversation.
            LiveEvent.GoAway -> scope.launch { stop(); play() }
        }
        version.value++
    }

    private fun heardChanged(text: String) {
        heardLine.value = text.trim()
        if (state.value != LiveState.SPEAKING) state.value = LiveState.LISTENING
        // The phone's own check, on what the user said.
        if (Safety.check(text) != SafetyHit.NONE) safetyStop()
    }

    private fun safetyStop() {
        val c = character()
        synchronized(heard) { heard.setLength(0) }; synchronized(said) { said.setLength(0) }
        stop()
        scope.launch { Db.get(app).messages().add(Message(character = c.id, fromUser = false, text = pauseText(), pause = true)) }
        pause.value = true
        version.value++
    }

    fun pauseText() = t(
        "Παύση για ασφάλεια. Μίλα τώρα με κάποιον: 1018 (24ωρη γραμμή για την αυτοκτονία) ή 112.",
        "Paused for safety. Talk to someone now: 112, or a local crisis line (in Greece 1018, 24 hours).",
    )

    private fun onClosed(err: String?) {
        if (conv == null) return
        conv = null
        saveTurn()
        LiveService.stop(app)
        if (err != null) {
            fail(when {
                err == "no-recognizer" -> t("Το κινητό δεν έχει αναγνώριση ομιλίας. Εγκατέστησε το Google app ή χρησιμοποίησε Gemini.", "The phone has no speech recognition. Install the Google app or use Gemini.")
                err == "no-tts" -> t("Το κινητό δεν έχει φωνή κειμένου (TTS).", "The phone has no text-to-speech voice.")
                err == "mic" -> t("Το Live θέλει το μικρόφωνο.", "Live needs the microphone.")
                err.contains("401") || err.contains("403") || err.contains("API key", true) -> t("Το κλειδί του Gemini δεν δουλεύει για το Live.", "The Gemini key doesn't work for Live.")
                err.contains("429") || err.contains("quota", true) || err.contains("exhausted", true) -> t("Έφτασες το δωρεάν όριο του Gemini. Περίμενε λίγο ή δοκίμασε αύριο.", "You reached Gemini's free limit. Wait a little or try tomorrow.")
                else -> t("Το Live σταμάτησε ($err). Πάτα Play για να συνεχίσεις.", "Live stopped ($err). Press Play to go on.")
            })
        } else state.value = LiveState.OFF
        version.value++
    }

    /** The lines of a finished turn go into the conversation history (text only, never audio). */
    private fun saveTurn() {
        val h = synchronized(heard) { heard.toString().trim().also { heard.setLength(0) } }
        val s = synchronized(said) { said.toString().trim().also { said.setLength(0) } }
        heardLine.value = ""; saidLine.value = ""
        if (h.isEmpty() && s.isEmpty()) return
        val c = character().id
        scope.launch {
            val dao = Db.get(app).messages()
            if (h.isNotEmpty()) dao.add(Message(character = c, fromUser = true, text = h))
            if (s.isNotEmpty()) dao.add(Message(character = c, fromUser = false, text = s))
        }
    }

    // ---- «▶ Άκου»: a sample of the character's voice ----

    private var sample: Conversation? = null
    private var sampleTts: TextToSpeech? = null

    fun preview(c: Character, g: Gender, voiceIndex: Int?) {
        if (conv != null) return
        sample?.stop(); sample = null
        val line = c.sample.ifEmpty { c.tagline }.ifEmpty { c.name }
        val key = keys.get(Provider.GEMINI)
        if (settings.provider == Provider.GEMINI && key != null) {
            sample = LiveSession(app, key, scope, { e -> if (e == LiveEvent.TurnComplete) scope.launch { sample?.stop(); sample = null } }, { sample = null },
                listen = false, firstText = t("Πες ακριβώς αυτή τη φράση, με το ύφος σου: «$line»", "Say exactly this line, in your style: \"$line\"")).also {
                it.start(Prompts.system(c, settings.intensity(c.id), settings.team, settings.style()), LiveProtocol.Options(LiveProtocol.voiceFor(c, g, voiceIndex), Lang.tag, null))
            }
        } else {
            sampleTts?.shutdown()
            var tts: TextToSpeech? = null
            tts = TextToSpeech(app) { ok ->
                if (ok == TextToSpeech.SUCCESS) tts?.let { PhoneVoice.setup(it, Locale.forLanguageTag(Lang.tag), g, voiceIndex); it.speak(line, TextToSpeech.QUEUE_FLUSH, null, "sample") }
            }
            sampleTts = tts
        }
    }

    /** How many voices the phone has for the language (DeepSeek), or Gemini's per gender. */
    fun voiceCount(g: Gender): Int = if (settings.provider == Provider.GEMINI) LiveProtocol.voices(g).size else 4
}

/** Opens the app from the notification or the car. */
fun openApp(c: Context): Intent = Intent(c, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
