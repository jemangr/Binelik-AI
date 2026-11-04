package app.binelik.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.binelik.BuildConfig
import app.binelik.ai.Ai
import app.binelik.ai.AiResult
import app.binelik.ai.Provider
import app.binelik.ai.Reason
import app.binelik.ai.Turn
import app.binelik.data.Character
import app.binelik.data.Db
import app.binelik.data.Intensity
import app.binelik.data.Message
import app.binelik.data.Prompts
import app.binelik.i18n.t
import app.binelik.safety.Safety
import app.binelik.safety.SafetyHit
import app.binelik.update.Updates
import app.binelik.voice.Engine
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** The screens' state: the text conversation here, the voice one in [Engine]. */
class ChatViewModel(app: Application) : AndroidViewModel(app) {
    init { Engine.init(app) }
    val settings = Engine.settings
    val keys = Engine.keys
    private val dao = Db.get(app).messages()

    val character = MutableStateFlow(settings.character())
    val intensity = MutableStateFlow(settings.intensity(character.value.id))
    val sending = MutableStateFlow(false)
    val error = MutableStateFlow<String?>(null)
    /** Shown over everything when the character steps out of its role for safety. */
    val pause get() = Engine.pause

    // The voice conversation, shared with the notification and Android Auto.
    val live get() = Engine.state
    val micMuted get() = Engine.micMuted
    val voiceMuted get() = Engine.voiceMuted
    val liveError get() = Engine.error
    val heardLine get() = Engine.heardLine
    val saidLine get() = Engine.saidLine

    @OptIn(ExperimentalCoroutinesApi::class)
    val messages: StateFlow<List<Message>> = character
        .flatMapLatest { dao.forCharacter(it.id) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    fun pick(c: Character) {
        settings.characterId = c.id
        character.value = c
        intensity.value = settings.intensity(c.id)
        error.value = null
        Engine.changed()
    }

    fun setIntensity(i: Intensity) {
        settings.setIntensity(character.value.id, i)
        intensity.value = i
        Engine.changed()
    }

    /** After the custom character, the team, the voice or the language change in the settings. */
    fun refresh() { character.value = settings.character(); Engine.changed() }

    // ---- New version on GitHub ----

    val update = MutableStateFlow<Updates.Release?>(null)
    val checking = MutableStateFlow(false)

    /** At start: if allowed and not checked for a few hours. [now] from the settings button. */
    fun checkUpdate(now: Boolean = false) {
        if (!now && (!settings.checkUpdates || System.currentTimeMillis() - settings.lastUpdateCheck < Updates.EVERY_MS)) return
        checking.value = true
        viewModelScope.launch {
            val r = Updates.check(BuildConfig.VERSION_NAME)
            settings.lastUpdateCheck = System.currentTimeMillis()
            update.value = r?.takeIf { now || it.version != settings.skippedVersion }
            checking.value = false
        }
    }

    fun later() { update.value?.let { settings.skippedVersion = it.version }; update.value = null }

    fun canVoice() = Engine.canVoice()
    fun play() = Engine.play()
    fun stopLive() = Engine.stop()
    fun toggleMic() = Engine.toggleMic()
    fun toggleVoice() = Engine.toggleVoice()

    fun send(text: String) {
        val tx = text.trim()
        if (tx.isEmpty() || sending.value) return
        val c = character.value
        error.value = null
        viewModelScope.launch {
            dao.add(Message(character = c.id, fromUser = true, text = tx))
            // The phone's own check comes first: nothing goes to the AI when it stops.
            if (Safety.check(tx) != SafetyHit.NONE) {
                dao.add(Message(character = c.id, fromUser = false, text = Engine.pauseText(), pause = true))
                Engine.pause.value = true
                return@launch
            }
            val provider = settings.provider
            val key = keys.get(provider)
            if (key == null) { error.value = t("Δεν υπάρχει κλειδί για το ${provider.label}. Βάλ' το από τις Ρυθμίσεις.", "There is no ${provider.label} key. Add it in the Settings."); return@launch }
            sending.value = true
            val turns = messages.value.filter { !it.pause }.map { Turn(it.fromUser, it.text) }
                .let { if (it.lastOrNull()?.text == tx) it else it + Turn(true, tx) }
            val system = Prompts.system(c, intensity.value, settings.team)
            when (val r = Ai.reply(provider, key, system, turns)) {
                is AiResult.Ok -> {
                    val isPause = r.text.startsWith(Prompts.PAUSE)
                    dao.add(Message(character = c.id, fromUser = false, text = r.text.removePrefix(Prompts.PAUSE).trim(), pause = isPause))
                    if (isPause) Engine.pause.value = true
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
        fun message(r: Reason, p: Provider): String = when (r) {
            Reason.BAD_KEY -> t("Το κλειδί του ${p.label} δεν δουλεύει. Έλεγξέ το από τις Ρυθμίσεις.", "The ${p.label} key doesn't work. Check it in the Settings.")
            Reason.LIMIT -> if (p == Provider.DEEPSEEK) t("Τέλειωσε το υπόλοιπο ή το όριο στο DeepSeek. Δες το platform.deepseek.com.", "Your DeepSeek balance or limit ran out. See platform.deepseek.com.")
            else t("Έφτασες το δωρεάν όριο του Gemini. Περίμενε λίγο ή δοκίμασε αύριο.", "You reached Gemini's free limit. Wait a little or try tomorrow.")
            Reason.BLOCKED -> t("Το ${p.label} δεν απάντησε σε αυτό (φίλτρο ασφαλείας του). Πες το αλλιώς.", "${p.label} didn't answer that (its safety filter). Say it another way.")
            Reason.NETWORK -> t("Δεν υπάρχει σύνδεση. Δοκίμασε ξανά.", "No connection. Try again.")
            Reason.OTHER -> t("Κάτι πήγε στραβά με το ${p.label}. Δοκίμασε ξανά.", "Something went wrong with ${p.label}. Try again.")
        }
    }
}
