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
import androidx.compose.foundation.layout.imePadding
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
    Column(Modifier.fillMaxSize().background(C.bg).systemBarsPadding().imePadding()) {
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
fun SwitchRow(title: String, sub: String, initial: Boolean, onChange: (Boolean) -> Unit) {
    var on by remember { mutableStateOf(initial) }
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable(role = Role.Switch) { on = !on; onChange(on) }.padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 14.5.sp, fontWeight = FontWeight.SemiBold)
            Text(sub, color = C.dim, fontSize = 12.sp, lineHeight = 16.sp)
        }
        androidx.compose.material3.Switch(on, null)
    }
}

@Composable
fun SettingsScreen(vm: ChatViewModel, onBack: () -> Unit, onKey: () -> Unit, onCustom: (Int) -> Unit, onPrivacy: () -> Unit, onLock: (Boolean) -> Unit = {}, onReport: () -> Unit = {}) {
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
            ProviderPicker(provider, accent) {
                provider = it; vm.settings.provider = it
                if (!vm.keys.has(it)) onKey()
            }
            Spacer(Modifier.height(10.dp))
            Text(
                if (vm.keys.has(provider)) t("✓ Υπάρχει κλειδί για το ${provider.label}, κρυπτογραφημένο στο κινητό.", "✓ There is a ${provider.label} key, encrypted on the phone.") else t("Δεν υπάρχει κλειδί για το ${provider.label}.", "There is no ${provider.label} key."),
                color = if (vm.keys.has(provider)) C.ok else C.warn, fontSize = 13.sp,
            )
            TextButton(onKey) { Text(t("Άλλαξε ή βάλε κλειδί", "Change or add the key"), color = accent) }
            androidx.compose.runtime.LaunchedEffect(provider) { vm.loadBalance() }
            val bal by vm.balance.collectAsState()
            if (provider == Provider.DEEPSEEK) Text(
                bal?.let { t("Υπόλοιπο DeepSeek: $it", "DeepSeek balance: $it") } ?: t("Υπόλοιπο: δεν φαίνεται τώρα (internet;)", "Balance: not available right now (internet?)"),
                color = C.amber, fontWeight = FontWeight.SemiBold, fontSize = 13.5.sp,
            ) else if (provider == Provider.GEMINI) Text(
                t("Με κλειδί με billing, το υπόλοιπο και τις χρεώσεις του Gemini τα δείχνει μόνο η Google, στο console του Google Cloud (Billing).", "With a billing key, Gemini's balance and charges are shown only by Google, in the Google Cloud console (Billing)."),
                color = C.dim, fontSize = 12.sp,
            )
            if (provider != Provider.GEMINI) {
                var model by remember(provider) { mutableStateOf(vm.settings.model(provider)) }
                OutlinedTextField(model, { model = it; vm.settings.setModel(provider, it) }, Modifier.fillMaxWidth().padding(top = 8.dp), singleLine = true,
                    label = { Text(t("Μοντέλο", "Model")) },
                    supportingText = { Text(t("Προεπιλογή: ${provider.model}. Άδειο = προεπιλογή.", "Default: ${provider.model}. Empty = default.")) })
                if (provider == Provider.OPENROUTER) TextButton({ openUrl(context, "https://openrouter.ai/models?max_price=0") }) { Text(t("Τα δωρεάν μοντέλα του OpenRouter ↗", "OpenRouter's free models ↗"), color = accent) }
                if (provider == Provider.GROQ) TextButton({ openUrl(context, "https://console.groq.com/docs/models") }) { Text(t("Τα μοντέλα του Groq ↗", "Groq's models ↗"), color = accent) }
            }
            if (provider == Provider.GEMINI) TextButton({ openUrl(context, "https://console.cloud.google.com/billing") }) { Text(t("Άνοιγμα Google Cloud Billing ↗", "Open Google Cloud Billing ↗"), color = accent) }
            val (texts, secs) = vm.settings.usage(provider)
            Text(
                t("Σήμερα: $texts μηνύματα κειμένου · ${secs / 60} λεπτά φωνής (${provider.label})", "Today: $texts text messages · ${secs / 60} minutes of voice (${provider.label})"),
                color = C.muted, fontSize = 12.5.sp,
            )
            Text(
                if (provider == Provider.GEMINI) t("Τα δωρεάν όρια του Gemini τα ορίζει η Google και αλλάζουν· όταν τα φτάσεις, σου το λέμε.", "Gemini's free limits are set by Google and change; when you reach them, we tell you.")
                else t("Το DeepSeek χρεώνει από το υπόλοιπό σου· δες το στο platform.deepseek.com.", "DeepSeek charges your balance; see it at platform.deepseek.com."),
                color = C.dim, fontSize = 12.sp,
            )
        }
        Panel {
            Label(t("ΓΛΩΣΣΑ / LANGUAGE", "LANGUAGE / ΓΛΩΣΣΑ"))
            Segmented(listOf(false, true), app.binelik.i18n.Lang.en, { if (it) "English" else "Ελληνικά" }, accent, {
                app.binelik.i18n.Lang.en = it; vm.settings.english = it; vm.refresh()
            })
            Text(t("Αλλάζουν και οι οθόνες και η γλώσσα που μιλάνε οι χαρακτήρες.", "Both the screens and the language the characters speak change."), color = C.dim, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
        }
        Panel {
            Label(t("ΟΘΟΝΗ ΚΑΙ ΦΩΝΗ", "SCREEN AND VOICE"))
            var scale by remember { mutableStateOf(vm.settings.textScale) }
            Text(t("Μέγεθος γραμμάτων", "Text size"), fontSize = 14.5.sp, fontWeight = FontWeight.SemiBold)
            Segmented(listOf(1f, 1.15f, 1.3f), scale, { when (it) { 1f -> t("Κανονικά", "Normal"); 1.15f -> t("Μεγάλα", "Large"); else -> t("Πολύ μεγάλα", "Very large") } }, accent, {
                scale = it; vm.settings.textScale = it; app.binelik.i18n.Ui.scale = it
            }, Modifier.padding(top = 6.dp, bottom = 10.dp))
            var gain by remember { mutableStateOf(vm.settings.voiceGain) }
            Text(t("Ένταση φωνής (πάνω από την ένταση του κινητού)", "Voice volume (on top of the phone's volume)"), fontSize = 14.5.sp, fontWeight = FontWeight.SemiBold)
            Segmented(listOf(1f, 1.6f, 2.2f), gain, { when (it) { 1f -> t("Κανονική", "Normal"); 1.6f -> t("Δυνατή", "Loud"); else -> t("Πολύ δυνατή", "Very loud") } }, accent, {
                gain = it; vm.settings.voiceGain = it; app.binelik.ai.Speaker.gain = it
            }, Modifier.padding(top = 6.dp, bottom = 10.dp))
            var rate by remember { mutableStateOf(vm.settings.speechRate) }
            Text(t("Ταχύτητα φωνής του κινητού (DeepSeek)", "Speed of the phone's voice (DeepSeek)"), fontSize = 14.5.sp, fontWeight = FontWeight.SemiBold)
            Segmented(listOf(0.9f, 1.05f, 1.25f), rate, { when (it) { 0.9f -> t("Αργά", "Slow"); 1.05f -> t("Κανονικά", "Normal"); else -> t("Γρήγορα", "Fast") } }, accent, {
                rate = it; vm.settings.speechRate = it; app.binelik.ai.PhoneVoice.rate = it
            }, Modifier.padding(top = 6.dp, bottom = 4.dp))
            SwitchRow(t("Πατάς και μιλάς", "Push to talk"), t("Κρατάς ένα κουμπί όσο μιλάς, αντί για ανοιχτό μικρόφωνο. Καλό με θόρυβο ή στο αυτοκίνητο.", "Hold a button while you talk, instead of an open microphone. Good with noise or in the car."), vm.settings.pushToTalk) { vm.settings.pushToTalk = it }
            SwitchRow(t("Πρόσωπο στον κύκλο", "Face in the circle"), t("Μάτια και στόμα που κουνιέται με τη φωνή.", "Eyes and a mouth that moves with the voice."), vm.settings.face) { vm.settings.face = it }
            SwitchRow(t("Ήχοι χαρακτήρων", "Character sounds"), t("Κόρνα για τον ταξιτζή, σφυρίχτρα για τον Λοχία, γήπεδο για τον οπαδό… όταν ξεκινά το Live.", "A horn for the taxi driver, a whistle for the sergeant, a stadium for the fan… when Live starts."), vm.settings.sounds) { vm.settings.sounds = it }
            SwitchRow(t("Τον διακόπτεις όταν μιλάς", "Talk over it to interrupt"), t("Groq, OpenRouter και DeepSeek: το μικρόφωνο μένει ανοιχτό και ο χαρακτήρας σταματά μόλις μιλήσεις, όπως στο Live του Gemini. Με ακουστικά δουλεύει καλύτερα.", "Groq, OpenRouter and DeepSeek: the microphone stays open and the character stops as soon as you talk, like Gemini Live. Works best with earphones."), vm.settings.bargeIn) { vm.settings.bargeIn = it }
            SwitchRow(t("Η οθόνη μένει ανοιχτή στο Live", "Keep the screen on during Live"), t("Αλλιώς σβήνει κανονικά και η κουβέντα συνεχίζει με ειδοποίηση.", "Otherwise it turns off as usual and the conversation goes on with a notification."), vm.settings.keepScreenOn) { vm.settings.keepScreenOn = it }
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
            Label(t("Η ΠΟΛΗ ΣΟΥ", "YOUR CITY"))
            var city by remember { mutableStateOf(vm.settings.city) }
            OutlinedTextField(city, { city = it.take(60); vm.settings.city = it }, Modifier.fillMaxWidth(), singleLine = true, label = { Text(t("Π.χ. Καλλιθέα, Αθήνα", "E.g. Kallithea, Athens")) })
            Text(t("Για ερωτήσεις όπως «τι καιρό θα κάνει αύριο». Προαιρετικό· πηγαίνει στο AI μαζί με την ερώτηση.", "For questions like “what's the weather tomorrow”. Optional; it goes to the AI with the question."), color = C.dim, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))
        }
        Panel {
            Label(t("ΥΦΟΣ", "STYLE"))
            var region by remember { mutableStateOf(vm.settings.region) }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                app.binelik.data.Region.entries.forEach { r ->
                    val on = r == region
                    Text(
                        r.label, color = if (on) Color.Black else C.text, fontWeight = FontWeight.SemiBold, fontSize = 13.sp,
                        modifier = Modifier.clip(RoundedCornerShape(50)).background(if (on) accent else C.panel2)
                            .border(1.dp, if (on) accent else C.line, RoundedCornerShape(50))
                            .clickable(role = Role.RadioButton) { region = r; vm.settings.region = r; vm.refresh() }
                            .semantics { selected = on }.padding(horizontal = 12.dp, vertical = 8.dp),
                    )
                }
            }
            Text(t("Το ύφος της περιοχής για όλους τους χαρακτήρες, ελαφριά και με αγάπη.", "A regional flavour for every character, light and with love."), color = C.dim, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))
            SwitchRow(t("Μπιπ (λογοκριμένη τηλεόραση)", "Beep (censored TV)"), t("Οι χαρακτήρες λένε «μπιπ» αντί για βρισιές, και στο κείμενο οι βρισιές γίνονται «μπιπ».", "The characters say \"beep\" instead of swearing, and swear words show as \"beep\"."), vm.settings.bleep) { vm.settings.bleep = it; vm.refresh() }
        }
        Panel {
            Label(t("ΜΝΗΜΗ ΚΑΙ ΔΕΥΤΕΡΟ AI", "MEMORY AND A SECOND AI"))
            SwitchRow(t("Οι χαρακτήρες θυμούνται", "The characters remember"), t("Κάθε χαρακτήρας κρατά έως 4 σύντομα πράγματα από τις κουβέντες σας («πάλι το αφεντικό;»). Μόνο στο κινητό, χωρίς ονόματα, υγεία ή κάτι προσωπικό. Στην κουβέντα: «🧠 Τι θυμάται» → «Ξέχνα με».", "Each character keeps up to 4 short things from your conversations (\"the boss again?\"). Only on the phone, no names, health or anything personal. In the chat: \"🧠 What it remembers\" → \"Forget me\"."), vm.settings.memory) { vm.settings.memory = it }
            TextButton({ app.binelik.voice.Engine.memory.forgetAll() }) { Text(t("Ξέχασε τα πάντα, για όλους τους χαρακτήρες", "Forget everything, for every character"), color = C.danger, fontSize = 13.sp) }
            SwitchRow(t("Δεύτερο AI όταν τελειώνει το όριο", "A second AI when the limit runs out"), t("Αν το AI που διάλεξες δεν μπορεί να απαντήσει (όριο, φόρτος), απαντά ένα άλλο για το οποίο έχεις βάλει κλειδί, και σου το λέει. Π.χ. Gemini και Groq, και τα δύο δωρεάν.", "If the AI you chose can't answer (limit, load), another one you have a key for answers, and tells you. E.g. Gemini and Groq, both free."), vm.settings.fallback) { vm.settings.fallback = it }
        }
        Panel {
            Label(t("ΠΑΙΧΝΙΔΙΑ ΚΑΙ ΕΙΔΟΠΟΙΗΣΕΙΣ", "GAMES AND NOTIFICATIONS"))
            SwitchRow(t("Πώς πήγε η βδομάδα;", "How did the week go?"), t("Την Παρασκευή το απόγευμα, μία ειδοποίηση με το θυμόμετρο της εβδομάδας. Και widget «Θυμόμετρο εβδομάδας» για την αρχική οθόνη.", "On Friday evening, one notification with the week's anger meter. And a \"Week's anger meter\" widget for the home screen."), vm.settings.fridayNote) {
                vm.settings.fridayNote = it; app.binelik.extras.Weekly.schedule(context, it)
            }
            SwitchRow(t("Θυμόμετρο", "Anger meter"), t("Σε ρωτά πόσο θυμωμένος είσαι πριν και μετά το Live.", "Asks how angry you are before and after Live."), vm.settings.angerMeter) { vm.settings.angerMeter = it }
            SwitchRow(t("Ατάκα της ημέρας", "Line of the day"), t("Μία ειδοποίηση την ημέρα, γύρω στις 11, από τυχαίο χαρακτήρα.", "One notification a day, around 11:00, from a random character."), vm.settings.dailyLine) {
                vm.settings.dailyLine = it; app.binelik.extras.Daily.schedule(context, it)
            }
            SwitchRow(t("Στο αυτοκίνητο ξεκινά ο Ταξιτζής", "In the car, start with the taxi driver"), t("Όταν ανοίγει το Android Auto.", "When Android Auto opens."), vm.settings.carTaxi) { vm.settings.carTaxi = it }
            Text(t("Ξέσπα τώρα: πρόσθεσε το widget στην αρχική οθόνη, ή το κουμπί στις γρήγορες ρυθμίσεις (σύρε από πάνω → μολύβι).", "Vent now: add the widget to the home screen, or the tile to the quick settings (swipe down → pencil)."), color = C.dim, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))
        }
        Panel {
            Label(t("ΑΠΟΡΡΗΤΟ", "PRIVACY"))
            SwitchRow(t("Κλείδωμα", "Lock"), t("Δαχτυλίδι, πρόσωπο ή το κλείδωμα του κινητού για να ανοίξει η εφαρμογή.", "Fingerprint, face or the phone's lock to open the app."), vm.settings.lock) { want -> onLock(want) }
            SwitchRow(t("Διακριτικό εικονίδιο", "Discreet icon"), t("Στην αρχική οθόνη φαίνεται «Σημειώσεις» αντί για το Binelik AI. Μπορεί να χρειαστεί λίγα δευτερόλεπτα να αλλάξει.", "The home screen shows \"Notes\" instead of Binelik AI. It may take a few seconds to change."), vm.settings.discreet) {
                vm.settings.discreet = it; app.binelik.extras.Icon.set(context, it)
            }
        }
        Panel {
            Label(t("ΟΙ ΔΙΚΟΙ ΣΟΥ ΧΑΡΑΚΤΗΡΕΣ", "YOUR OWN CHARACTERS"))
            Characters.CUSTOM_SLOTS.forEach { slot ->
                val own = vm.settings.custom(slot)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(own?.label ?: t("Custom $slot: κενός", "Custom $slot: empty"), color = if (own == null) C.dim else C.text, fontSize = 13.5.sp, modifier = Modifier.weight(1f))
                    TextButton({ onCustom(slot) }) { Text(if (own == null) t("Φτιάξε", "Make") else t("Άλλαξε", "Change"), color = accent) }
                }
            }
        }
        Panel {
            Label(t("ΚΟΥΒΕΝΤΕΣ", "CONVERSATIONS"))
            Text(t("Οι κουβέντες σου υπάρχουν μόνο σε αυτό το κινητό.", "Your conversations exist only on this phone."), color = C.muted, fontSize = 13.sp)
            TextButton({ confirm = false }) { Text(t("Σβήσε τις κουβέντες με: ${c.name}", "Delete the conversations with: ${c.name}"), color = C.danger) }
            val msgs by vm.messages.collectAsState()
            TextButton({ app.binelik.extras.Share.shareText(context, app.binelik.extras.Share.conversationText(c, msgs.map { it.copy(text = vm.shown(it.text)) })) }, enabled = msgs.isNotEmpty()) {
                Text(t("Κοινοποίηση της κουβέντας με: ${c.name}", "Share the conversation with: ${c.name}"), color = accent)
            }
            TextButton({ confirm = true }) { Text(t("Σβήσε όλες τις κουβέντες", "Delete all conversations"), color = C.danger) }
            var reset by remember { mutableStateOf(false) }
            TextButton({ reset = true }) { Text(t("Επαναφορά: ρυθμίσεις και κλειδιά", "Reset: settings and keys"), color = C.danger) }
            if (reset) androidx.compose.material3.AlertDialog(
                onDismissRequest = { reset = false }, containerColor = C.panel,
                title = { Text(t("Επαναφορά;", "Reset?")) },
                text = { Text(t("Σβήνονται όλες οι ρυθμίσεις και τα κλειδιά AI. Οι κουβέντες μένουν (τις σβήνεις χωριστά). Η εφαρμογή ξεκινά από την αρχή.", "All settings and AI keys are deleted. Conversations stay (delete them separately). The app starts over."), color = C.muted) },
                confirmButton = { TextButton({ vm.stopLive(); vm.settings.resetAll(context); app.binelik.voice.Engine.memory.forgetAll(); (context as? android.app.Activity)?.recreate() }) { Text(t("Επαναφορά", "Reset"), color = C.danger) } },
                dismissButton = { TextButton({ reset = false }) { Text(t("Άκυρο", "Cancel"), color = C.muted) } },
            )
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
            Text(t("Η εφαρμογή ρωτά το GitHub, το πολύ κάθε ώρα, ποια είναι η τελευταία έκδοση, και βγάζει παράθυρο στην αρχική. Δεν στέλνεται τίποτα δικό σου.", "The app asks GitHub, at most every hour, which is the latest version, and shows a popup on the home screen. Nothing of yours is sent."), color = C.dim, fontSize = 12.sp)
            TextButton({ vm.checkUpdate(now = true) }, enabled = !checking) { Text(if (checking) t("Ελέγχω…", "Checking…") else t("Έλεγχος τώρα", "Check now"), color = accent) }
            val last = vm.settings.lastUpdateCheck
            if (!checking && last > 0) Text(
                upd?.let { t("Υπάρχει η ${it.version}: πάτα «Ενημέρωση» στην αρχική.", "${it.version} is out: press “Update” on the home screen.") }
                    ?: t("Έχεις την τελευταία έκδοση (${BuildConfig.VERSION_NAME}).", "You have the latest version (${BuildConfig.VERSION_NAME})."),
                color = C.muted, fontSize = 12.5.sp,
            )
        }
        Panel {
            Label(t("ΚΟΙΝΟΠΟΙΗΣΗ", "SHARE"))
            Text(t("Στείλε το Binelik AI σε φίλους που θέλουν να ξεσπάσουν (18+).", "Send Binelik AI to friends who need to let off steam (18+)."), color = C.muted, fontSize = 13.sp)
            TextButton({ app.binelik.extras.MoreApps.share(context) }) { Text(t("📤 Κοινοποίηση", "📤 Share"), color = accent) }
        }
        Panel {
            Label(t("ΟΙ ΑΛΛΕΣ ΕΦΑΡΜΟΓΕΣ ΜΑΣ", "OUR OTHER APPS"))
            app.binelik.extras.MoreApps.others.forEach { a ->
                val has = remember(a.pkg) { app.binelik.extras.MoreApps.installed(context, a.pkg) }
                Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.Top) {
                    Text(a.emoji, fontSize = 26.sp, modifier = Modifier.padding(end = 12.dp, top = 2.dp))
                    Column(Modifier.weight(1f)) {
                        Text(a.name, fontSize = 14.5.sp, fontWeight = FontWeight.SemiBold)
                        Text(a.desc, color = C.dim, fontSize = 12.sp, lineHeight = 16.sp)
                        TextButton({ app.binelik.extras.MoreApps.openOrInstall(context, a) }) { Text(if (has) t("Άνοιγμα ↗", "Open ↗") else t("Εγκατάσταση ↗", "Install ↗"), color = accent) }
                    }
                }
            }
        }
        Panel {
            Label(t("ΣΧΕΤΙΚΑ", "ABOUT"))
            Text("Binelik AI ${BuildConfig.VERSION_NAME}", fontWeight = FontWeight.Bold)
            Text(t("Δημιουργός: jemangr · Ανοιχτός κώδικας, MIT license", "Made by jemangr · Open source, MIT license"), color = C.muted, fontSize = 13.sp)
            TextButton({ openUrl(context, REPO) }) { Text(t("Ο κώδικας στο GitHub ↗", "The code on GitHub ↗"), color = accent) }
            TextButton(onPrivacy) { Text(t("Πολιτική απορρήτου", "Privacy policy"), color = accent) }
            TextButton(onReport) { Text(t("🐞 Αναφορά προβλήματος", "🐞 Report a problem"), color = accent) }
            var shake by remember { mutableStateOf(vm.settings.shakeReport) }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(t("Κούνημα του κινητού: αναφορά προβλήματος", "Shake the phone: report a problem"), modifier = Modifier.weight(1f), fontSize = 14.sp)
                androidx.compose.material3.Switch(shake, { shake = it; vm.settings.shakeReport = it })
            }
            Text(t("3 δυνατά κουνήματα ανοίγουν την αναφορά (όχι στα πιάτα, που σπάνε με κούνημα).", "3 strong shakes open the report (not on the plates, which break with a shake)."), color = C.dim, fontSize = 12.sp)
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

internal val swatches = listOf(0xFFFFB020, 0xFFFF3B3B, 0xFFFF5FA2, 0xFF3DDC84, 0xFF4DA3FF, 0xFFA66BFF, 0xFF22D3EE, 0xFFF5F5F5)

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
    val UPDATED = t("Τελευταία ενημέρωση: 5 Νοεμβρίου 2026 (1.3)", "Last updated: 5 November 2026 (1.3)")
    val sections get() = listOf(
        t("Δεν κρατάμε τίποτα", "We keep nothing") to t("Το Binelik AI δεν έχει δικό του server. Δεν υπάρχει λογαριασμός, εγγραφή, διαφημίσεις, analytics ή trackers. Ο δημιουργός δεν βλέπει τίποτα από όσα γράφεις.", "Binelik AI has no server of its own. There is no account, sign-up, ads, analytics or trackers. The maker sees nothing of what you write."),
        t("Οι κουβέντες σου", "Your conversations") to t("Μένουν μόνο στο κινητό σου. Δεν μπαίνουν ούτε στο αντίγραφο ασφαλείας του Android. Τις σβήνεις από τις Ρυθμίσεις, ή απεγκαθιστώντας την εφαρμογή.", "They stay on your phone only. They're not even in Android's backup. You delete them in the Settings, or by uninstalling the app."),
        t("Το κλειδί σου", "Your key") to t("Μένει κρυπτογραφημένο στο κινητό σου (Android Keystore) και στέλνεται μόνο στην υπηρεσία που ανήκει.", "It stays encrypted on your phone (Android Keystore) and is sent only to the service it belongs to."),
        t("Τι στέλνεται και πού", "What is sent, and where") to t("Ό,τι γράφεις στέλνεται απευθείας από το κινητό σου στην υπηρεσία AI που διάλεξες, με το δικό σου κλειδί, και εκεί ισχύει η δική της πολιτική:\n• Google Gemini: με δωρεάν κλειδί, η Google μπορεί να χρησιμοποιεί τις κουβέντες για να βελτιώνει τα προϊόντα της και μπορεί να τις διαβάσουν άνθρωποι· με billing, όχι.\n• DeepSeek: οι κουβέντες πάνε στους servers του, στην Κίνα.\n• Groq: στους servers του, στις ΗΠΑ.\n• OpenRouter: στο OpenRouter (ΗΠΑ) και από εκεί στην εταιρεία του μοντέλου· κάποια δωρεάν μοντέλα κρατούν τις κουβέντες για εκπαίδευση.", "What you write is sent straight from your phone to the AI service you chose, with your own key, and its own policy applies there:\n• Google Gemini: with a free key, Google may use the conversations to improve its products and people may read them; with billing, not.\n• DeepSeek: the conversations go to its servers, in China.\n• Groq: to its servers, in the USA.\n• OpenRouter: to OpenRouter (USA) and from there to the model's company; some free models keep conversations for training."),
        t("Παιχνίδια", "Games") to t("Το ουρλιαχτόμετρο μετράει μόνο την ένταση, στο κινητό· ήχος δεν γράφεται. Το «Κάψ' το» δεν αποθηκεύει τίποτα. Κόντρα, πρόβα και ομαδική κουβέντα πηγαίνουν στο AI σου όπως η κουβέντα, χωρίς ιστορικό. Τα «Περισσότεροι χαρακτήρες» κατεβαίνουν από το GitHub μόνο όταν ανοίγεις τη σελίδα. Ένας δικός σου χαρακτήρας με QR ή link είναι όλος μέσα στο link, χωρίς server.", "The scream meter only measures loudness, on the phone; no audio is recorded. «Burn it» saves nothing. Battle, rehearsal and group chat go to your AI like the chat, without history. «More characters» are downloaded from GitHub only when you open that page. Your own character shared by QR or link is all inside the link, no server."),
        t("Μνήμη, δεύτερο AI, sticker (1.3)", "Memory, second AI, stickers (1.3)") to t(
            "Κάθε χαρακτήρας κρατά έως 4 σύντομες γραμμές για το τι σε εκνευρίζει. Τις γράφει το AI σου από την κουβέντα που ήδη του στέλνεις, και μένουν μόνο στο κινητό, χωρίς ονόματα, τηλέφωνα ή κάτι για αυτοτραυματισμό. Τις σβήνεις με το «Ξέχνα με» ή από εδώ, και σβήνονται μαζί με τις κουβέντες. Αν το AI σου δεν απαντά (όριο, φόρτος), απαντά άλλο AI για το οποίο έχεις βάλει εσύ κλειδί, με την πολιτική εκείνης της υπηρεσίας. Τα sticker φτιάχνονται στο κινητό· το πακέτο του WhatsApp το διαβάζει μόνο το WhatsApp. Το θυμόμετρο εβδομάδας υπολογίζεται στο κινητό.",
            "Each character keeps up to 4 short lines about what annoys you. Your own AI writes them from the conversation you already send it, and they stay on the phone only, with no names, phone numbers or self-harm. You delete them with \"Forget me\" or here, and they go with the conversations. If your AI can't answer (limit, load), another AI you gave a key to answers, under that service's policy. Stickers are made on the phone; the WhatsApp pack is read only by WhatsApp. The week's meter is computed on the phone.",
        ),
        t("Ο έλεγχος ασφάλειας", "The safety check") to t("Ο έλεγχος για αυτοκτονία και για πραγματική πρόθεση βίας γίνεται στο κινητό σου και δεν στέλνεται πουθενά. Όταν πιάνει κάτι, το μήνυμα δεν πηγαίνει στο AI.", "The check for suicide and for real intent of violence runs on your phone and isn't sent anywhere. When it catches something, the message doesn't go to the AI."),
        t("Φωνή και μικρόφωνο", "Voice and microphone") to t("Το μικρόφωνο ζητιέται μόνο όταν πατήσεις Play. Με το Gemini, η φωνή σου στέλνεται ζωντανά μόνο στο Google Gemini, με το δικό σου κλειδί. Με το DeepSeek, τη φωνή σου την ακούει η αναγνώριση ομιλίας του κινητού σου (συνήθως της Google) και στο DeepSeek πηγαίνει μόνο το κείμενο. Ήχος δεν γράφεται πουθενά· κρατιέται μόνο το κείμενο της κουβέντας. Όσο μιλάτε, μια ειδοποίηση δείχνει ότι το μικρόφωνο είναι ανοιχτό, με κουμπί Παύση.", "The microphone is asked for only when you press Play. With Gemini, your voice is streamed only to Google Gemini, with your own key. With DeepSeek, your phone's speech recognition (usually Google's) hears your voice and only the text goes to DeepSeek. No audio is recorded anywhere; only the text of the conversation is kept. While you talk, a notification shows the microphone is on, with a Pause button."),
        t("Έλεγχος για νέα έκδοση", "New version check") to t("Αν το αφήσεις ανοιχτό (Ρυθμίσεις), η εφαρμογή ρωτά το GitHub, το πολύ κάθε 6 ώρες, ποια είναι η τελευταία έκδοση. Το GitHub βλέπει μόνο ότι έγινε ένα αίτημα (και τη διεύθυνση IP, όπως κάθε site). Τίποτα από τις κουβέντες ή το κλειδί σου.", "If you leave it on (Settings), the app asks GitHub, at most every 6 hours, which is the latest version. GitHub sees only that a request was made (and the IP address, like any site). Nothing of your conversations or key."),
        t("Σφάλματα και αναφορά προβλήματος", "Errors and problem reports") to t("Αν η εφαρμογή κλείσει απότομα, το τεχνικό σφάλμα (όχι οι κουβέντες) μένει στο κινητό σου μέχρι το επόμενο άνοιγμα, για να το δεις. Η «Αναφορά προβλήματος» φτιάχνει ένα κείμενο με όσα γράψεις, την έκδοση, το κινητό και τα τελευταία σφάλματα· ποτέ κλειδιά ή κουβέντες. Δεν στέλνεται τίποτα μόνο του: το στέλνεις εσύ, με «Αποστολή» (περνά από το platebit.app και φτάνει με email στο info@3wsol.gr, χωρίς να κρατιέται εκεί· το email σου μόνο αν το γράψεις) ή με την εφαρμογή email σου.", "If the app closes suddenly, the technical error (not the conversations) stays on your phone until the next start, for you to see. «Report a problem» makes a text with what you write, the version, the phone and the last errors; never keys or conversations. Nothing is sent by itself: you send it, with «Send» (it goes through platebit.app and arrives by email at info@3wsol.gr, nothing kept there; your email only if you write it) or with your email app."),
        t("Άδειες", "Permissions") to t("Internet (για το AI και τον έλεγχο νέας έκδοσης), μικρόφωνο (μόνο για τη φωνή) και ειδοποιήσεις (για το κουμπί Παύση). Τίποτα άλλο: ούτε επαφές, ούτε τοποθεσία, ούτε αρχεία.", "Internet (for the AI and the new version check), microphone (only for voice) and notifications (for the Pause button). Nothing else: no contacts, no location, no files."),
        t("18+ και όχι ψυχοθεραπεία", "18+ and not therapy") to t("Είναι για ενήλικες. Δεν είναι ψυχοθεραπεία ούτε ιατρική συμβουλή. Αν χρειάζεσαι βοήθεια: 1018 (24ωρη γραμμή για την αυτοκτονία) ή 112.", "It's for adults. It isn't therapy or medical advice. If you need help: 112, or your local crisis line (in Greece 1018, 24 hours)."),
        t("Ανοιχτός κώδικας", "Open source") to t("Ο κώδικας είναι δημόσιος (MIT license) στο github.com/jemangr/Binelik-AI: μπορεί ο καθένας να ελέγξει όλα τα παραπάνω. Για ερωτήσεις, άνοιξε ένα issue εκεί.", "The code is public (MIT license) at github.com/jemangr/Binelik-AI: anyone can check all of the above. For questions, open an issue there."),
    )
}
