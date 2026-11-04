package app.binelik.ui

import android.app.Application
import android.speech.tts.TextToSpeech
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import app.binelik.ai.Ai
import app.binelik.ai.AiResult
import app.binelik.ai.PhoneVoice
import app.binelik.ai.Turn
import app.binelik.data.Character
import app.binelik.data.Characters
import app.binelik.data.GamePrompts
import app.binelik.data.Intensity
import app.binelik.data.Prompts
import app.binelik.extras.Packs
import app.binelik.i18n.Lang
import app.binelik.i18n.t
import app.binelik.safety.Safety
import app.binelik.safety.SafetyHit
import app.binelik.voice.Engine
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import java.util.Locale

/** One line of a game: who said it (null: the user) and what. */
data class GameLine(val who: Character?, val text: String, val user: Int? = null, val me: Int? = null)

/** The 1.2 games that talk to the AI: insult battle, rehearsal, group chat; and the character packs. */
class GamesViewModel(application: Application) : AndroidViewModel(application) {
    private val settings get() = Engine.settings
    val busy = MutableStateFlow(false)
    val error = MutableStateFlow<String?>(null)

    /** The user's own text first goes through the phone's safety check; a PAUSE reply stops the game too. */
    private fun unsafe(text: String): Boolean = (Safety.check(text) != SafetyHit.NONE).also { if (it) Engine.pause.value = true }

    private suspend fun ask(system: String, turns: List<Turn>): String? {
        val p = settings.provider
        val key = Engine.keys.get(p) ?: run { error.value = t("Δεν υπάρχει κλειδί για το ${p.label}. Βάλ' το από τις Ρυθμίσεις.", "There is no ${p.label} key. Add it in the Settings."); return null }
        settings.used(p, text = 1)
        return when (val r = Ai.reply(p, key, system, turns)) {
            is AiResult.Ok -> if (r.text.startsWith(Prompts.PAUSE)) { Engine.pause.value = true; null } else r.text
            is AiResult.Fail -> { error.value = ChatViewModel.message(r.reason, p); null }
        }
    }

    private fun intensity(c: Character, cap: Intensity? = null): Intensity {
        val i = settings.intensity(c.id)
        return if (cap != null && i.ordinal > cap.ordinal) cap else i
    }

    // ---- Insult battle ----
    val battle = MutableStateFlow<List<GameLine>>(emptyList())
    var battleWith: Character = Characters.all.first()

    fun battleStart(c: Character) { battleWith = c; battle.value = emptyList(); error.value = null }
    val battleOver get() = battle.value.count { it.who != null } >= 3

    fun battleSend(text: String) {
        val tx = text.trim()
        if (tx.isEmpty() || busy.value || battleOver || unsafe(tx)) return
        val c = battleWith
        battle.value = battle.value + GameLine(null, tx)
        viewModelScope.launch {
            busy.value = true; error.value = null
            val round = battle.value.count { it.who != null } + 1
            val turns = battle.value.map { Turn(it.who == null, it.text) }
            ask(GamePrompts.battle(c, intensity(c), settings.team, settings.style(), round), turns)?.let { r ->
                val (line, u, me) = GamePrompts.parseBattle(r)
                battle.value = battle.value + GameLine(c, line, u ?: 5, me ?: 5)
                if (battleOver) {
                    settings.add("battles")
                    if (battleScore().first > battleScore().second) settings.add("battles_won")
                }
            } ?: run { battle.value = battle.value.dropLast(1) }
            busy.value = false
        }
    }

    /** User's total, character's total. */
    fun battleScore(): Pair<Int, Int> = battle.value.filter { it.who != null }.let { l -> l.sumOf { it.user ?: 0 } to l.sumOf { it.me ?: 0 } }

    // ---- Rehearsal ----
    val rehearsal = MutableStateFlow<List<GameLine>>(emptyList())
    val coach = MutableStateFlow<String?>(null)
    var scenario = ""
    var rehearsalWith: Character = Characters.all.first()

    fun rehearsalStart(c: Character, what: String) {
        if (what.isBlank() || unsafe(what)) return
        rehearsalWith = c; scenario = what.trim(); rehearsal.value = emptyList(); coach.value = null; error.value = null
    }

    fun rehearsalSend(text: String) {
        val tx = text.trim()
        if (tx.isEmpty() || busy.value || unsafe(tx)) return
        val c = rehearsalWith
        rehearsal.value = rehearsal.value + GameLine(null, tx)
        viewModelScope.launch {
            busy.value = true; error.value = null
            // The other side pushes back, but not at full force: it is practice for a real talk.
            ask(GamePrompts.rehearsal(c, intensity(c, Intensity.MEDIUM), settings.team, settings.style(), scenario), rehearsal.value.map { Turn(it.who == null, it.text) })
                ?.let { rehearsal.value = rehearsal.value + GameLine(c, it) } ?: run { rehearsal.value = rehearsal.value.dropLast(1) }
            busy.value = false
        }
    }

    fun rehearsalEnd() = viewModelScope.launch {
        if (rehearsal.value.none { it.who == null }) return@launch
        busy.value = true; error.value = null
        val talk = rehearsal.value.joinToString("\n") { (it.who?.name ?: t("Χρήστης", "User")) + ": " + it.text }
        coach.value = ask(GamePrompts.coach(scenario), listOf(Turn(true, talk.takeLast(6000))))
        if (coach.value != null) settings.add("rehearsals")
        busy.value = false
    }

    // ---- Group chat ----
    val group = MutableStateFlow<List<GameLine>>(emptyList())
    var groupA: Character = Characters.all.first()
    var groupB: Character = Characters.all[1]

    fun groupStart(a: Character, b: Character) { groupA = a; groupB = b; group.value = emptyList(); error.value = null }

    fun groupSend(text: String) {
        val tx = text.trim()
        if (tx.isEmpty() || busy.value || unsafe(tx)) return
        group.value = group.value + GameLine(null, tx)
        viewModelScope.launch {
            busy.value = true; error.value = null
            for ((me, other) in listOf(groupA to groupB, groupB to groupA)) {
                // For each character, its own lines are its turns; the user's and the other's are the user's side.
                val turns = group.value.map { l ->
                    when (l.who?.id) { me.id -> Turn(false, l.text); null -> Turn(true, l.text); else -> Turn(true, "${other.name}: ${l.text}") }
                }
                val r = ask(GamePrompts.group(me, other, intensity(me), settings.team, settings.style()), turns) ?: break
                group.value = group.value + GameLine(me, r.removePrefix("${me.name}:").trim())
            }
            busy.value = false
        }
    }

    // ---- Character packs from GitHub ----
    val available = MutableStateFlow<List<Packs.Pack>?>(null)
    val installed = MutableStateFlow(Packs.decode(settings.packsJson))

    fun fetchPacks() = viewModelScope.launch {
        busy.value = true; error.value = null
        val got = Packs.fetch()
        if (got == null) error.value = t("Δεν βρέθηκε η λίστα στο GitHub (internet;).", "Couldn't get the list from GitHub (internet?).")
        available.value = got
        busy.value = false
    }

    fun toggle(p: Packs.Pack) {
        val now = installed.value
        val next = if (now.any { it.id == p.id }) now.filter { it.id != p.id } else now + p
        settings.packsJson = Packs.encode(next)
        installed.value = next
        if (settings.characterId == "pack:${p.id}" && next.none { it.id == p.id }) settings.characterId = Characters.all.first().id
    }
}

@Composable
private fun Chips(selected: Character, onPick: (Character) -> Unit, exclude: String? = null) {
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        // The chosen one first, so it is always in view.
        Engine.settings.everyone().filter { it.id != exclude }.sortedBy { if (it.id == selected.id) 0 else 1 }.forEach { c ->
            val on = c.id == selected.id
            val k = Color(c.color)
            Row(
                Modifier.clip(RoundedCornerShape(50)).background(if (on) k.copy(alpha = 0.18f) else C.panel2)
                    .border(1.dp, if (on) k else C.line, RoundedCornerShape(50)).clickable(role = Role.RadioButton) { onPick(c) }
                    .padding(horizontal = 12.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) { Dot(k, 10); Spacer(Modifier.width(6.dp)); Text(c.label, fontSize = 13.sp, fontWeight = FontWeight.SemiBold) }
        }
    }
}

@Composable
private fun Bubble(l: GameLine, extra: String? = null) {
    val mine = l.who == null
    val k = l.who?.let { Color(it.color) } ?: C.text
    Column(Modifier.fillMaxWidth(), horizontalAlignment = if (mine) Alignment.End else Alignment.Start) {
        if (!mine) Text(l.who!!.label, color = k, fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
        Text(
            l.text, fontSize = 15.sp, lineHeight = 21.sp,
            modifier = Modifier.clip(RoundedCornerShape(16.dp)).background(if (mine) C.panel2 else k.copy(alpha = 0.12f))
                .border(1.dp, if (mine) C.line else k.copy(alpha = 0.4f), RoundedCornerShape(16.dp)).padding(horizontal = 14.dp, vertical = 10.dp),
        )
        extra?.let { Text(it, color = C.amber, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 3.dp)) }
    }
}

@Composable
private fun SendRow(enabled: Boolean, hint: String, onSend: (String) -> Unit) {
    var text by remember { mutableStateOf("") }
    Row(verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(text, { text = it.take(600) }, Modifier.weight(1f), label = { Text(hint) })
        Spacer(Modifier.width(8.dp))
        androidx.compose.material3.FilledIconButton({ onSend(text); text = "" }, Modifier.size(52.dp), enabled = enabled && text.isNotBlank(),
            colors = androidx.compose.material3.IconButtonDefaults.filledIconButtonColors(containerColor = C.amber, contentColor = Color.Black)) {
            androidx.compose.material3.Icon(androidx.compose.material.icons.Icons.AutoMirrored.Filled.Send, t("Αποστολή", "Send"))
        }
    }
}

@Composable
private fun Status(vm: GamesViewModel, who: Character?) {
    val busy by vm.busy.collectAsState()
    val err by vm.error.collectAsState()
    if (busy) Text(who?.let { t("${it.name} γράφει…", "${it.name} is typing…") } ?: t("Λίγο…", "One moment…"), color = C.muted, fontSize = 13.sp)
    err?.let { Text(it, color = C.danger, fontSize = 13.sp) }
}

/** «Κόντρα βρισιών»: 3 rounds, the AI scores both lines. */
@Composable
fun BattleScreen(onBack: () -> Unit, vm: GamesViewModel = viewModel()) {
    val lines by vm.battle.collectAsState()
    val busy by vm.busy.collectAsState()
    var c by remember { mutableStateOf(vm.battleWith) }
    Screen(t("Κόντρα βρισιών", "Insult battle"), onBack) {
        Text(t("3 γύροι. Πετάς μία ατάκα, ο χαρακτήρας απαντά, και το AI βαθμολογεί και τις δύο. Μετράει η εξυπνάδα, όχι η χυδαιότητα.", "3 rounds. You throw a line, the character answers, and the AI scores both. Wit counts, not crudeness."), color = C.muted, fontSize = 13.sp)
        Chips(c, { c = it; vm.battleStart(it) })
        lines.forEach { l -> Bubble(l, if (l.who != null) t("Εσύ ${l.user}/10 · ${l.who.name} ${l.me}/10", "You ${l.user}/10 · ${l.who.name} ${l.me}/10") else null) }
        Status(vm, c)
        if (vm.battleOver) {
            val (u, me) = vm.battleScore()
            Panel {
                Text(
                    when { u > me -> t("🏆 Κέρδισες! $u – $me", "🏆 You win! $u – $me"); u < me -> t("😤 Κέρδισε ο ${c.name}. $u – $me", "😤 ${c.name} wins. $u – $me"); else -> t("🤝 Ισοπαλία $u – $me", "🤝 Draw $u – $me") },
                    fontWeight = FontWeight.ExtraBold, fontSize = 20.sp, color = if (u > me) C.ok else C.amber,
                )
                TextButton({ vm.battleStart(c) }) { Text(t("Ρεβάνς", "Rematch"), color = C.amber) }
            }
        } else SendRow(!busy, t("Η ατάκα σου (γύρος ${lines.count { it.who != null } + 1}/3)", "Your line (round ${lines.count { it.who != null } + 1}/3)")) { vm.battleWith = c; vm.battleSend(it) }
    }
}

/** «Πρόβα»: rehearse a hard conversation, then a coach's tips. */
@Composable
fun RehearsalScreen(onBack: () -> Unit, vm: GamesViewModel = viewModel()) {
    val lines by vm.rehearsal.collectAsState()
    val coach by vm.coach.collectAsState()
    val busy by vm.busy.collectAsState()
    var started by remember { mutableStateOf(vm.scenario.isNotEmpty()) }
    var what by remember { mutableStateOf(vm.scenario) }
    var c by remember { mutableStateOf(vm.rehearsalWith) }
    Screen(t("Πρόβα δύσκολης κουβέντας", "Rehearse a hard talk"), onBack) {
        if (!started) {
            Text(t("Διάλεξε τι θα πεις και σε ποιον. Ο χαρακτήρας παίζει τον άλλον με αντίσταση· στο τέλος παίρνεις συμβουλές.", "Pick what you'll say and to whom. The character plays the other side with pushback; at the end you get tips."), color = C.muted, fontSize = 13.sp)
            GamePrompts.scenarios.forEach { s ->
                Text(s.text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(if (what == s.text) C.panel2 else C.panel)
                        .border(1.dp, if (what == s.text) C.amber else C.line, RoundedCornerShape(12.dp))
                        .clickable { what = s.text; Characters.byId(s.character)?.let { c = it } }.padding(12.dp))
            }
            OutlinedTextField(what, { what = it.take(300) }, Modifier.fillMaxWidth(), label = { Text(t("Ή γράψε το δικό σου", "Or write your own")) })
            Hud(t("ΠΟΙΟΣ ΠΑΙΖΕΙ ΤΟΝ ΑΛΛΟΝ", "WHO PLAYS THE OTHER SIDE"))
            Chips(c, { c = it })
            PrimaryButton(t("Ξεκίνα την πρόβα", "Start the rehearsal"), { vm.rehearsalStart(c, what); started = vm.scenario.isNotEmpty() }, Modifier.fillMaxWidth(), enabled = what.isNotBlank())
        } else {
            Text("«${vm.scenario}»", color = C.amber, fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold)
            lines.forEach { Bubble(it) }
            Status(vm, if (coach == null && lines.lastOrNull()?.who == null) c else null)
            coach?.let { Panel { Hud(t("ΣΥΜΒΟΥΛΕΣ", "TIPS")); Spacer(Modifier.width(4.dp)); Text(it, fontSize = 14.sp, lineHeight = 20.sp) } }
            if (coach == null) {
                SendRow(!busy, t("Τι λες;", "What do you say?")) { vm.rehearsalSend(it) }
                OutlinedButton({ vm.rehearsalEnd() }, Modifier.fillMaxWidth(), enabled = !busy && lines.any { it.who == null }) { Text(t("Τέλος και συμβουλές", "Finish and get tips"), color = C.text) }
            }
            TextButton({ started = false; vm.scenario = "" }) { Text(t("Νέα πρόβα", "New rehearsal"), color = C.amber) }
        }
    }
}

/** «Ομαδική κουβέντα»: two characters at once; their replies can be read aloud with two voices. */
@Composable
fun GroupScreen(onBack: () -> Unit, vm: GamesViewModel = viewModel()) {
    val context = LocalContext.current
    val lines by vm.group.collectAsState()
    val busy by vm.busy.collectAsState()
    var a by remember { mutableStateOf(vm.groupA) }
    var b by remember { mutableStateOf(vm.groupB) }
    var aloud by remember { mutableStateOf(true) }
    val tts = remember { mutableStateOf<TextToSpeech?>(null) }
    DisposableEffect(Unit) {
        var e: TextToSpeech? = null
        e = TextToSpeech(context) { ok -> if (ok == TextToSpeech.SUCCESS) tts.value = e }
        onDispose { e.shutdown() }
    }
    // Each new reply in the character's own voice (phone voice, by gender and voice choice).
    var spoken by remember { mutableStateOf(lines.size) }
    LaunchedEffect(lines.size) {
        val e = tts.value
        if (aloud && e != null) lines.drop(spoken).filter { it.who != null }.forEach { l ->
            val c = l.who!!
            PhoneVoice.setup(e, Locale.forLanguageTag(Lang.tag), Engine.settings.gender(c), Engine.settings.voiceIndex(c.id))
            e.speak(l.text, TextToSpeech.QUEUE_ADD, null, "g${l.hashCode()}")
        }
        spoken = lines.size
    }
    Screen(t("Ομαδική κουβέντα", "Group chat"), onBack) {
        Text(t("Δύο χαρακτήρες μαζί. Γράφεις, απαντούν και οι δύο (και πειράζονται μεταξύ τους).", "Two characters at once. You write, both answer (and tease each other)."), color = C.muted, fontSize = 13.sp)
        Chips(a, { a = it; vm.groupStart(a, b) }, exclude = b.id)
        Chips(b, { b = it; vm.groupStart(a, b) }, exclude = a.id)
        Row(verticalAlignment = Alignment.CenterVertically) {
            androidx.compose.material3.Switch(aloud, { aloud = it; if (!it) tts.value?.stop() })
            Spacer(Modifier.width(8.dp)); Text(t("Με φωνή (του κινητού, δύο φωνές)", "Aloud (phone voice, two voices)"), fontSize = 13.sp)
        }
        lines.forEach { Bubble(it) }
        Status(vm, null)
        SendRow(!busy, t("Γράψ' τα και στους δύο…", "Tell them both…")) { vm.groupA = a; vm.groupB = b; vm.groupSend(it) }
    }
}

/** «Περισσότεροι χαρακτήρες»: packs from the GitHub repository, added or removed with one tap. */
@Composable
fun PacksScreen(onBack: () -> Unit, vm: GamesViewModel = viewModel()) {
    val list by vm.available.collectAsState()
    val installed by vm.installed.collectAsState()
    val busy by vm.busy.collectAsState()
    val err by vm.error.collectAsState()
    LaunchedEffect(Unit) { if (vm.available.value == null) vm.fetchPacks() }
    Screen(t("Περισσότεροι χαρακτήρες", "More characters"), onBack) {
        Text(t("Νέοι χαρακτήρες από το GitHub του Binelik AI, χωρίς νέα έκδοση. Η λίστα κατεβαίνει μόνο όταν ανοίγεις αυτή τη σελίδα.", "New characters from Binelik AI's GitHub, without a new version. The list is downloaded only when you open this page."), color = C.muted, fontSize = 13.sp)
        if (busy) Text(t("Φόρτωση…", "Loading…"), color = C.muted)
        err?.let { Text(it, color = C.danger, fontSize = 13.sp); TextButton({ vm.fetchPacks() }) { Text(t("Ξανά", "Again"), color = C.amber) } }
        list?.forEach { p ->
            val c = Packs.character(p)
            val on = installed.any { it.id == p.id }
            Panel {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Dot(Color(c.color), 14); Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(c.name, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        Text(c.tagline, color = C.muted, fontSize = 12.5.sp)
                    }
                    TextButton({ vm.toggle(p) }) { Text(if (on) t("Αφαίρεση", "Remove") else t("Πρόσθεσε", "Add"), color = if (on) C.danger else C.amber) }
                }
            }
        }
        if (list?.isEmpty() == true) Text(t("Δεν υπάρχουν ακόμα πακέτα.", "No packs yet."), color = C.muted)
    }
}
