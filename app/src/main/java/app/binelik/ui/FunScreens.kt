package app.binelik.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.binelik.data.Character
import app.binelik.data.Characters
import app.binelik.extras.Share
import app.binelik.i18n.t

/** «Παιχνίδια»: everything beyond the plain conversation. */
@Composable
fun FunScreen(vm: ChatViewModel, onBack: () -> Unit, onOpen: (String) -> Unit) {
    Screen(t("Παιχνίδια", "Games"), onBack) {
        FunCard("🥊", t("Καβγάς δύο χαρακτήρων", "Two characters fight"), t("Βάλε δύο να τσακωθούν και ρίξε λάδι στη φωτιά", "Make two fight and add fuel to the fire")) { onOpen("fight") }
        FunCard("✉️", t("Το email που δεν θα στείλεις", "The email you won't send"), t("Ο χαρακτήρας γράφει το θυμωμένο· με ένα κουμπί, το ευγενικό", "The character writes the angry one; one tap, the polite one")) { onOpen("email") }
        FunCard("🔥", t("Ψήσε με", "Roast me"), t("Ο χαρακτήρας σε κράζει με όσα του έχεις πει", "The character roasts you with what you told it")) { vm.roast(); onOpen("chat") }
        FunCard("🎲", t("Ζάρι", "Dice"), t("Τυχαίος χαρακτήρας, τυχαία ένταση", "Random character, random intensity")) { vm.dice(); onOpen("home") }
        FunCard("📊", t("Μπινελικόμετρο, θυμόμετρο, βραβεία", "Swear meter, anger meter, badges"), t("Τα στατιστικά σου, μόνο στο κινητό", "Your statistics, only on the phone")) { onOpen("stats") }
    }
}

@Composable
private fun FunCard(icon: String, title: String, sub: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(C.panel).border(1.dp, C.line, RoundedCornerShape(16.dp))
            .clickable(role = Role.Button, onClick = onClick).padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(icon, fontSize = 28.sp)
        Spacer(Modifier.width(14.dp))
        Column {
            Text(title, fontWeight = FontWeight.Bold, fontSize = 15.sp)
            Text(sub, color = C.muted, fontSize = 12.5.sp, lineHeight = 17.sp)
        }
    }
}

@Composable
private fun CharacterChips(selected: Character?, onPick: (Character) -> Unit, exclude: String? = null) {
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Characters.all.filter { it.id != exclude }.forEach { c ->
            val on = c.id == selected?.id
            val k = Color(c.color)
            Row(
                Modifier.clip(RoundedCornerShape(50)).background(if (on) k.copy(alpha = 0.18f) else C.panel2)
                    .border(1.dp, if (on) k else C.line, RoundedCornerShape(50))
                    .clickable(role = Role.RadioButton) { onPick(c) }.semantics { this.selected = on }
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) { Dot(k, 10); Spacer(Modifier.width(6.dp)); Text(c.name, fontSize = 13.sp, fontWeight = FontWeight.SemiBold) }
        }
    }
}

@Composable
fun FightScreen(vm: ChatViewModel, onBack: () -> Unit) {
    var a by remember { mutableStateOf(Characters.byId("mana")) }
    var b by remember { mutableStateOf(Characters.byId("gynaika")) }
    var topic by remember { mutableStateOf(t("Ποιος μαγειρεύει καλύτερα", "Who cooks better")) }
    var fuel by remember { mutableStateOf("") }
    val lines by vm.fight.collectAsState()
    val busy by vm.busy.collectAsState()
    val err by vm.error.collectAsState()
    Screen(t("Καβγάς δύο χαρακτήρων", "Two characters fight"), onBack) {
        Hud(t("ΠΡΩΤΟΣ", "FIRST")); CharacterChips(a, { a = it }, exclude = b?.id)
        Hud(t("ΔΕΥΤΕΡΟΣ", "SECOND")); CharacterChips(b, { b = it }, exclude = a?.id)
        OutlinedTextField(topic, { topic = it.take(200) }, Modifier.fillMaxWidth(), singleLine = true, label = { Text(t("Για ποιο θέμα;", "About what?")) })
        PrimaryButton(if (lines.isEmpty()) t("Ξεκίνα τον καβγά", "Start the fight") else t("Από την αρχή", "Start again"), { vm.fightStart(a!!, b!!, topic) }, enabled = !busy && a != null && b != null && topic.isNotBlank())
        lines.forEach { l ->
            val c = l.character
            val k = if (c != null) Color(c.color) else C.amber
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(k.copy(alpha = 0.1f)).border(1.dp, k.copy(alpha = 0.4f), RoundedCornerShape(14.dp)).padding(12.dp)) {
                Text(c?.name ?: t("Εσύ (λάδι στη φωτιά)", "You (fuel)"), color = k, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                Text(vm.shown(l.text), fontSize = 14.5.sp, lineHeight = 20.sp)
            }
        }
        if (busy) CircularProgressIndicator(Modifier.size(24.dp), color = C.amber, strokeWidth = 2.dp)
        err?.let { Text(it, color = C.danger, fontSize = 13.sp) }
        if (lines.isNotEmpty()) {
            OutlinedTextField(fuel, { fuel = it.take(300) }, Modifier.fillMaxWidth(), label = { Text(t("Ρίξε λάδι στη φωτιά (π.χ. «πες της για το φαγητό»)", "Add fuel (e.g. “tell her about the food”)")) })
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SecondaryButton(t("Λάδι!", "Fuel!"), { vm.fightFuel(fuel); fuel = "" }, Modifier.weight(1f))
                SecondaryButton(t("Συνέχεια", "Go on"), { vm.fightMore(2) }, Modifier.weight(1f))
            }
        }
    }
}

@Composable
fun EmailScreen(vm: ChatViewModel, onBack: () -> Unit) {
    val c by vm.character.collectAsState()
    val angry by vm.angryEmail.collectAsState()
    val polite by vm.politeEmail.collectAsState()
    val busy by vm.busy.collectAsState()
    val err by vm.error.collectAsState()
    var to by remember { mutableStateOf("") }
    var what by remember { mutableStateOf("") }
    val clip = LocalClipboardManager.current
    val context = LocalContext.current
    Screen(t("Το email που δεν θα στείλεις", "The email you won't send"), onBack) {
        Text(t("Γράφει: ${c.name} (αλλάζει από την αρχική)", "Written by: ${c.name} (change it on the home screen)"), color = Color(c.color), fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
        OutlinedTextField(to, { to = it.take(80) }, Modifier.fillMaxWidth(), singleLine = true, label = { Text(t("Προς ποιον; (π.χ. το αφεντικό)", "To whom? (e.g. the boss)")) })
        OutlinedTextField(what, { what = it.take(1500) }, Modifier.fillMaxWidth().heightIn(min = 120.dp), label = { Text(t("Τι έγινε;", "What happened?")) })
        PrimaryButton(t("Γράψε το θυμωμένο email", "Write the angry email"), { vm.writeAngry(to, what) }, enabled = !busy && what.isNotBlank())
        if (busy) CircularProgressIndicator(Modifier.size(24.dp), color = C.amber, strokeWidth = 2.dp)
        err?.let { Text(it, color = C.danger, fontSize = 13.sp) }
        if (angry.isNotBlank()) {
            Panel {
                Hud(t("ΤΟ ΘΥΜΩΜΕΝΟ (ΜΗΝ ΤΟ ΣΤΕΙΛΕΙΣ!)", "THE ANGRY ONE (DON'T SEND IT!)"))
                Spacer(Modifier.height(6.dp)); Text(vm.shown(angry), fontSize = 14.sp, lineHeight = 20.sp)
                Row { TextButton({ Share.shareText(context, vm.shown(angry)) }) { Text(t("Κοινοποίηση για πλάκα", "Share for fun"), color = C.muted) } }
            }
            PrimaryButton(t("Κάν' το ευγενικό", "Make it polite"), { vm.makePolite() }, enabled = !busy)
        }
        if (polite.isNotBlank()) Panel {
            Hud(t("ΤΟ ΕΥΓΕΝΙΚΟ (ΑΥΤΟ ΣΤΕΛΝΕΤΑΙ)", "THE POLITE ONE (THIS ONE YOU SEND)"))
            Spacer(Modifier.height(6.dp)); Text(polite, fontSize = 14.sp, lineHeight = 20.sp)
            Row {
                TextButton({ clip.setText(AnnotatedString(polite)) }) { Text(t("Αντιγραφή", "Copy"), color = C.ok) }
                TextButton({ Share.shareText(context, polite) }) { Text(t("Κοινοποίηση", "Share"), color = C.ok) }
            }
        }
    }
}

@Composable
fun StatsScreen(vm: ChatViewModel, onBack: () -> Unit) {
    LaunchedEffect(Unit) { vm.loadStats() }
    val st by vm.stats.collectAsState()
    val week by vm.weekText.collectAsState()
    val busy by vm.busy.collectAsState()
    Screen(t("Στατιστικά", "Statistics"), onBack) {
        val s = st ?: return@Screen
        Panel {
            Hud(t("ΜΠΙΝΕΛΙΚΟΜΕΤΡΟ", "SWEAR METER"))
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                Big("${s.swearsToday}", t("σήμερα", "today")); Big("${s.swearsWeek}", t("εβδομάδα", "week")); Big("${s.swearsTotal}", t("σύνολο", "total"))
            }
            s.topWord?.let { Text(t("Η λέξη της εβδομάδας: «${vm.shown(it)}»", "Word of the week: “${vm.shown(it)}”"), color = C.muted, fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp)) }
        }
        Panel {
            Hud(t("Η ΕΒΔΟΜΑΔΑ ΣΟΥ", "YOUR WEEK"))
            Text(t("${s.messagesWeek} μηνύματα · σερί ${s.streak} ημερών", "${s.messagesWeek} messages · ${s.streak}-day streak"), modifier = Modifier.padding(top = 6.dp))
            s.topCharacter?.let { Text(t("Ξέσπασες πιο πολύ με: $it", "You vented most with: $it"), color = C.muted, fontSize = 13.sp) }
            Text(week ?: t("Ζήτα μια αστεία σύνοψη: τα μηνύματά σου της εβδομάδας πάνε στο AI σου, μόνο τώρα.", "Ask for a funny summary: your messages of the week go to your AI, only now."), color = if (week != null) C.text else C.dim, fontSize = 13.5.sp, lineHeight = 19.sp, modifier = Modifier.padding(top = 8.dp))
            TextButton({ vm.summarizeWeek() }, enabled = !busy) { Text(if (busy) t("Γράφει…", "Writing…") else t("Σύνοψη εβδομάδας", "Summary of the week"), color = C.amber) }
        }
        Panel {
            Hud(t("ΘΥΜΟΜΕΤΡΟ", "ANGER METER"))
            val done = s.angerReadings
            if (done.isEmpty()) Text(t("Δεν υπάρχουν μετρήσεις αυτή την εβδομάδα. Μετράει πριν και μετά το Live.", "No readings this week. It measures before and after Live."), color = C.dim, fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp))
            else {
                Text(t("Κατά μέσο όρο ξεθύμανες %.1f βαθμούς".format(s.angerDrop ?: 0.0), "On average you cooled down %.1f points".format(s.angerDrop ?: 0.0)), modifier = Modifier.padding(vertical = 6.dp))
                Row(Modifier.fillMaxWidth().heightIn(min = 100.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.Bottom) {
                    done.takeLast(12).forEach { a ->
                        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                            Box(Modifier.width(8.dp).height((a.before * 9).dp).clip(RoundedCornerShape(3.dp)).background(C.danger))
                            Box(Modifier.width(8.dp).height((a.after * 9).dp).clip(RoundedCornerShape(3.dp)).background(C.ok))
                        }
                    }
                }
                Text(t("Κόκκινο: πριν · Πράσινο: μετά", "Red: before · Green: after"), color = C.dim, fontSize = 11.sp)
            }
        }
        Panel {
            Hud(t("ΒΡΑΒΕΙΑ", "BADGES"))
            s.badges.forEach { b ->
                Row(Modifier.padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(b.icon, fontSize = 24.sp, modifier = Modifier.widthIn(min = 36.dp), color = if (b.earned) Color.Unspecified else C.dim.copy(alpha = 0.4f))
                    Column(Modifier.weight(1f)) {
                        Text(b.title, fontWeight = FontWeight.Bold, color = if (b.earned) C.text else C.dim)
                        Text(b.how, color = C.dim, fontSize = 12.sp)
                    }
                    Text(if (b.earned) "✓" else "", color = C.ok, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun Big(n: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(n, fontSize = 30.sp, fontWeight = FontWeight.ExtraBold, color = C.amber)
        Text(label, color = C.muted, fontSize = 12.sp)
    }
}

/** The anger meter: 1–10, before or after. */
@Composable
fun AngerDialog(before: Boolean, accent: Color, onDone: (Int?) -> Unit) {
    var v by remember { mutableFloatStateOf(if (before) 7f else 3f) }
    androidx.compose.material3.AlertDialog(
        onDismissRequest = { onDone(null) }, containerColor = C.panel,
        title = { Text(if (before) t("Πόσο θυμωμένος είσαι τώρα;", "How angry are you now?") else t("Και τώρα;", "And now?"), fontWeight = FontWeight.ExtraBold) },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("${v.toInt()}", fontSize = 44.sp, fontWeight = FontWeight.ExtraBold, color = if (v >= 7) C.danger else if (v >= 4) C.amber else C.ok)
                Slider(v, { v = it }, valueRange = 1f..10f, steps = 8, colors = SliderDefaults.colors(thumbColor = accent, activeTrackColor = accent))
                Text(t("1 ζεν · 10 θα σπάσω κάτι", "1 zen · 10 about to break something"), color = C.dim, fontSize = 12.sp, textAlign = TextAlign.Center)
            }
        },
        confirmButton = { TextButton({ onDone(v.toInt()) }) { Text(t("ΟΚ", "OK"), color = accent) } },
        dismissButton = { TextButton({ onDone(null) }) { Text(t("Παράλειψη", "Skip"), color = C.muted) } },
    )
}

/** Share a character's line as a card, with or without the user's line before it. */
@Composable
fun ShareDialog(c: Character, line: String, mine: String?, onClose: () -> Unit) {
    val context = LocalContext.current
    var withMine by remember { mutableStateOf(false) }
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onClose, containerColor = C.panel,
        title = { Text(t("Κάρτα με την ατάκα", "Card with the line"), fontWeight = FontWeight.ExtraBold) },
        text = {
            Column {
                Text("«$line»", color = C.muted, fontSize = 13.5.sp, maxLines = 4)
                if (mine != null) Row(Modifier.padding(top = 10.dp).clickable { withMine = !withMine }, verticalAlignment = Alignment.CenterVertically) {
                    androidx.compose.material3.Checkbox(withMine, { withMine = it })
                    Text(t("Μαζί με το δικό μου μήνυμα", "With my own message"), fontSize = 13.5.sp)
                }
            }
        },
        confirmButton = { TextButton({ Share.shareCard(context, c, line, if (withMine) mine else null); onClose() }) { Text(t("Κοινοποίηση", "Share"), color = Color(c.color)) } },
        dismissButton = { TextButton(onClose) { Text(t("Άκυρο", "Cancel"), color = C.muted) } },
    )
}
