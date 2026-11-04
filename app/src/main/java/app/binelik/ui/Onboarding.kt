package app.binelik.ui

import app.binelik.i18n.t
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
            Spacer(Modifier.weight(1f)); Hud(t("ΒΗΜΑ $step / 3", "STEP $step / 3"))
        }
        Row(Modifier.fillMaxWidth().padding(bottom = 16.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            (1..3).forEach { Box(Modifier.weight(1f).height(4.dp).clip(RoundedCornerShape(2.dp)).background(if (it <= step) blue else C.line)) }
        }
        when (step) {
            1 -> AdultStep({ vm.settings.english = it }) { vm.settings.adult = true; step = 2 }
            2 -> KeyStep(vm, onSaved = { step = 3 })
            else -> FirstCharacterStep(vm, onDone)
        }
    }
}

@Composable
private fun AdultStep(onLanguage: (Boolean) -> Unit, onOk: () -> Unit) {
    var adult by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        // The language first, so everything after it is in the right one.
        Segmented(listOf(false, true), app.binelik.i18n.Lang.en, { if (it) "English" else "Ελληνικά" }, blue, { app.binelik.i18n.Lang.en = it; onLanguage(it) })
        Text(t("Καλώς ήρθες", "Welcome"), fontSize = 24.sp, fontWeight = FontWeight.ExtraBold)
        Text(
            t("Εδώ ξεσπάς: τα λες, βρίζεις, και ο χαρακτήρας που διαλέγεις απαντά με τον τρόπο του. Υπάρχουν βρισιές, γι' αυτό είναι μόνο για ενήλικες.", "Here you vent: you rant, you swear, and the character you pick answers in its own way. There's swearing, so it's for adults only."),
            color = C.muted, lineHeight = 21.sp,
        )
        Panel {
            Text(t("Να ξέρεις", "Good to know"), fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(6.dp))
            listOf(
                t("Δεν είναι ψυχοθεραπεία ούτε ιατρική συμβουλή.", "It isn't therapy or medical advice."),
                t("Αν μιλήσεις για αυτοκτονία, ή για πραγματική πρόθεση να βλάψεις κάποιον, ο χαρακτήρας σταματά και σου δίνει αριθμούς βοήθειας.", "If you talk about suicide, or a real intent to hurt someone, the character stops and gives you help numbers."),
                t("Δεν κρατάμε τίποτα: δεν έχουμε server, οι κουβέντες μένουν μόνο στο κινητό σου.", "We keep nothing: there's no server, conversations stay only on your phone."),
            ).forEach { Text("• $it", color = C.muted, fontSize = 13.5.sp, lineHeight = 19.sp, modifier = Modifier.padding(vertical = 3.dp)) }
        }
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).clickable { adult = !adult }.padding(vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Checkbox(adult, { adult = it }, colors = CheckboxDefaults.colors(checkedColor = blue, checkmarkColor = Color.Black))
            Text(t("Είμαι 18 ετών ή μεγαλύτερος/η", "I am 18 or older"), fontWeight = FontWeight.SemiBold)
        }
        Spacer(Modifier.weight(1f))
        PrimaryButton(t("Συνέχεια", "Continue"), onOk, enabled = adult, modifier = Modifier.padding(bottom = 18.dp))
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
        Text(t("Βάλε το κλειδί σου", "Add your key"), fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
        Segmented(
            Provider.entries, provider, { if (it == Provider.GEMINI) t("Gemini · δωρεάν", "Gemini · free") else "DeepSeek" }, blue,
            onPick = { provider = it; state = if (vm.keys.has(it)) KeyState.OK else KeyState.WAITING; shown = "" },
        )
        StepLine(1, t("Πάτα το κουμπί και φτιάξε κλειδί", "Press the button and make a key"),
            if (provider == Provider.GEMINI) t("Ανοίγει το Google AI Studio. Πατάς «Create API key» και μετά «Copy».", "Google AI Studio opens. Press “Create API key”, then “Copy”.")
            else t("Ανοίγει το DeepSeek Platform. Πατάς «Create new API key» και μετά «Copy». Το DeepSeek θέλει υπόλοιπο (π.χ. 2 $).", "DeepSeek Platform opens. Press “Create new API key”, then “Copy”. DeepSeek needs a balance (e.g. $2)."))
        PrimaryButton(if (provider == Provider.GEMINI) t("Άνοιγμα Google AI Studio ↗", "Open Google AI Studio ↗") else t("Άνοιγμα DeepSeek Platform ↗", "Open DeepSeek Platform ↗"), { openUrl(context, provider.keyPage) })
        StepLine(2, t("Γύρνα εδώ", "Come back here"), t("Μόλις το αντιγράψεις, το βάζουμε μόνοι μας. Δεν χρειάζεται επικόλληση.", "As soon as you copy it, we put it in ourselves. No need to paste."))
        KeyBox(state, shown)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton({ if (!fromClipboard()) manual = true }) { Text(t("Επικόλληση", "Paste"), color = blue) }
            TextButton({ manual = !manual }) { Text(t("Γράψε το με το χέρι", "Type it in"), color = C.muted) }
        }
        if (manual) {
            OutlinedTextField(
                typed, { typed = it.trim() }, Modifier.fillMaxWidth(), singleLine = true, label = { Text(t("Κλειδί ${provider.label}", "${provider.label} key")) },
                visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            )
            SecondaryButton(t("Δοκιμή κλειδιού", "Test the key"), { if (typed.length > 10) tryKey(typed) })
        }
        Text(
            t("Περισσότερες πληροφορίες και αναλυτικές οδηγίες", "More information and detailed instructions"), color = blue, fontWeight = FontWeight.SemiBold,
            textDecoration = TextDecoration.Underline, modifier = Modifier.clip(RoundedCornerShape(6.dp)).clickable { info = true }.padding(vertical = 6.dp),
        )
        Row(Modifier.fillMaxWidth()) {
            Box(Modifier.width(3.dp).heightIn(min = 40.dp).background(C.warn))
            Text(
                if (provider == Provider.GEMINI) t("Με δωρεάν κλειδί η Google μπορεί να χρησιμοποιεί τις κουβέντες για να βελτιώνει τα προϊόντα της. Αν σε νοιάζει, βάλε κλειδί με billing. Το κλειδί σου μένει κρυπτογραφημένο μόνο στο κινητό σου.", "With a free key Google may use the conversations to improve its products. If that matters to you, use a key with billing. Your key stays encrypted on your phone only.")
                else t("Οι κουβέντες πάνε στους servers του DeepSeek (Κίνα). Το κλειδί σου μένει κρυπτογραφημένο μόνο στο κινητό σου.", "Conversations go to DeepSeek's servers (China). Your key stays encrypted on your phone only."),
                color = C.muted, fontSize = 12.sp, lineHeight = 17.sp, modifier = Modifier.padding(start = 10.dp),
            )
        }
        Spacer(Modifier.weight(1f))
        PrimaryButton(t("Συνέχεια", "Continue"), onSaved, enabled = state == KeyState.OK, modifier = Modifier.padding(bottom = 18.dp))
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
                Text(t("Περιμένω να αντιγράψεις το κλειδί…", "Waiting for you to copy the key…"), color = C.muted, fontSize = 13.sp)
            }
            KeyState.TESTING -> Text(t("Δοκιμάζω το κλειδί…", "Testing the key…"), color = C.muted, fontSize = 13.sp)
            KeyState.OK -> Text(t("✓ Το κλειδί δουλεύει · αποθηκεύτηκε", "✓ The key works · saved"), color = C.ok, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            KeyState.BAD -> Text(t("Το κλειδί δεν δουλεύει. Αντέγραψέ το ξανά ολόκληρο.", "The key doesn't work. Copy all of it again."), color = C.danger, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
            KeyState.NET -> Text(t("Δεν υπάρχει σύνδεση για να το δοκιμάσω. Δοκίμασε ξανά.", "No connection to test it. Try again."), color = C.warn, fontSize = 13.sp)
        }
    }
}

@Composable
private fun KeyHelpDialog(provider: Provider, onClose: () -> Unit) {
    AlertDialog(
        onDismissRequest = onClose, containerColor = C.panel,
        confirmButton = { TextButton(onClose) { Text(t("Κλείσιμο", "Close"), color = blue) } },
        title = { Text(if (provider == Provider.GEMINI) t("Πώς βγάζω κλειδί Gemini", "How to get a Gemini key") else t("Πώς βγάζω κλειδί DeepSeek", "How to get a DeepSeek key"), fontWeight = FontWeight.ExtraBold) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                KeyHelp.steps(provider).forEachIndexed { i, s -> Text("${i + 1}. $s", color = C.muted, fontSize = 13.5.sp, lineHeight = 19.sp) }
                Text(t("Συχνές ερωτήσεις", "Questions"), fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp))
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
            t("Πάτα «Άνοιγμα Google AI Studio». Ανοίγει η σελίδα aistudio.google.com/apikey στον browser του κινητού.", "Press “Open Google AI Studio”. The page aistudio.google.com/apikey opens in the phone's browser."),
            t("Συνδέσου με τον λογαριασμό Google σου (το Gmail σου). Αν δεν έχεις, φτιάχνεις έναν δωρεάν.", "Sign in with your Google account (your Gmail). If you have none, make one for free."),
            t("Την πρώτη φορά σε ρωτά να δεχτείς τους όρους. Τσέκαρε το κουτάκι και πάτα «Continue».", "The first time it asks you to accept the terms. Tick the box and press “Continue”."),
            t("Πάτα το μπλε κουμπί «Create API key». Αν σε ρωτήσει για project, διάλεξε «Create API key in new project».", "Press the blue “Create API key” button. If it asks for a project, choose “Create API key in new project”."),
            t("Εμφανίζεται ένα μακρύ κλειδί που ξεκινά με «AIza». Πάτα «Copy».", "A long key starting with “AIza” appears. Press “Copy”."),
            t("Γύρνα στο Binelik AI. Το κλειδί μπαίνει μόνο του και δοκιμάζεται. Όταν δεις «Το κλειδί δουλεύει ✓», τελείωσες.", "Come back to Binelik AI. The key goes in by itself and is tested. When you see “The key works ✓”, you're done."),
        )
        Provider.DEEPSEEK -> listOf(
            t("Το DeepSeek δεν είναι δωρεάν: βάζεις πρώτα υπόλοιπο (π.χ. 2 $). Για κείμενο κρατά πολύ, γιατί η χρέωση είναι πολύ μικρή.", "DeepSeek isn't free: you first add a balance (e.g. $2). For text it lasts a long time, as the charges are very small."),
            t("Πάτα «Άνοιγμα DeepSeek Platform». Ανοίγει το platform.deepseek.com.", "Press “Open DeepSeek Platform”. platform.deepseek.com opens."),
            t("Κάνε εγγραφή με email ή με τον λογαριασμό Google σου.", "Sign up with an email or your Google account."),
            t("Πάτα «Top up» και βάλε υπόλοιπο με κάρτα ή PayPal.", "Press “Top up” and add a balance by card or PayPal."),
            t("Πήγαινε στο «API keys» και πάτα «Create new API key». Δώσε όνομα, π.χ. «Binelik», και πάτα «Create».", "Go to “API keys” and press “Create new API key”. Give it a name, e.g. “Binelik”, and press “Create”."),
            t("Πάτα «Copy». Το κλειδί ξεκινά με «sk-» και φαίνεται μόνο αυτή τη φορά.", "Press “Copy”. The key starts with “sk-” and is shown only this once."),
            t("Γύρνα στο Binelik AI. Μπαίνει μόνο του και δοκιμάζεται.", "Come back to Binelik AI. It goes in by itself and is tested."),
        )
    }

    fun faq(p: Provider): List<Pair<String, String>> = when (p) {
        Provider.GEMINI -> listOf(
            t("Κοστίζει κάτι;", "Does it cost anything?") to t("Όχι. Το δωρεάν κλειδί έχει όρια ανά λεπτό και ανά μέρα. Αν τα φτάσεις, η εφαρμογή σου το λέει και περιμένεις λίγο ή ως την επόμενη μέρα. Δεν χρειάζεται κάρτα.", "No. The free key has limits per minute and per day. If you reach them, the app tells you and you wait a little or until the next day. No card needed."),
            t("Τι κάνει η Google με τις κουβέντες;", "What does Google do with the conversations?") to t("Με δωρεάν κλειδί, η Google μπορεί να χρησιμοποιεί τις κουβέντες για να βελτιώνει τα προϊόντα της, και μπορεί να τις διαβάσουν άνθρωποι. Αν σε νοιάζει, ενεργοποίησε billing στο ίδιο project: τότε δεν χρησιμοποιούνται.", "With a free key, Google may use the conversations to improve its products, and people may read them. If that matters to you, turn on billing in the same project: then they aren't used."),
            t("Δεν μπήκε μόνο του", "It didn't go in by itself") to t("Πάτα «Επικόλληση», ή «Γράψε το με το χέρι». Σιγουρέψου ότι αντέγραψες όλο το κλειδί (ξεκινά με AIza).", "Press “Paste”, or “Type it in”. Make sure you copied the whole key (it starts with AIza)."),
            t("Γράφει «Το κλειδί δεν δουλεύει»", "It says “The key doesn't work”") to t("Έλεγξε ότι έχεις internet, ότι δεν έλειψε κάποιο γράμμα, και ότι δεν έσβησες το κλειδί στο AI Studio. Φτιάξε νέο αν χρειαστεί.", "Check you have internet, that no letter is missing, and that you didn't delete the key in AI Studio. Make a new one if needed."),
            t("Είναι ασφαλές;", "Is it safe?") to t("Το κλειδί μένει κρυπτογραφημένο μόνο στο κινητό σου και πηγαίνει μόνο στην Google. Μην το δίνεις σε κανέναν. Αν διαρρεύσει, σβήσ' το στο AI Studio και φτιάξε νέο. Μετά την αποθήκευση σβήνουμε το κλειδί από το πρόχειρο.", "The key stays encrypted on your phone only and goes only to Google. Don't give it to anyone. If it leaks, delete it in AI Studio and make a new one. After saving, we clear the key from the clipboard."),
        )
        Provider.DEEPSEEK -> listOf(
            t("Έχει φωνή;", "Does it have a voice?") to t("Το DeepSeek δίνει μόνο κείμενο. Η φωνή θα είναι του κινητού σου, χωρίς πραγματικό Live. Για Live, βάλε Gemini.", "DeepSeek gives only text. The voice is your phone's own, without real Live. For Live, use Gemini."),
            t("Πού πάνε οι κουβέντες;", "Where do the conversations go?") to t("Στους servers του DeepSeek, στην Κίνα, σύμφωνα με την πολιτική απορρήτου τους.", "To DeepSeek's servers, in China, under their privacy policy."),
            t("Γράφει ότι τέλειωσε το υπόλοιπο", "It says the balance ran out") to t("Μπες στο platform.deepseek.com και πάτα «Top up».", "Go to platform.deepseek.com and press “Top up”."),
            t("Είναι ασφαλές;", "Is it safe?") to t("Το κλειδί μένει κρυπτογραφημένο μόνο στο κινητό σου. Αν διαρρεύσει, σβήσ' το από το «API keys» και φτιάξε νέο.", "The key stays encrypted on your phone only. If it leaks, delete it under “API keys” and make a new one."),
        )
    }
}

@Composable
private fun FirstCharacterStep(vm: ChatViewModel, onDone: () -> Unit) {
    Column(Modifier.fillMaxSize()) {
        Text(t("Με ποιον θα τα πεις;", "Who do you want to talk to?"), fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
        Spacer(Modifier.height(8.dp))
        CharacterList(vm, Modifier.weight(1f)) { vm.pick(it); onDone() }
    }
}
