package app.binelik.ui

import android.content.ClipboardManager
import android.view.ViewTreeObserver
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.binelik.ai.Ai
import app.binelik.ai.AiResult
import app.binelik.ai.Provider
import app.binelik.ai.Reason
import app.binelik.data.KeyVault
import kotlinx.coroutines.launch

private val blue = Color(0xFF4DA3FF)

@Composable
fun Onboarding(vm: ChatViewModel, onDone: () -> Unit) {
    var step by remember { mutableStateOf(if (vm.settings.adult) 2 else 1) }
    Column(Modifier.fillMaxSize().background(C.bg).systemBarsPadding().padding(horizontal = 20.dp)) {
        Row(Modifier.fillMaxWidth().padding(vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Logo(30.dp); Spacer(Modifier.width(8.dp))
            Text("Binelik AI", fontWeight = FontWeight.ExtraBold, fontSize = 17.sp)
            Spacer(Modifier.weight(1f)); Hud("ΒΗΜΑ $step / 3")
        }
        Row(Modifier.fillMaxWidth().padding(bottom = 16.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            (1..3).forEach { Box(Modifier.weight(1f).height(4.dp).clip(RoundedCornerShape(2.dp)).background(if (it <= step) blue else C.line)) }
        }
        when (step) {
            1 -> AdultStep { vm.settings.adult = true; step = 2 }
            2 -> KeyStep(vm, onSaved = { step = 3 })
            else -> FirstCharacterStep(vm, onDone)
        }
    }
}

@Composable
private fun AdultStep(onOk: () -> Unit) {
    var adult by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Καλώς ήρθες", fontSize = 24.sp, fontWeight = FontWeight.ExtraBold)
        Text(
            "Εδώ ξεσπάς: τα λες, βρίζεις, και ο χαρακτήρας που διαλέγεις απαντά με τον τρόπο του. Υπάρχουν βρισιές, γι' αυτό είναι μόνο για ενήλικες.",
            color = C.muted, lineHeight = 21.sp,
        )
        Panel {
            Text("Να ξέρεις", fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(6.dp))
            listOf(
                "Δεν είναι ψυχοθεραπεία ούτε ιατρική συμβουλή.",
                "Αν μιλήσεις για αυτοκτονία, ή για πραγματική πρόθεση να βλάψεις κάποιον, ο χαρακτήρας σταματά και σου δίνει αριθμούς βοήθειας.",
                "Δεν κρατάμε τίποτα: δεν έχουμε server, οι κουβέντες μένουν μόνο στο κινητό σου.",
            ).forEach { Text("• $it", color = C.muted, fontSize = 13.5.sp, lineHeight = 19.sp, modifier = Modifier.padding(vertical = 3.dp)) }
        }
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).clickable { adult = !adult }.padding(vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Checkbox(adult, { adult = it }, colors = CheckboxDefaults.colors(checkedColor = blue, checkmarkColor = Color.Black))
            Text("Είμαι 18 ετών ή μεγαλύτερος/η", fontWeight = FontWeight.SemiBold)
        }
        Spacer(Modifier.weight(1f))
        PrimaryButton("Συνέχεια", onOk, enabled = adult, modifier = Modifier.padding(bottom = 18.dp))
    }
}

private enum class KeyState { WAITING, TESTING, OK, BAD, NET }

/** The key, as simple as it gets: a button to the key page, then the app picks the copied key up by itself. */
@Composable
fun KeyStep(vm: ChatViewModel, onSaved: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var provider by remember { mutableStateOf(vm.settings.provider) }
    var state by remember { mutableStateOf(if (vm.keys.has(provider)) KeyState.OK else KeyState.WAITING) }
    var shown by remember { mutableStateOf("") }
    var manual by remember { mutableStateOf(false) }
    var typed by remember { mutableStateOf("") }
    var info by remember { mutableStateOf(false) }
    val cm = remember { context.getSystemService(ClipboardManager::class.java) }

    fun mask(k: String) = k.take(4) + "•".repeat(12) + k.takeLast(3)

    fun tryKey(key: String) {
        shown = mask(key); state = KeyState.TESTING
        scope.launch {
            when (val r = Ai.testKey(provider, key)) {
                is AiResult.Ok -> {
                    vm.keys.save(provider, key); vm.settings.provider = provider; state = KeyState.OK
                    // So it is not pasted somewhere else by mistake.
                    runCatching { cm?.clearPrimaryClip() }
                }
                is AiResult.Fail -> state = if (r.reason == Reason.NETWORK) KeyState.NET else KeyState.BAD
            }
        }
    }

    fun fromClipboard(): Boolean {
        val text = runCatching { cm?.primaryClip?.getItemAt(0)?.coerceToText(context)?.toString() }.getOrNull()
        val key = provider.findKey(text) ?: return false
        if (state == KeyState.TESTING || (state == KeyState.OK && shown == mask(key))) return true
        tryKey(key); return true
    }

    // Android lets an app read the clipboard only while it has the focus: check each time we get it back.
    val view = LocalView.current
    val check by rememberUpdatedState { if (state != KeyState.OK || shown.isNotEmpty()) fromClipboard() }
    DisposableEffect(view) {
        val l = ViewTreeObserver.OnWindowFocusChangeListener { has -> if (has) check() }
        view.viewTreeObserver.addOnWindowFocusChangeListener(l)
        onDispose { view.viewTreeObserver.removeOnWindowFocusChangeListener(l) }
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Βάλε το κλειδί σου", fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
        Segmented(
            Provider.entries, provider, { if (it == Provider.GEMINI) "Gemini · δωρεάν" else "DeepSeek" }, blue,
            onPick = { provider = it; state = if (vm.keys.has(it)) KeyState.OK else KeyState.WAITING; shown = "" },
        )
        StepLine(1, "Πάτα το κουμπί και φτιάξε κλειδί",
            if (provider == Provider.GEMINI) "Ανοίγει το Google AI Studio. Πατάς «Create API key» και μετά «Copy»."
            else "Ανοίγει το DeepSeek Platform. Πατάς «Create new API key» και μετά «Copy». Το DeepSeek θέλει υπόλοιπο (π.χ. 2 $).")
        PrimaryButton(if (provider == Provider.GEMINI) "Άνοιγμα Google AI Studio ↗" else "Άνοιγμα DeepSeek Platform ↗", { openUrl(context, provider.keyPage) })
        StepLine(2, "Γύρνα εδώ", "Μόλις το αντιγράψεις, το βάζουμε μόνοι μας. Δεν χρειάζεται επικόλληση.")
        KeyBox(state, shown)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton({ if (!fromClipboard()) manual = true }) { Text("Επικόλληση", color = blue) }
            TextButton({ manual = !manual }) { Text("Γράψε το με το χέρι", color = C.muted) }
        }
        if (manual) {
            OutlinedTextField(
                typed, { typed = it.trim() }, Modifier.fillMaxWidth(), singleLine = true, label = { Text("Κλειδί ${provider.label}") },
                visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            )
            SecondaryButton("Δοκιμή κλειδιού", { if (typed.length > 10) tryKey(typed) })
        }
        Text(
            "Περισσότερες πληροφορίες και αναλυτικές οδηγίες", color = blue, fontWeight = FontWeight.SemiBold,
            textDecoration = TextDecoration.Underline, modifier = Modifier.clip(RoundedCornerShape(6.dp)).clickable { info = true }.padding(vertical = 6.dp),
        )
        Row(Modifier.fillMaxWidth()) {
            Box(Modifier.width(3.dp).heightIn(min = 40.dp).background(C.warn))
            Text(
                if (provider == Provider.GEMINI) "Με δωρεάν κλειδί η Google μπορεί να χρησιμοποιεί τις κουβέντες για να βελτιώνει τα προϊόντα της. Αν σε νοιάζει, βάλε κλειδί με billing. Το κλειδί σου μένει κρυπτογραφημένο μόνο στο κινητό σου."
                else "Οι κουβέντες πάνε στους servers του DeepSeek (Κίνα). Το κλειδί σου μένει κρυπτογραφημένο μόνο στο κινητό σου.",
                color = C.muted, fontSize = 12.sp, lineHeight = 17.sp, modifier = Modifier.padding(start = 10.dp),
            )
        }
        Spacer(Modifier.weight(1f))
        PrimaryButton("Συνέχεια", onSaved, enabled = state == KeyState.OK, modifier = Modifier.padding(bottom = 18.dp))
    }
    if (info) KeyHelpDialog(provider) { info = false }
}

@Composable
private fun StepLine(n: Int, title: String, body: String) {
    Row(verticalAlignment = Alignment.Top) {
        Box(Modifier.size(26.dp).clip(CircleShape).background(blue), contentAlignment = Alignment.Center) {
            Text("$n", color = Color.Black, fontWeight = FontWeight.ExtraBold, fontSize = 13.sp)
        }
        Spacer(Modifier.width(12.dp))
        Column {
            Text(title, fontWeight = FontWeight.Bold, fontSize = 15.sp)
            Text(body, color = C.muted, fontSize = 13.sp, lineHeight = 18.sp)
        }
    }
}

@Composable
private fun KeyBox(state: KeyState, shown: String) {
    val ok = state == KeyState.OK
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(if (ok) C.ok.copy(alpha = 0.07f) else Color.Transparent)
            .border(1.dp, if (ok) C.ok else C.line, RoundedCornerShape(14.dp)).padding(12.dp)
            .semantics { liveRegion = LiveRegionMode.Polite },
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        if (shown.isNotEmpty()) Text(shown, fontFamily = FontFamily.Monospace, fontSize = 12.5.sp)
        when (state) {
            KeyState.WAITING -> Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(Modifier.size(14.dp), color = blue, strokeWidth = 2.dp); Spacer(Modifier.width(8.dp))
                Text("Περιμένω να αντιγράψεις το κλειδί…", color = C.muted, fontSize = 13.sp)
            }
            KeyState.TESTING -> Text("Δοκιμάζω το κλειδί…", color = C.muted, fontSize = 13.sp)
            KeyState.OK -> Text("✓ Το κλειδί δουλεύει · αποθηκεύτηκε", color = C.ok, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            KeyState.BAD -> Text("Το κλειδί δεν δουλεύει. Αντέγραψέ το ξανά ολόκληρο.", color = C.danger, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
            KeyState.NET -> Text("Δεν υπάρχει σύνδεση για να το δοκιμάσω. Δοκίμασε ξανά.", color = C.warn, fontSize = 13.sp)
        }
    }
}

@Composable
private fun KeyHelpDialog(provider: Provider, onClose: () -> Unit) {
    AlertDialog(
        onDismissRequest = onClose, containerColor = C.panel,
        confirmButton = { TextButton(onClose) { Text("Κλείσιμο", color = blue) } },
        title = { Text(if (provider == Provider.GEMINI) "Πώς βγάζω κλειδί Gemini" else "Πώς βγάζω κλειδί DeepSeek", fontWeight = FontWeight.ExtraBold) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                KeyHelp.steps(provider).forEachIndexed { i, s -> Text("${i + 1}. $s", color = C.muted, fontSize = 13.5.sp, lineHeight = 19.sp) }
                Text("Συχνές ερωτήσεις", fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp))
                KeyHelp.faq(provider).forEach { (q, a) ->
                    Text(q, fontWeight = FontWeight.SemiBold, fontSize = 13.5.sp)
                    Text(a, color = C.muted, fontSize = 13.sp, lineHeight = 18.sp)
                }
            }
        },
    )
}

/** The detailed instructions behind «Περισσότερες πληροφορίες». */
object KeyHelp {
    fun steps(p: Provider): List<String> = when (p) {
        Provider.GEMINI -> listOf(
            "Πάτα «Άνοιγμα Google AI Studio». Ανοίγει η σελίδα aistudio.google.com/apikey στον browser του κινητού.",
            "Συνδέσου με τον λογαριασμό Google σου (το Gmail σου). Αν δεν έχεις, φτιάχνεις έναν δωρεάν.",
            "Την πρώτη φορά σε ρωτά να δεχτείς τους όρους. Τσέκαρε το κουτάκι και πάτα «Continue».",
            "Πάτα το μπλε κουμπί «Create API key». Αν σε ρωτήσει για project, διάλεξε «Create API key in new project».",
            "Εμφανίζεται ένα μακρύ κλειδί που ξεκινά με «AIza». Πάτα «Copy».",
            "Γύρνα στο Binelik AI. Το κλειδί μπαίνει μόνο του και δοκιμάζεται. Όταν δεις «Το κλειδί δουλεύει ✓», τελείωσες.",
        )
        Provider.DEEPSEEK -> listOf(
            "Το DeepSeek δεν είναι δωρεάν: βάζεις πρώτα υπόλοιπο (π.χ. 2 $). Για κείμενο κρατά πολύ, γιατί η χρέωση είναι πολύ μικρή.",
            "Πάτα «Άνοιγμα DeepSeek Platform». Ανοίγει το platform.deepseek.com.",
            "Κάνε εγγραφή με email ή με τον λογαριασμό Google σου.",
            "Πάτα «Top up» και βάλε υπόλοιπο με κάρτα ή PayPal.",
            "Πήγαινε στο «API keys» και πάτα «Create new API key». Δώσε όνομα, π.χ. «Binelik», και πάτα «Create».",
            "Πάτα «Copy». Το κλειδί ξεκινά με «sk-» και φαίνεται μόνο αυτή τη φορά.",
            "Γύρνα στο Binelik AI. Μπαίνει μόνο του και δοκιμάζεται.",
        )
    }

    fun faq(p: Provider): List<Pair<String, String>> = when (p) {
        Provider.GEMINI -> listOf(
            "Κοστίζει κάτι;" to "Όχι. Το δωρεάν κλειδί έχει όρια ανά λεπτό και ανά μέρα. Αν τα φτάσεις, η εφαρμογή σου το λέει και περιμένεις λίγο ή ως την επόμενη μέρα. Δεν χρειάζεται κάρτα.",
            "Τι κάνει η Google με τις κουβέντες;" to "Με δωρεάν κλειδί, η Google μπορεί να χρησιμοποιεί τις κουβέντες για να βελτιώνει τα προϊόντα της, και μπορεί να τις διαβάσουν άνθρωποι. Αν σε νοιάζει, ενεργοποίησε billing στο ίδιο project: τότε δεν χρησιμοποιούνται.",
            "Δεν μπήκε μόνο του" to "Πάτα «Επικόλληση», ή «Γράψε το με το χέρι». Σιγουρέψου ότι αντέγραψες όλο το κλειδί (ξεκινά με AIza).",
            "Γράφει «Το κλειδί δεν δουλεύει»" to "Έλεγξε ότι έχεις internet, ότι δεν έλειψε κάποιο γράμμα, και ότι δεν έσβησες το κλειδί στο AI Studio. Φτιάξε νέο αν χρειαστεί.",
            "Είναι ασφαλές;" to "Το κλειδί μένει κρυπτογραφημένο μόνο στο κινητό σου και πηγαίνει μόνο στην Google. Μην το δίνεις σε κανέναν. Αν διαρρεύσει, σβήσ' το στο AI Studio και φτιάξε νέο. Μετά την αποθήκευση σβήνουμε το κλειδί από το πρόχειρο.",
        )
        Provider.DEEPSEEK -> listOf(
            "Έχει φωνή;" to "Το DeepSeek δίνει μόνο κείμενο. Η φωνή θα είναι του κινητού σου, χωρίς πραγματικό Live. Για Live, βάλε Gemini.",
            "Πού πάνε οι κουβέντες;" to "Στους servers του DeepSeek, στην Κίνα, σύμφωνα με την πολιτική απορρήτου τους.",
            "Γράφει ότι τέλειωσε το υπόλοιπο" to "Μπες στο platform.deepseek.com και πάτα «Top up».",
            "Είναι ασφαλές;" to "Το κλειδί μένει κρυπτογραφημένο μόνο στο κινητό σου. Αν διαρρεύσει, σβήσ' το από το «API keys» και φτιάξε νέο.",
        )
    }
}

@Composable
private fun FirstCharacterStep(vm: ChatViewModel, onDone: () -> Unit) {
    Column(Modifier.fillMaxSize()) {
        Text("Με ποιον θα τα πεις;", fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
        Spacer(Modifier.height(8.dp))
        CharacterList(vm, Modifier.weight(1f)) { vm.pick(it); onDone() }
    }
}
