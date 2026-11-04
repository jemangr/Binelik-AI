package app.binelik.ui

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
            IconButton(onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Πίσω", tint = C.muted) }
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
    var otherTeam by remember { mutableStateOf(if (team in Characters.teams) "" else team) }
    var confirm by remember { mutableStateOf<Boolean?>(null) }

    Screen("Ρυθμίσεις", onBack) {
        Panel {
            Label("AI")
            Segmented(Provider.entries, provider, { it.label }, accent, {
                provider = it; vm.settings.provider = it
                if (!vm.keys.has(it)) onKey()
            })
            Spacer(Modifier.height(10.dp))
            Text(
                if (vm.keys.has(provider)) "✓ Υπάρχει κλειδί για το ${provider.label}, κρυπτογραφημένο στο κινητό." else "Δεν υπάρχει κλειδί για το ${provider.label}.",
                color = if (vm.keys.has(provider)) C.ok else C.warn, fontSize = 13.sp,
            )
            TextButton(onKey) { Text("Άλλαξε ή βάλε κλειδί", color = accent) }
        }
        Panel {
            Label("ΦΩΝΗ: ${c.name.uppercase().noAccents()}")
            Segmented(listOf(Gender.MALE, Gender.FEMALE), gender, { if (it == Gender.MALE) "Ανδρική" else "Γυναικεία" }, accent, {
                gender = it; vm.settings.setGender(c.id, it)
            })
            var vi by remember(c.id, gender) { mutableStateOf(vm.settings.voiceIndex(c.id)) }
            val names = app.binelik.ai.LiveProtocol.voices(gender)
            val current = app.binelik.ai.LiveProtocol.voiceFor(c, gender, vi)
            Spacer(Modifier.height(10.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                names.forEachIndexed { i, n ->
                    val on = n == current
                    Text(
                        "Φωνή ${i + 1}", color = if (on) accent else C.text, fontFamily = FontFamily.Monospace, fontSize = 12.sp,
                        modifier = Modifier.clip(RoundedCornerShape(10.dp)).background(C.panel2)
                            .border(1.dp, if (on) accent else C.line, RoundedCornerShape(10.dp))
                            .clickable(role = Role.RadioButton) { vi = i; vm.settings.setVoiceIndex(c.id, i) }
                            .semantics { selected = on }.padding(horizontal = 10.dp, vertical = 7.dp),
                    )
                }
            }
            Text("Τη φωνή την ακούς στο Live (Gemini). Η αλλαγή ισχύει από το επόμενο Play.", color = C.dim, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
        }
        Panel {
            Label("ΟΠΑΔΟΣ: Η ΟΜΑΔΑ ΤΟΥ")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Characters.teams.forEach { t ->
                    val on = t == team
                    Text(
                        t, color = if (on) Color.Black else C.text, fontWeight = FontWeight.SemiBold, fontSize = 13.sp,
                        modifier = Modifier.clip(RoundedCornerShape(50)).background(if (on) Color(0xFF84CC16) else C.panel2)
                            .border(1.dp, if (on) Color(0xFF84CC16) else C.line, RoundedCornerShape(50))
                            .clickable(role = Role.RadioButton) { team = t; otherTeam = ""; vm.settings.team = t; vm.refresh() }
                            .semantics { selected = on }.padding(horizontal = 12.dp, vertical = 8.dp),
                    )
                }
            }
            OutlinedTextField(
                otherTeam, { otherTeam = it.take(40); if (it.isNotBlank()) { team = it.trim(); vm.settings.team = it; vm.refresh() } },
                Modifier.fillMaxWidth().padding(top = 10.dp), singleLine = true, label = { Text("Άλλη ομάδα") },
            )
        }
        Panel {
            Label("Ο ΔΙΚΟΣ ΣΟΥ ΧΑΡΑΚΤΗΡΑΣ")
            Text(vm.settings.custom()?.name ?: "Δεν τον έχεις φτιάξει ακόμα.", color = C.muted, fontSize = 13.sp)
            TextButton(onCustom) { Text(if (vm.settings.custom() == null) "Φτιάξε τον" else "Άλλαξέ τον", color = accent) }
        }
        Panel {
            Label("ΚΟΥΒΕΝΤΕΣ")
            Text("Οι κουβέντες σου υπάρχουν μόνο σε αυτό το κινητό.", color = C.muted, fontSize = 13.sp)
            TextButton({ confirm = false }) { Text("Σβήσε τις κουβέντες με: ${c.name}", color = C.danger) }
            TextButton({ confirm = true }) { Text("Σβήσε όλες τις κουβέντες", color = C.danger) }
        }
        Panel {
            Label("ΣΧΕΤΙΚΑ")
            Text("Binelik AI ${BuildConfig.VERSION_NAME}", fontWeight = FontWeight.Bold)
            Text("Δημιουργός: jemangr · Ανοιχτός κώδικας, MIT license", color = C.muted, fontSize = 13.sp)
            TextButton({ openUrl(context, REPO) }) { Text("Ο κώδικας στο GitHub ↗", color = accent) }
            TextButton(onPrivacy) { Text("Πολιτική απορρήτου", color = accent) }
        }
    }
    confirm?.let { all ->
        AlertDialog(
            onDismissRequest = { confirm = null }, containerColor = C.panel,
            title = { Text(if (all) "Να σβηστούν όλες οι κουβέντες;" else "Να σβηστούν οι κουβέντες με: ${c.name};") },
            text = { Text("Δεν γυρίζουν πίσω.", color = C.muted) },
            confirmButton = { TextButton({ vm.clearHistory(all); confirm = null }) { Text("Σβήσιμο", color = C.danger) } },
            dismissButton = { TextButton({ confirm = null }) { Text("Άκυρο", color = C.muted) } },
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
    Screen("Ο δικός σου χαρακτήρας", onBack) {
        OutlinedTextField(name, { name = it.take(40) }, Modifier.fillMaxWidth(), singleLine = true, label = { Text("Όνομα, π.χ. Ο θείος Τάκης") })
        OutlinedTextField(
            desc, { desc = it.take(600) }, Modifier.fillMaxWidth().heightIn(min = 140.dp),
            label = { Text("Πώς μιλάει και τι κάνει") },
            supportingText = { Text("${desc.length}/600 · π.χ. «Τα ξέρει όλα, έχει άποψη για όλα, κάθε κουβέντα τη γυρίζει στο ποδόσφαιρο».") },
        )
        Panel {
            Label("ΦΩΝΗ")
            Segmented(listOf(Gender.MALE, Gender.FEMALE), gender, { if (it == Gender.MALE) "Ανδρική" else "Γυναικεία" }, accent, { gender = it })
            Spacer(Modifier.height(12.dp))
            Label("ΧΡΩΜΑ")
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                swatches.forEach { sw ->
                    Box(
                        Modifier.size(32.dp).clip(CircleShape).background(Color(sw))
                            .border(2.dp, if (sw == color) Color.White else Color.Transparent, CircleShape)
                            .clickable(role = Role.RadioButton) { color = sw }
                            .semantics { selected = sw == color; contentDescription = "Χρώμα" },
                    )
                }
            }
        }
        Text("Ισχύουν οι ίδιοι κανόνες ασφάλειας με όλους τους χαρακτήρες: χωρίς σεξουαλικό περιεχόμενο, χωρίς μίσος, χωρίς ενθάρρυνση βίας.", color = C.dim, fontSize = 12.sp)
        PrimaryButton("Αποθήκευση", {
            s.customName = name; s.customDescription = desc; s.customColor = color; s.setGender(Characters.CUSTOM_ID, gender)
            s.custom()?.let { vm.pick(it) }; onBack()
        }, enabled = desc.isNotBlank())
    }
}

@Composable
fun KeyScreen(vm: ChatViewModel, onBack: () -> Unit) {
    Screen("Κλειδί AI", onBack) { Box(Modifier.heightIn(max = 900.dp)) { KeyStep(vm, onSaved = onBack) } }
}

@Composable
fun PrivacyScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    Screen("Πολιτική απορρήτου", onBack) {
        Privacy.sections.forEach { (h, body) ->
            Panel {
                Text(h, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                Spacer(Modifier.height(6.dp))
                Text(body, color = C.muted, fontSize = 13.5.sp, lineHeight = 20.sp)
            }
        }
        Text(Privacy.UPDATED, color = C.dim, fontFamily = FontFamily.Monospace, fontSize = 11.sp)
        TextButton({ openUrl(context, "$REPO/blob/main/PRIVACY.md") }) { Text("Η ίδια πολιτική στο GitHub ↗", color = Color(0xFF4DA3FF)) }
    }
}

/** The privacy policy. The same text is in PRIVACY.md in the repository. */
object Privacy {
    const val UPDATED = "Τελευταία ενημέρωση: 4 Νοεμβρίου 2026"
    val sections = listOf(
        "Δεν κρατάμε τίποτα" to "Το Binelik AI δεν έχει δικό του server. Δεν υπάρχει λογαριασμός, εγγραφή, διαφημίσεις, analytics ή trackers. Ο δημιουργός δεν βλέπει τίποτα από όσα γράφεις.",
        "Οι κουβέντες σου" to "Μένουν μόνο στο κινητό σου. Δεν μπαίνουν ούτε στο αντίγραφο ασφαλείας του Android. Τις σβήνεις από τις Ρυθμίσεις, ή απεγκαθιστώντας την εφαρμογή.",
        "Το κλειδί σου" to "Μένει κρυπτογραφημένο στο κινητό σου (Android Keystore) και στέλνεται μόνο στην υπηρεσία που ανήκει.",
        "Τι στέλνεται και πού" to "Ό,τι γράφεις στέλνεται απευθείας από το κινητό σου στην υπηρεσία AI που διάλεξες, με το δικό σου κλειδί, και εκεί ισχύει η δική της πολιτική:\n• Google Gemini: με δωρεάν κλειδί, η Google μπορεί να χρησιμοποιεί τις κουβέντες για να βελτιώνει τα προϊόντα της και μπορεί να τις διαβάσουν άνθρωποι· με billing, όχι.\n• DeepSeek: οι κουβέντες πάνε στους servers του, στην Κίνα.",
        "Ο έλεγχος ασφάλειας" to "Ο έλεγχος για αυτοκτονία και για πραγματική πρόθεση βίας γίνεται στο κινητό σου και δεν στέλνεται πουθενά. Όταν πιάνει κάτι, το μήνυμα δεν πηγαίνει στο AI.",
        "Φωνή και μικρόφωνο" to "Το μικρόφωνο ζητιέται μόνο όταν πατήσεις Play στο Live. Η φωνή σου στέλνεται ζωντανά μόνο στο Google Gemini, με το δικό σου κλειδί, και δεν γράφεται πουθενά στο κινητό· κρατιέται μόνο το κείμενο της κουβέντας. Όταν η εφαρμογή φύγει από την οθόνη, το Live σταματά και το μικρόφωνο κλείνει.",
        "Άδειες" to "Internet (για το AI) και μικρόφωνο (μόνο για το Live). Τίποτα άλλο: ούτε επαφές, ούτε τοποθεσία, ούτε αρχεία.",
        "18+ και όχι ψυχοθεραπεία" to "Είναι για ενήλικες. Δεν είναι ψυχοθεραπεία ούτε ιατρική συμβουλή. Αν χρειάζεσαι βοήθεια: 1018 (24ωρη γραμμή για την αυτοκτονία) ή 112.",
        "Ανοιχτός κώδικας" to "Ο κώδικας είναι δημόσιος (MIT license) στο github.com/jemangr/Binelik-AI: μπορεί ο καθένας να ελέγξει όλα τα παραπάνω. Για ερωτήσεις, άνοιξε ένα issue εκεί.",
    )
}
