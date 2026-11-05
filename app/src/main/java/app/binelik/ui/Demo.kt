package app.binelik.ui

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.binelik.data.Character
import app.binelik.data.Characters
import app.binelik.i18n.t
import app.binelik.safety.Safety
import app.binelik.safety.SafetyHit
import kotlinx.coroutines.delay

/**
 * 1.3: a 30-second try before the key, with no AI and no internet. Three characters answer three
 * ready lines with ready replies, so a new user sees what the app does before Google AI Studio.
 */
object Demo {
    const val SECONDS = 30

    /** The characters of the try (built in, so their colours and names are the real ones). */
    val ids = listOf("kavgatzis", "mana", "taxitzis")

    /** The lines the user can tap. */
    val lines get() = listOf(
        t("Το αφεντικό μου με φόρτωσε πάλι υπερωρίες!", "My boss piled overtime on me again!"),
        t("Μισή ώρα στην Κηφισίας για 2 χιλιόμετρα!", "Half an hour in traffic for one mile!"),
        t("Χάσαμε πάλι στο 90' ρε γαμώτο!", "We lost in the 90th minute again, damn it!"),
    )

    /** The ready reply of character [id] to line [i] (none for the AI: the try works offline). */
    fun reply(id: String, i: Int): String = when (id) {
        "kavgatzis" -> listOf(
            t("Υπερωρίες; Και τι του είπες, «ναι κύριε»; Αν δεν του πεις ένα όχι, θα σε φορτώνει μέχρι τη σύνταξη!", "Overtime? And what did you say, \"yes sir\"? Say no once, or he'll load you up until you retire!"),
            t("Μισή ώρα; Εγώ θα είχα βγει να σπρώξω το μπροστινό με τα χέρια! Γιατί δεν πήρες μετρό, φωστήρα;", "Half an hour? I'd have got out and pushed the car in front! Why didn't you take the train, genius?"),
            t("Στο 90'; Ε, αυτό δεν είναι ομάδα, είναι ψυχοθεραπεία που πληρώνεις εσύ!", "In the 90th? That's not a team, that's therapy you pay for!"),
        )[i]
        "mana" -> listOf(
            t("Αχ παιδί μου, σε εκμεταλλεύεται! Έφαγες τουλάχιστον; Πάρε και ένα ταπεράκι για αύριο.", "Oh my child, he's using you! Did you at least eat? Take a little tupperware for tomorrow."),
            t("Μισή ώρα; Σου έλεγα να φύγεις νωρίτερα! Πάρε με τηλέφωνο όταν φτάσεις, να μην ανησυχώ.", "Half an hour? I told you to leave earlier! Call me when you get there so I don't worry."),
            t("Πάλι με το ποδόσφαιρο; Αυτή η ομάδα θα σε πεθάνει. Βρες έναν καλό άνθρωπο να ηρεμήσεις.", "Football again? That team will be the death of you. Find someone nice and calm down."),
        )[i]
        else -> listOf(
            t("Άσ' τα, φίλε, όλοι έτσι είναι. Εγώ είχα αφεντικό που μου έκοβε και τα ρέστα από τον καφέ!", "Forget it, mate, they're all the same. I had a boss who docked me the change from my coffee!"),
            t("Μισή ώρα; Χαλαρά. Εγώ ζω εκεί μέσα! Άμα δεν κόρναρε κι ο από πίσω, θα 'ταν και ωραία.", "Half an hour? Easy. I live in there! If the guy behind wasn't honking, it'd be nice."),
            t("Στο 90'; Ε, ο προπονητής φταίει, το λέω από τον Σεπτέμβρη σε όποιον μπαίνει στο ταξί!", "In the 90th? It's the coach's fault, I've told everyone in my cab since September!"),
        )[i]
    }

    fun character(id: String): Character = Characters.byId(id) ?: Characters.all.first()
}

/** The try itself: pick a character, tap a line, read the reply; after [Demo.SECONDS] seconds, the key. */
@Composable
fun DemoScreen(onKey: () -> Unit) {
    var who by remember { mutableStateOf(Demo.ids.first()) }
    val chat = remember { mutableStateListOf<Pair<Boolean, String>>() }
    var left by remember { mutableIntStateOf(Demo.SECONDS) }
    var stopped by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { while (left > 0) { delay(1000); left-- }; stopped = true }
    val c = Demo.character(who)
    val accent = Color(c.color)

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(t("Δοκίμασε πρώτα", "Try it first"), fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.weight(1f))
            Hud(if (stopped) t("ΤΕΛΟΣ", "DONE") else "${left}″")
        }
        Text(t("Χωρίς κλειδί και χωρίς internet: έτοιμες απαντήσεις, για να δεις πώς είναι. Με το κλειδί σου απαντούν σε ό,τι πεις.", "No key and no internet: ready replies, to see what it's like. With your key they answer whatever you say."), color = C.muted, fontSize = 13.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Demo.ids.forEach { id ->
                val ch = Demo.character(id)
                val on = id == who
                Text(
                    ch.name, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = if (on) Color.Black else Color(ch.color),
                    modifier = Modifier.clip(RoundedCornerShape(50)).background(if (on) Color(ch.color) else C.panel)
                        .border(1.dp, Color(ch.color).copy(alpha = 0.6f), RoundedCornerShape(50))
                        .clickable(role = Role.RadioButton) { who = id }.padding(horizontal = 12.dp, vertical = 7.dp),
                )
            }
        }
        chat.forEach { (me, text) ->
            Box(Modifier.fillMaxWidth(), contentAlignment = if (me) Alignment.CenterEnd else Alignment.CenterStart) {
                Text(
                    text, fontSize = 14.5.sp, lineHeight = 20.sp,
                    modifier = Modifier.widthIn(max = 300.dp).clip(RoundedCornerShape(14.dp))
                        .background(if (me) Color(0xFF1D2B3D) else accent.copy(alpha = 0.14f))
                        .padding(horizontal = 12.dp, vertical = 9.dp),
                )
            }
        }
        if (!stopped) {
            Text(t("Πάτα μία φράση:", "Tap a line:"), color = C.dim, fontSize = 12.sp)
            Demo.lines.forEachIndexed { i, line ->
                Text(
                    line, fontSize = 14.sp, color = C.text,
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(C.panel).border(1.dp, C.line, RoundedCornerShape(12.dp))
                        .clickable(role = Role.Button) {
                            chat += true to line
                            // The phone's safety check works here too, even with ready lines.
                            chat += false to (if (Safety.check(line) != SafetyHit.NONE) app.binelik.voice.Engine.pauseText() else "${c.name}: " + Demo.reply(who, i))
                        }.padding(horizontal = 14.dp, vertical = 11.dp),
                )
            }
        } else {
            Text(t("Σου άρεσε; Με δωρεάν κλειδί Gemini (2 λεπτά) σου απαντούν σε ό,τι πεις, και με φωνή.", "Liked it? With a free Gemini key (2 minutes) they answer whatever you say, by voice too."), fontWeight = FontWeight.SemiBold)
        }
        Spacer(Modifier.height(8.dp))
        PrimaryButton(t("Βάλε το δωρεάν κλειδί", "Add the free key"), onKey, modifier = Modifier.padding(bottom = 18.dp))
        if (!stopped) TextButton(onKey) { Text(t("Παράλειψη", "Skip"), color = C.muted) }
    }
}
