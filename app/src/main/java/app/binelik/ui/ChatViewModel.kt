package app.binelik.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.binelik.ai.Ai
import app.binelik.ai.AiResult
import app.binelik.ai.Provider
import app.binelik.ai.Reason
import app.binelik.ai.LiveEvent
import app.binelik.ai.LiveProtocol
import app.binelik.ai.LiveSession
import app.binelik.ai.Turn
import app.binelik.data.Character
import app.binelik.data.Db
import app.binelik.data.Intensity
import app.binelik.data.KeyVault
import app.binelik.data.Message
import app.binelik.data.Prompts
import app.binelik.data.Settings
import app.binelik.safety.Safety
import app.binelik.safety.SafetyHit
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class LiveState { OFF, CONNECTING, LISTENING, SPEAKING, ERROR }

class ChatViewModel(app: Application) : AndroidViewModel(app) {
    val settings = Settings(app)
    val keys = KeyVault(app)
    private val dao = Db.get(app).messages()

    val character = MutableStateFlow(settings.character())
    val intensity = MutableStateFlow(settings.intensity(character.value.id))
    val sending = MutableStateFlow(false)
    val error = MutableStateFlow<String?>(null)
    /** Shown over everything when the character steps out of its role for safety. */
    val pause = MutableStateFlow(false)

    @OptIn(ExperimentalCoroutinesApi::class)
    val messages: StateFlow<List<Message>> = character
        .flatMapLatest { dao.forCharacter(it.id) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    fun pick(c: Character) {
        val wasLive = liveOn
        if (wasLive) stopLive()
        settings.characterId = c.id
        character.value = c
        intensity.value = settings.intensity(c.id)
        error.value = null
        if (wasLive) play()
    }

    fun setIntensity(i: Intensity) {
        settings.setIntensity(character.value.id, i)
        intensity.value = i
        if (liveOn) { stopLive(); play() }
    }

    // ---- Live (Gemini only) ----

    val live = MutableStateFlow(LiveState.OFF)
    val micMuted = MutableStateFlow(false)
    val voiceMuted = MutableStateFlow(false)
    val liveError = MutableStateFlow<String?>(null)
    /** The lines of the turn in progress, shown under the circle. */
    val heardLine = MutableStateFlow("")
    val saidLine = MutableStateFlow("")
    private var session: LiveSession? = null
    private val liveOn get() = session != null

    fun canLive() = settings.provider == Provider.GEMINI && keys.has(Provider.GEMINI)

    fun play() {
        if (session != null) return
        val key = keys.get(Provider.GEMINI) ?: run { liveError.value = "Το Live θέλει κλειδί Gemini. Βάλ' το από τις Ρυθμίσεις."; live.value = LiveState.ERROR; return }
        val c = character.value
        val g = settings.gender(c)
        val recent = messages.value.filter { !it.pause }.map { it.fromUser to it.text }
        val system = Prompts.live(c, intensity.value, settings.team, recent)
        liveError.value = null
        live.value = LiveState.CONNECTING
        session = LiveSession(key, viewModelScope, ::onLive, ::onLiveClosed).also {
            it.micMuted = micMuted.value
            it.voiceMuted = voiceMuted.value
            it.start(system, LiveProtocol.Options(LiveProtocol.voiceFor(c, g, settings.voiceIndex(c.id)), "el-GR", null))
        }
    }

    /** Pause: the conversation stops and the microphone closes. */
    fun stopLive() {
        saveTurn()
        session?.let { session = null; it.stop() }
        live.value = LiveState.OFF
    }

    fun toggleMic() { micMuted.value = !micMuted.value; session?.micMuted = micMuted.value }
    fun toggleVoice() { voiceMuted.value = !voiceMuted.value; session?.voiceMuted = voiceMuted.value }

    private val heard = StringBuilder()
    private val said = StringBuilder()

    private fun onLive(e: LiveEvent) {
        when (e) {
            LiveEvent.Ready -> live.value = LiveState.LISTENING
            is LiveEvent.Heard -> {
                synchronized(heard) { heard.append(e.text) }
                heardLine.value = heard.toString().trim()
                if (live.value != LiveState.SPEAKING) live.value = LiveState.LISTENING
                // The phone's own check, on what the user said.
                if (Safety.check(heard.toString()) != SafetyHit.NONE) safetyStop()
            }
            is LiveEvent.Said -> {
                synchronized(said) { said.append(e.text) }
                saidLine.value = said.toString().trim()
                live.value = LiveState.SPEAKING
                if (Safety.normalize(said.toString()).contains(Safety.normalize(Prompts.LIVE_PAUSE))) safetyStop()
            }
            is LiveEvent.Audio -> if (live.value != LiveState.SPEAKING) live.value = LiveState.SPEAKING
            LiveEvent.Interrupted, LiveEvent.TurnComplete -> { saveTurn(); live.value = LiveState.LISTENING }
            // The server asks to reconnect: a new session continues the conversation.
            LiveEvent.GoAway -> viewModelScope.launch { stopLive(); play() }
        }
    }

    private fun safetyStop() = viewModelScope.launch {
        val c = character.value
        heard.setLength(0); said.setLength(0)
        stopLive()
        dao.add(Message(character = c.id, fromUser = false, text = PAUSE_TEXT, pause = true))
        pause.value = true
    }

    private fun onLiveClosed(err: String?) {
        if (session == null) return
        session = null
        saveTurn()
        if (err != null) {
            liveError.value = when {
                err.contains("401") || err.contains("403") || err.contains("API key", true) -> "Το κλειδί του Gemini δεν δουλεύει για το Live."
                err.contains("429") || err.contains("quota", true) || err.contains("exhausted", true) -> "Έφτασες το δωρεάν όριο του Gemini. Περίμενε λίγο ή δοκίμασε αύριο."
                else -> "Το Live σταμάτησε ($err). Πάτα Play για να συνεχίσεις."
            }
            live.value = LiveState.ERROR
        } else live.value = LiveState.OFF
    }

    /** The lines of a finished turn go into the conversation history. */
    private fun saveTurn() {
        val h = synchronized(heard) { heard.toString().trim().also { heard.setLength(0) } }
        val s = synchronized(said) { said.toString().trim().also { said.setLength(0) } }
        heardLine.value = ""; saidLine.value = ""
        if (h.isEmpty() && s.isEmpty()) return
        val c = character.value.id
        viewModelScope.launch {
            if (h.isNotEmpty()) dao.add(Message(character = c, fromUser = true, text = h))
            if (s.isNotEmpty()) dao.add(Message(character = c, fromUser = false, text = s))
        }
    }

    override fun onCleared() { session?.stop(); session = null }

    /** After the custom character or the team changes in the settings. */
    fun refresh() { character.value = settings.character() }

    fun send(text: String) {
        val t = text.trim()
        if (t.isEmpty() || sending.value) return
        val c = character.value
        error.value = null
        viewModelScope.launch {
            dao.add(Message(character = c.id, fromUser = true, text = t))
            // The phone's own check comes first: nothing goes to the AI when it stops.
            if (Safety.check(t) != SafetyHit.NONE) {
                dao.add(Message(character = c.id, fromUser = false, text = PAUSE_TEXT, pause = true))
                pause.value = true
                return@launch
            }
            val provider = settings.provider
            val key = keys.get(provider)
            if (key == null) { error.value = "Δεν υπάρχει κλειδί για το ${provider.label}. Βάλ' το από τις Ρυθμίσεις."; return@launch }
            sending.value = true
            val turns = messages.value.filter { !it.pause }.map { Turn(it.fromUser, it.text) }
                .let { if (it.lastOrNull()?.text == t) it else it + Turn(true, t) }
            val system = Prompts.system(c, intensity.value, settings.team)
            when (val r = Ai.reply(provider, key, system, turns)) {
                is AiResult.Ok -> {
                    val isPause = r.text.startsWith(Prompts.PAUSE)
                    dao.add(Message(character = c.id, fromUser = false, text = r.text.removePrefix(Prompts.PAUSE).trim(), pause = isPause))
                    if (isPause) pause.value = true
                }
                is AiResult.Fail -> error.value = message(r.reason, provider)
            }
            sending.value = false
        }
    }

    fun clearHistory(all: Boolean) = viewModelScope.launch {
        if (all) dao.deleteAll() else dao.deleteCharacter(character.value.id)
    }

    companion object {
        const val PAUSE_TEXT = "Παύση για ασφάλεια. Μίλα τώρα με κάποιον: 1018 (24ωρη γραμμή για την αυτοκτονία) ή 112."

        fun message(r: Reason, p: Provider): String = when (r) {
            Reason.BAD_KEY -> "Το κλειδί του ${p.label} δεν δουλεύει. Έλεγξέ το από τις Ρυθμίσεις."
            Reason.LIMIT -> if (p == Provider.DEEPSEEK) "Τέλειωσε το υπόλοιπο ή το όριο στο DeepSeek. Δες το platform.deepseek.com."
            else "Έφτασες το δωρεάν όριο του Gemini. Περίμενε λίγο ή δοκίμασε αύριο."
            Reason.BLOCKED -> "Το ${p.label} δεν απάντησε σε αυτό (φίλτρο ασφαλείας του). Πες το αλλιώς."
            Reason.NETWORK -> "Δεν υπάρχει σύνδεση. Δοκίμασε ξανά."
            Reason.OTHER -> "Κάτι πήγε στραβά με το ${p.label}. Δοκίμασε ξανά."
        }
    }
}
