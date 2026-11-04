package app.binelik.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.binelik.BuildConfig
import app.binelik.ai.Ai
import app.binelik.update.Installer
import app.binelik.extras.Stats
import app.binelik.extras.StatsCalc
import app.binelik.data.Anger
import app.binelik.i18n.Lang
import app.binelik.extras.Swears
import app.binelik.ai.AiResult
import app.binelik.ai.Provider
import app.binelik.ai.Reason
import app.binelik.ai.Turn
import app.binelik.data.Character
import app.binelik.data.Characters
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
        if (!BuildConfig.UPDATE_CHECK) return
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

    /** The paid balance left (DeepSeek), shown in the settings and on the home screen. */
    val balance = MutableStateFlow<String?>(null)
    fun loadBalance() = viewModelScope.launch {
        val p = settings.provider
        balance.value = keys.get(p)?.let { Ai.balance(p, it) }
    }

    fun canVoice() = Engine.canVoice()
    fun play() = Engine.play()
    fun stopLive() = Engine.stop()
    fun toggleMic() = Engine.toggleMic()
    fun toggleVoice() = Engine.toggleVoice()

    private var roastNext = false

    /** «Ψήσε με»: the next reply roasts the user, from the conversation so far. */
    fun roast() { roastNext = true; send(t("🔥 Ψήσε με!", "🔥 Roast me!")) }

    /** The dice: a random character with a random intensity. */
    fun dice() {
        val all = Characters.all + listOfNotNull(settings.custom())
        val c = all.random()
        settings.setIntensity(c.id, Intensity.entries.random())
        pick(c)
    }

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
            val system = Prompts.system(c, intensity.value, settings.team, settings.style()) + (if (roastNext) "\n\n" + Prompts.roast else "")
            roastNext = false
            settings.used(provider, text = 1)
            when (val r = Ai.reply(provider, key, system, turns)) {
                is AiResult.Ok -> {
                    val isPause = r.text.startsWith(Prompts.PAUSE)
                    val found = (r.sources.map { "${it.title.replace('\t', ' ')}\t${it.uri}" } + r.searches.map { "?\t$it" }).joinToString("\n")
                    dao.add(Message(character = c.id, fromUser = false, text = r.text.removePrefix(Prompts.PAUSE).trim(), pause = isPause, sources = found))
                    if (isPause) Engine.pause.value = true
                }
                is AiResult.Fail -> error.value = message(r.reason, provider)
            }
            sending.value = false
        }
    }

    fun clearHistory(all: Boolean) = viewModelScope.launch {
        if (all) { dao.deleteAll(); Db.get(getApplication()).anger().deleteAll() } else dao.deleteCharacter(character.value.id)
    }

    private suspend fun ask(system: String, turns: List<Turn>): String? {
        val provider = settings.provider
        val key = keys.get(provider) ?: run { error.value = t("Δεν υπάρχει κλειδί για το ${provider.label}. Βάλ' το από τις Ρυθμίσεις.", "There is no ${provider.label} key. Add it in the Settings."); return null }
        settings.used(provider, text = 1)
        return when (val r = Ai.reply(provider, key, system, turns)) {
            is AiResult.Ok -> r.text.removePrefix(Prompts.PAUSE).trim()
            is AiResult.Fail -> { error.value = message(r.reason, provider); null }
        }
    }

    /** Text as shown: with bleep mode, swear words become «μπιπ». */
    fun shown(text: String) = if (settings.bleep) Swears.mask(text, Lang.en) else text

    // ---- Anger meter ----

    private var angerId: Long? = null
    fun angerBefore(v: Int) = viewModelScope.launch { angerId = Db.get(getApplication()).anger().add(Anger(character = character.value.id, before = v)) }
    fun angerAfter(v: Int) = viewModelScope.launch { angerId?.let { Db.get(getApplication()).anger().setAfter(it, v) }; angerId = null }
    val angerOpen get() = angerId != null

    // ---- Statistics and badges ----

    val stats = MutableStateFlow<Stats?>(null)
    val weekText = MutableStateFlow<String?>(null)
    val busy = MutableStateFlow(false)

    fun loadStats() = viewModelScope.launch {
        val db = Db.get(getApplication())
        stats.value = StatsCalc.compute(db.messages().since(0), db.anger().since(0))
    }

    /** The funny summary of the week: the user's own messages of the week go to the AI, only when asked. */
    fun summarizeWeek() = viewModelScope.launch {
        busy.value = true
        val mine = Db.get(getApplication()).messages().since(System.currentTimeMillis() - 7 * 24 * 3600 * 1000L).filter { it.fromUser && !it.pause }
        weekText.value = if (mine.isEmpty()) t("Δεν ξέσπασες καθόλου αυτή την εβδομάδα. Ζεν!", "You didn't vent at all this week. Zen!")
        else ask(Prompts.week, listOf(Turn(true, mine.takeLast(80).joinToString("\n") { "- " + it.text.take(200) })))
        busy.value = false
    }

    // ---- Two characters fighting ----

    data class FightLine(val character: Character?, val text: String)
    val fight = MutableStateFlow<List<FightLine>>(emptyList())
    private var fighters: Pair<Character, Character>? = null
    private var fightTopic = ""

    fun fightStart(a: Character, b: Character, topic: String) {
        if (Safety.check(topic) != SafetyHit.NONE) { Engine.pause.value = true; return }
        fighters = a to b; fightTopic = topic; fight.value = emptyList()
        fightMore(4)
    }

    fun fightFuel(text: String) {
        if (text.isBlank()) return
        if (Safety.check(text) != SafetyHit.NONE) { Engine.pause.value = true; return }
        fight.value = fight.value + FightLine(null, text.trim())
        fightMore(2)
    }

    fun fightMore(turns: Int = 2) = viewModelScope.launch {
        val (a, b) = fighters ?: return@launch
        busy.value = true
        repeat(turns) {
            val lines = fight.value
            val lastSpeaker = lines.lastOrNull { it.character != null }?.character
            val me = if (lastSpeaker?.id == a.id) b else a
            val other = if (me.id == a.id) b else a
            // From this character's side: its own lines are the model's, everything else the user's.
            val turnsFor = lines.map { l ->
                when (l.character?.id) {
                    me.id -> Turn(false, l.text)
                    null -> Turn(true, t("(Ο χρήστης φωνάζει:) ", "(The user shouts:) ") + l.text)
                    else -> Turn(true, "${l.character.name}: ${l.text}")
                }
            }.ifEmpty { listOf(Turn(true, t("Ξεκίνα τον καβγά.", "Start the fight."))) }
            val reply = ask(Prompts.fight(me, other, settings.intensity(me.id), settings.team, fightTopic, settings.style()), turnsFor) ?: return@repeat
            fight.value = fight.value + FightLine(me, reply)
        }
        busy.value = false
    }

    // ---- The email you won't send ----

    val angryEmail = MutableStateFlow("")
    val politeEmail = MutableStateFlow("")

    fun writeAngry(to: String, what: String) = viewModelScope.launch {
        if (Safety.check(what) != SafetyHit.NONE) { Engine.pause.value = true; return@launch }
        busy.value = true; politeEmail.value = ""
        val c = character.value
        angryEmail.value = ask(Prompts.angryEmail(c, settings.intensity(c.id), settings.team, settings.style()),
            listOf(Turn(true, t("Προς: ", "To: ") + to.take(80) + "\n" + t("Τι έγινε: ", "What happened: ") + what.take(1500)))).orEmpty()
        busy.value = false
    }

    fun makePolite() = viewModelScope.launch {
        if (angryEmail.value.isBlank()) return@launch
        busy.value = true
        politeEmail.value = ask(Prompts.politeEmail, listOf(Turn(true, angryEmail.value))).orEmpty()
        busy.value = false
    }

    // ---- The update, from inside the app ----

    val downloading = MutableStateFlow<Int?>(null)

    fun installUpdate(context: android.content.Context) = viewModelScope.launch {
        val r = update.value ?: return@launch
        if (!Installer.canInstall(context)) { Installer.allowInstalls(context); return@launch }
        downloading.value = 0
        val f = Installer.download(context, r.apk) { downloading.value = it }
        downloading.value = null
        if (f != null) Installer.install(context, f) else error.value = t("Το κατέβασμα απέτυχε. Δοκίμασε από το GitHub.", "The download failed. Try from GitHub.")
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
