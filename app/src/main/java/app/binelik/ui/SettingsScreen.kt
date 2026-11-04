package app.binelik.ui

import app.binelik.i18n.t
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.binelik.BuildConfig
import app.binelik.ai.Provider
import app.binelik.data.Characters
import app.binelik.data.Gender

const val REPO = "https://github.com/jemangr/Binelik-AI"

@Composable
fun Screen(title: String, onBack: () -> Unit, content: @Composable () -> Unit) {
    Column(Modifier.fillMaxSize().background(C.bg).systemBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, t("Πίσω", "Back"), tint = C.muted) }
            Text(title, fontWeight = FontWeight.ExtraBold, fontSize = 19.sp)
        }
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            content(); Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun Label(t: String) = Hud(t, Modifier.padding(bottom = 8.dp))

@Composable
fun SettingsScreen(vm: ChatViewModel, onBack: () -> Unit, onKey: () -> Unit, onCustom: () -> Unit, onPrivacy: () -> Unit) {
    val context = LocalContext.current
    val c by vm.character.collectAsState()
    val accent = Color(c.color)
    var provider by remember { mutableStateOf(vm.settings.provider) }
    var gender by remember(c.id) { mutableStateOf(vm.settings.gender(c)) }
    var team by remember { mutableStateOf(vm.settings.team) }
    var otherTeam by remember { mutableStateOf(if (Characters.teams.any { it.el == team }) "" else team) }
    var confirm by remember { mutableStateOf<Boolean?>(null) }

    Screen(t("Ρυθμίσεις", "Settings"), onBack) {
        Panel {
            Label("AI")
            Segmented(Provider.entries, provider, { it.label }, accent, {
                provider = it; vm.settings.provider = it
                if (!vm.keys.has(it)) onKey()
            })
            Spacer(Modifier.height(10.dp))
            Text(
                if (vm.keys.has(provider)) t("✓ Υπάρχει κλειδί για το ${provider.label}, κρυπτογραφημένο στο κινητό.", "✓ There is a ${provider.label} key, encrypted on the phone.") else t("Δεν υπάρχει κλειδί για το ${provider.label}.", "There is no ${provider.label} key."),
                color = if (vm.keys.has(provider)) C.ok else C.warn, fontSize = 13.sp,
            )
            TextButton(onKey) { Text(t("Άλλαξε ή βάλε κλειδί", "Change or add the key"), color = accent) }
        }
        Panel {
            Label(t("ΓΛΩΣΣΑ / LANGUAGE", "LANGUAGE / ΓΛΩΣΣΑ"))
            Segmented(listOf(false, true), app.binelik.i18n.Lang.en, { if (it) "English" else "Ελληνικά" }, accent, {
                app.binelik.i18n.Lang.en = it; vm.settings.english = it; vm.refresh()
            })
            Text(t("Αλλάζουν και οι οθόνες και η γλώσσα που μιλάνε οι χαρακτήρες.", "Both the screens and the language the characters speak change."), color = C.dim, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
        }
        Panel {
            Label(t("ΦΩΝΗ: ${c.name.uppercase().noAccents()}", "VOICE: ${c.name.uppercase().noAccents()}"))
            Segmented(listOf(Gender.MALE, Gender.FEMALE), gender, { if (it == Gender.MALE) t("Ανδρική", "Male") else t("Γυναικεία", "Female") }, accent, {
                gender = it; vm.settings.setGender(c.id, it); vm.refresh()
            })
            var vi by remember(c.id, gender) { mutableStateOf(vm.settings.voiceIndex(c.id)) }
            val names = app.binelik.ai.LiveProtocol.voices(gender)
            val current = app.binelik.ai.LiveProtocol.voiceFor(c, gender, vi)
            Spacer(Modifier.height(10.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                names.forEachIndexed { i, n ->
                    val on = n == current
                    Text(
                        t("Φωνή ${i + 1}", "Voice ${i + 1}"), color = if (on) accent else C.text, fontFamily = FontFamily.Monospace, fontSize = 12.sp,
                        modifier = Modifier.clip(RoundedCornerShape(10.dp)).background(C.panel2)
                            .border(1.dp, if (on) accent else C.line, RoundedCornerShape(10.dp))
                            .clickable(role = Role.RadioButton) { vi = i; vm.settings.setVoiceIndex(c.id, i); vm.refresh() }
                            .semantics { selected = on }.padding(horizontal = 10.dp, vertical = 7.dp),
                    )
                }
            }
            TextButton({ app.binelik.voice.Engine.preview(c, gender, vi) }) { Text(t("▶ Άκου", "▶ Listen"), color = accent) }
            Text(t("Με το DeepSeek μιλάει η φωνή του κινητού, λίγο πιο βαριά ή πιο ψιλή. Η αλλαγή ισχύει αμέσως.", "With DeepSeek the phone's own voice speaks, a little lower or higher. The change applies at once."), color = C.dim, fontSize = 12.sp)
        }
        Panel {
            Label(t("ΟΠΑΔΟΣ: Η ΟΜΑΔΑ ΤΟΥ", "FOOTBALL FAN: HIS TEAM"))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Characters.teams.forEach { tm ->
                    val on = tm.el == team
                    Text(
                        tm.name, color = if (on) Color.Black else C.text, fontWeight = FontWeight.SemiBold, fontSize = 13.sp,
                        modifier = Modifier.clip(RoundedCornerShape(50)).background(if (on) Color(0xFF84CC16) else C.panel2)
                            .border(1.dp, if (on) Color(0xFF84CC16) else C.line, RoundedCornerShape(50))
                            .clickable(role = Role.RadioButton) { team = tm.el; otherTeam = ""; vm.settings.team = tm.el; vm.refresh() }
                            .semantics { selected = on }.padding(horizontal = 12.dp, vertical = 8.dp),
                    )
                }
            }
            OutlinedTextField(
                otherTeam, { otherTeam = it.take(40); if (it.isNotBlank()) { team = it.trim(); vm.settings.team = it; vm.refresh() } },
                Modifier.fillMaxWidth().padding(top = 10.dp), singleLine = true, label = { Text(t("Άλλη ομάδα", "Another team")) },
            )
        }
        Panel {
            Label(t("Ο ΔΙΚΟΣ ΣΟΥ ΧΑΡΑΚΤΗΡΑΣ", "YOUR OWN CHARACTER"))
            Text(vm.settings.custom()?.name ?: t("Δεν τον έχεις φτιάξει ακόμα.", "You haven't made it yet."), color = C.muted, fontSize = 13.sp)
            TextButton(onCustom) { Text(if (vm.settings.custom() == null) t("Φτιάξε τον", "Make it") else t("Άλλαξέ τον", "Change it"), color = accent) }
        }
        Panel {
            Label(t("ΚΟΥΒΕΝΤΕΣ", "CONVERSATIONS"))
            Text(t("Οι κουβέντες σου υπάρχουν μόνο σε αυτό το κινητό.", "Your conversations exist only on this phone."), color = C.muted, fontSize = 13.sp)
            TextButton({ confirm = false }) { Text(t("Σβήσε τις κουβέντες με: ${c.name}", "Delete the conversations with: ${c.name}"), color = C.danger) }
            TextButton({ confirm = true }) { Text(t("Σβήσε όλες τις κουβέντες", "Delete all conversations"), color = C.danger) }
        }
        Panel {
            Label(t("ΝΕΑ ΕΚΔΟΣΗ", "NEW VERSION"))
            var on by remember { mutableStateOf(vm.settings.checkUpdates) }
            val checking by vm.checking.collectAsState()
            val upd by vm.update.collectAsState()
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(t("Ειδοποίηση όταν βγαίνει νέα έκδοση", "Tell me when a new version is out"), modifier = Modifier.weight(1f), fontSize = 14.sp)
                androidx.compose.material3.Switch(on, { on = it; vm.settings.checkUpdates = it })
            }
            Text(t("Η εφαρμογή ρωτά το GitHub, το πολύ κάθε 6 ώρες, ποια είναι η τελευταία έκδοση. Δεν στέλνεται τίποτα δικό σου.", "The app asks GitHub, at most every 6 hours, which is the latest version. Nothing of yours is sent."), color = C.dim, fontSize = 12.sp)
            TextButton({ vm.checkUpdate(now = true) }, enabled = !checking) { Text(if (checking) t("Ελέγχω…", "Checking…") else t("Έλεγχος τώρα", "Check now"), color = accent) }
            val last = vm.settings.lastUpdateCheck
            if (!checking && last > 0) Text(
                upd?.let { t("Υπάρχει η ${it.version}: πάτα «Κατέβασμα» στην αρχική.", "${it.version} is out: press “Download” on the home screen.") }
                    ?: t("Έχεις την τελευταία έκδοση (${BuildConfig.VERSION_NAME}).", "You have the latest version (${BuildConfig.VERSION_NAME})."),
                color = C.muted, fontSize = 12.5.sp,
            )
        }
        Panel {
            Label(t("ΣΧΕΤΙΚΑ", "ABOUT"))
            Text("Binelik AI ${BuildConfig.VERSION_NAME}", fontWeight = FontWeight.Bold)
            Text(t("Δημιουργός: jemangr · Ανοιχτός κώδικας, MIT license", "Made by jemangr · Open source, MIT license"), color = C.muted, fontSize = 13.sp)
            TextButton({ openUrl(context, REPO) }) { Text(t("Ο κώδικας στο GitHub ↗", "The code on GitHub ↗"), color = accent) }
            TextButton(onPrivacy) { Text(t("Πολιτική απορρήτου", "Privacy policy"), color = accent) }
        }
    }
    confirm?.let { all ->
        AlertDialog(
            onDismissRequest = { confirm = null }, containerColor = C.panel,
            title = { Text(if (all) t("Να σβηστούν όλες οι κουβέντες;", "Delete all conversations?") else t("Να σβηστούν οι κουβέντες με: ${c.name};", "Delete the conversations with: ${c.name}?")) },
            text = { Text(t("Δεν γυρίζουν πίσω.", "They can't be brought back."), color = C.muted) },
            confirmButton = { TextButton({ vm.clearHistory(all); confirm = null }) { Text(t("Σβήσιμο", "Delete"), color = C.danger) } },
            dismissButton = { TextButton({ confirm = null }) { Text(t("Άκυρο", "Cancel"), color = C.muted) } },
        )
    }
}

/** Greek capitals never carry accents. */
fun String.noAccents(): String = java.text.Normalizer.normalize(this, java.text.Normalizer.Form.NFD).replace(Regex("\\p{Mn}+"), "")

private val swatches = listOf(0xFFFFB020, 0xFFFF3B3B, 0xFFFF5FA2, 0xFF3DDC84, 0xFF4DA3FF, 0xFFA66BFF, 0xFF22D3EE, 0xFFF5F5F5)

@Composable
fun CustomScreen(vm: ChatViewModel, onBack: () -> Unit) {
    val s = vm.settings
    var name by remember { mutableStateOf(s.customName) }
    var desc by remember { mutableStateOf(s.customDescription) }
    var color by remember { mutableStateOf(s.customColor) }
    var gender by remember { mutableStateOf(s.gender(Characters.custom("", "", 0, Gender.MALE))) }
    val accent = Color(color)
    Screen(t("Ο δικός σου χαρακτήρας", "Your own character"), onBack) {
        OutlinedTextField(name, { name = it.take(40) }, Modifier.fillMaxWidth(), singleLine = true, label = { Text(t("Όνομα, π.χ. Ο θείος Τάκης", "Name, e.g. Uncle Takis")) })
        OutlinedTextField(
            desc, { desc = it.take(600) }, Modifier.fillMaxWidth().heightIn(min = 140.dp),
            label = { Text(t("Πώς μιλάει και τι κάνει", "How it talks and what it does")) },
            supportingText = { Text(t("${desc.length}/600 · π.χ. «Τα ξέρει όλα, έχει άποψη για όλα, κάθε κουβέντα τη γυρίζει στο ποδόσφαιρο».", "${desc.length}/600 · e.g. “Knows it all, has an opinion on everything, turns every conversation to football”.")) },
        )
        Panel {
            Label(t("ΦΩΝΗ", "VOICE"))
            Segmented(listOf(Gender.MALE, Gender.FEMALE), gender, { if (it == Gender.MALE) t("Ανδρική", "Male") else t("Γυναικεία", "Female") }, accent, { gender = it })
            Spacer(Modifier.height(12.dp))
            Label(t("ΧΡΩΜΑ", "COLOUR"))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                swatches.forEach { sw ->
                    Box(
                        Modifier.size(32.dp).clip(CircleShape).background(Color(sw))
                            .border(2.dp, if (sw == color) Color.White else Color.Transparent, CircleShape)
                            .clickable(role = Role.RadioButton) { color = sw }
                            .semantics { selected = sw == color; contentDescription = t("Χρώμα", "Colour") },
                    )
                }
            }
        }
        Text(t("Ισχύουν οι ίδιοι κανόνες ασφάλειας με όλους τους χαρακτήρες: χωρίς σεξουαλικό περιεχόμενο, χωρίς μίσος, χωρίς ενθάρρυνση βίας.", "The same safety rules apply as for every character: no sexual content, no hate, no encouragement of violence."), color = C.dim, fontSize = 12.sp)
        PrimaryButton(t("Αποθήκευση", "Save"), {
            s.customName = name; s.customDescription = desc; s.customColor = color; s.setGender(Characters.CUSTOM_ID, gender)
            s.custom()?.let { vm.pick(it) }; onBack()
        }, enabled = desc.isNotBlank())
    }
}

@Composable
fun KeyScreen(vm: ChatViewModel, onBack: () -> Unit) {
    Screen(t("Κλειδί AI", "AI key"), onBack) { Box(Modifier.heightIn(max = 900.dp)) { KeyStep(vm, onSaved = onBack) } }
}

@Composable
fun PrivacyScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    Screen(t("Πολιτική απορρήτου", "Privacy policy"), onBack) {
        Privacy.sections.forEach { (h, body) ->
            Panel {
                Text(h, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                Spacer(Modifier.height(6.dp))
                Text(body, color = C.muted, fontSize = 13.5.sp, lineHeight = 20.sp)
            }
        }
        Text(Privacy.UPDATED, color = C.dim, fontFamily = FontFamily.Monospace, fontSize = 11.sp)
        TextButton({ openUrl(context, "$REPO/blob/main/PRIVACY.md") }) { Text(t("Η ίδια πολιτική στο GitHub ↗", "The same policy on GitHub ↗"), color = Color(0xFF4DA3FF)) }
    }
}

/** The privacy policy. The same text is in PRIVACY.md in the repository. */
object Privacy {
    val UPDATED = t("Τελευταία ενημέρωση: 4 Νοεμβρίου 2026", "Last updated: 4 November 2026")
    val sections get() = listOf(
        t("Δεν κρατάμε τίποτα", "We keep nothing") to t("Το Binelik AI δεν έχει δικό του server. Δεν υπάρχει λογαριασμός, εγγραφή, διαφημίσεις, analytics ή trackers. Ο δημιουργός δεν βλέπει τίποτα από όσα γράφεις.", "Binelik AI has no server of its own. There is no account, sign-up, ads, analytics or trackers. The maker sees nothing of what you write."),
        t("Οι κουβέντες σου", "Your conversations") to t("Μένουν μόνο στο κινητό σου. Δεν μπαίνουν ούτε στο αντίγραφο ασφαλείας του Android. Τις σβήνεις από τις Ρυθμίσεις, ή απεγκαθιστώντας την εφαρμογή.", "They stay on your phone only. They're not even in Android's backup. You delete them in the Settings, or by uninstalling the app."),
        t("Το κλειδί σου", "Your key") to t("Μένει κρυπτογραφημένο στο κινητό σου (Android Keystore) και στέλνεται μόνο στην υπηρεσία που ανήκει.", "It stays encrypted on your phone (Android Keystore) and is sent only to the service it belongs to."),
        t("Τι στέλνεται και πού", "What is sent, and where") to t("Ό,τι γράφεις στέλνεται απευθείας από το κινητό σου στην υπηρεσία AI που διάλεξες, με το δικό σου κλειδί, και εκεί ισχύει η δική της πολιτική:\n• Google Gemini: με δωρεάν κλειδί, η Google μπορεί να χρησιμοποιεί τις κουβέντες για να βελτιώνει τα προϊόντα της και μπορεί να τις διαβάσουν άνθρωποι· με billing, όχι.\n• DeepSeek: οι κουβέντες πάνε στους servers του, στην Κίνα.", "What you write is sent straight from your phone to the AI service you chose, with your own key, and its own policy applies there:\n• Google Gemini: with a free key, Google may use the conversations to improve its products and people may read them; with billing, not.\n• DeepSeek: the conversations go to its servers, in China."),
        t("Ο έλεγχος ασφάλειας", "The safety check") to t("Ο έλεγχος για αυτοκτονία και για πραγματική πρόθεση βίας γίνεται στο κινητό σου και δεν στέλνεται πουθενά. Όταν πιάνει κάτι, το μήνυμα δεν πηγαίνει στο AI.", "The check for suicide and for real intent of violence runs on your phone and isn't sent anywhere. When it catches something, the message doesn't go to the AI."),
        t("Φωνή και μικρόφωνο", "Voice and microphone") to t("Το μικρόφωνο ζητιέται μόνο όταν πατήσεις Play. Με το Gemini, η φωνή σου στέλνεται ζωντανά μόνο στο Google Gemini, με το δικό σου κλειδί. Με το DeepSeek, τη φωνή σου την ακούει η αναγνώριση ομιλίας του κινητού σου (συνήθως της Google) και στο DeepSeek πηγαίνει μόνο το κείμενο. Ήχος δεν γράφεται πουθενά· κρατιέται μόνο το κείμενο της κουβέντας. Όσο μιλάτε, μια ειδοποίηση δείχνει ότι το μικρόφωνο είναι ανοιχτό, με κουμπί Παύση.", "The microphone is asked for only when you press Play. With Gemini, your voice is streamed only to Google Gemini, with your own key. With DeepSeek, your phone's speech recognition (usually Google's) hears your voice and only the text goes to DeepSeek. No audio is recorded anywhere; only the text of the conversation is kept. While you talk, a notification shows the microphone is on, with a Pause button."),
        t("Έλεγχος για νέα έκδοση", "New version check") to t("Αν το αφήσεις ανοιχτό (Ρυθμίσεις), η εφαρμογή ρωτά το GitHub, το πολύ κάθε 6 ώρες, ποια είναι η τελευταία έκδοση. Το GitHub βλέπει μόνο ότι έγινε ένα αίτημα (και τη διεύθυνση IP, όπως κάθε site). Τίποτα από τις κουβέντες ή το κλειδί σου.", "If you leave it on (Settings), the app asks GitHub, at most every 6 hours, which is the latest version. GitHub sees only that a request was made (and the IP address, like any site). Nothing of your conversations or key."),
        t("Άδειες", "Permissions") to t("Internet (για το AI και τον έλεγχο νέας έκδοσης), μικρόφωνο (μόνο για τη φωνή) και ειδοποιήσεις (για το κουμπί Παύση). Τίποτα άλλο: ούτε επαφές, ούτε τοποθεσία, ούτε αρχεία.", "Internet (for the AI and the new version check), microphone (only for voice) and notifications (for the Pause button). Nothing else: no contacts, no location, no files."),
        t("18+ και όχι ψυχοθεραπεία", "18+ and not therapy") to t("Είναι για ενήλικες. Δεν είναι ψυχοθεραπεία ούτε ιατρική συμβουλή. Αν χρειάζεσαι βοήθεια: 1018 (24ωρη γραμμή για την αυτοκτονία) ή 112.", "It's for adults. It isn't therapy or medical advice. If you need help: 112, or your local crisis line (in Greece 1018, 24 hours)."),
        t("Ανοιχτός κώδικας", "Open source") to t("Ο κώδικας είναι δημόσιος (MIT license) στο github.com/jemangr/Binelik-AI: μπορεί ο καθένας να ελέγξει όλα τα παραπάνω. Για ερωτήσεις, άνοιξε ένα issue εκεί.", "The code is public (MIT license) at github.com/jemangr/Binelik-AI: anyone can check all of the above. For questions, open an issue there."),
    )
}
