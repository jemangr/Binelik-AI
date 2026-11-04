package app.binelik.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.binelik.ai.Ai
import app.binelik.ai.AiResult
import app.binelik.ai.Provider
import app.binelik.ai.Reason
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
        settings.characterId = c.id
        character.value = c
        intensity.value = settings.intensity(c.id)
        error.value = null
    }

    fun setIntensity(i: Intensity) {
        settings.setIntensity(character.value.id, i)
        intensity.value = i
    }

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
