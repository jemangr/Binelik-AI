package app.binelik

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.test.core.app.ApplicationProvider
import app.binelik.data.Characters
import app.binelik.data.Anger
import app.binelik.data.Db
import app.binelik.data.Message
import app.binelik.ui.BinelikTheme
import app.binelik.ui.C
import app.binelik.ui.CharacterList
import app.binelik.ui.ChatScreen
import app.binelik.ui.ChatViewModel
import app.binelik.ui.KeyStep
import app.binelik.ui.LiveScreen
import app.binelik.ui.FunScreen
import app.binelik.ui.StatsScreen
import app.binelik.ui.FightScreen
import app.binelik.ui.SettingsScreen
import app.binelik.ui.Onboarding
import app.binelik.ui.PrivacyScreen
import app.binelik.ui.SafetyDialog
import app.binelik.ui.GamesViewModel
import app.binelik.ui.GameLine
import app.binelik.ui.BattleScreen
import app.binelik.ui.PlatesScreen
import app.binelik.ui.GroupScreen
import app.binelik.ui.CustomScreen
import app.binelik.voice.Engine
import app.binelik.i18n.Lang
import com.github.takahirom.roborazzi.captureRoboImage
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The screens of the app, rendered on the JVM: checked by eye, and the README screenshots (docs/screenshots). */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w390dp-h844dp-xhdpi")
class ScreenshotsTest {
    @get:Rule val compose = createComposeRule()
    private val app get() = ApplicationProvider.getApplicationContext<android.app.Application>()

    @org.junit.Before fun greek() { Engine.init(app); Lang.en = false }

    private fun shot(name: String, content: @androidx.compose.runtime.Composable () -> Unit) {
        compose.mainClock.autoAdvance = false
        compose.setContent { BinelikTheme { content() } }
        compose.mainClock.advanceTimeBy(1500)
        compose.onRoot().captureRoboImage("../docs/screenshots/$name.png")
    }

    @Test fun s1_welcome() = shot("1-welcome") { Onboarding(ChatViewModel(app)) {} }

    @Test fun s2_key() = shot("2-key") {
        Column(Modifier.fillMaxSize().background(C.bg).padding(20.dp)) { KeyStep(ChatViewModel(app)) {} }
    }

    @Test fun s3_chat() {
        val vm = ChatViewModel(app)
        vm.pick(Characters.byId("kollitos")!!)
        runBlocking {
            val dao = Db.get(app).messages()
            dao.add(Message(character = "kollitos", fromUser = true, text = "Ο μαλάκας ο προϊστάμενος μου φόρτωσε πάλι όλη τη δουλειά τελευταία στιγμή"))
            dao.add(Message(character = "kollitos", fromUser = false, text = "Έχεις δίκιο ρε φίλε, μαλάκας είναι. Πάντα στις πέντε παρά τέταρτο θυμάται; Πες τα μου όλα."))
            dao.add(Message(character = "kollitos", fromUser = true, text = "Και μετά είπε στη σύσκεψη ότι ήταν δική του ιδέα"))
            dao.add(Message(character = "kollitos", fromUser = false, text = "Όχι ρε, αυτό είναι το χειρότερο. Δικό σου μυαλό, δικά του τα εύσημα. Τι του απάντησες;"))
        }
        // Room delivers on its own threads, then the main looper: let both run.
        val end = System.currentTimeMillis() + 10_000
        while (vm.messages.value.size < 4 && System.currentTimeMillis() < end) {
            org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle(); Thread.sleep(50)
        }
        shot("3-chat") { ChatScreen(vm, {}, {}) }
    }

    @Test fun s4_characters() = shot("4-characters") {
        Column(Modifier.fillMaxSize().background(Color(0xFF0A1018)).padding(top = 16.dp)) {
            Text("Με ποιον θα τα πεις;", fontWeight = FontWeight.ExtraBold, fontSize = 17.sp, modifier = Modifier.padding(horizontal = 18.dp, vertical = 4.dp))
            CharacterList(ChatViewModel(app), onCustom = {}) {}
        }
    }

    @Test fun s5_empty() {
        val vm = ChatViewModel(app)
        vm.pick(Characters.byId("kavgatzis")!!)
        shot("5-start") { ChatScreen(vm, {}, {}) }
    }

    @Test fun s6_safety() = shot("6-safety") {
        Column(Modifier.fillMaxSize().background(C.bg)) { SafetyDialog {} }
    }

    @Test fun s0_live() {
        val vm = ChatViewModel(app)
        vm.pick(Characters.byId("gynaika")!!)
        shot("0-live") { LiveScreen(vm, {}, {}, {}) }
    }

    @Test fun e1_live_en() {
        Lang.en = true
        val vm = ChatViewModel(app)
        vm.pick(Characters.byId("afentiko")!!)
        shot("en-live") { LiveScreen(vm, {}, {}, {}) }
    }

    @Test fun e2_characters_en() {
        Lang.en = true
        shot("en-characters") {
            Column(Modifier.fillMaxSize().background(Color(0xFF0A1018)).padding(top = 16.dp)) {
                CharacterList(ChatViewModel(app), onCustom = {}) {}
            }
        }
    }

    @Test fun f1_fun() = shot("8-games") { FunScreen(ChatViewModel(app), {}) {} }

    @Test fun f2_fight() {
        val vm = ChatViewModel(app)
        val a = Characters.byId("mana")!!; val b = Characters.byId("gynaika")!!
        vm.fight.value = listOf(
            ChatViewModel.FightLine(a, "Το παιδί μου τρώει μόνο σπιτικό. Εσύ τι του δίνεις, ντελίβερι;"),
            ChatViewModel.FightLine(b, "Τίποτα δεν λέω. Ε βέβαια, εσείς ξέρετε καλύτερα."),
            ChatViewModel.FightLine(null, "Πες της για τα γεμιστά!"),
            ChatViewModel.FightLine(a, "Τα γεμιστά μου τα θυμάται από τα πέντε του. Τα δικά σου τα θυμάται κανείς;"),
        )
        shot("9-fight") { FightScreen(vm) {} }
    }

    @Test fun f3_stats() {
        runBlocking {
            val db = Db.get(app)
            val now = System.currentTimeMillis()
            listOf("Ρε μαλάκα, πάλι μου φόρτωσε δουλειά", "Γαμώτο, πάλι κίνηση", "Μαλάκας είναι, σου λέω").forEachIndexed { i, x ->
                db.messages().add(Message(character = "kollitos", fromUser = true, text = x, time = now - i * 86_400_000L))
            }
            db.anger().add(Anger(character = "kollitos", before = 9, after = 3, time = now - 3_600_000))
            db.anger().add(Anger(character = "kavgatzis", before = 7, after = 4, time = now - 7_200_000))
        }
        val vm = ChatViewModel(app)
        vm.loadStats()
        val end = System.currentTimeMillis() + 10_000
        while (vm.stats.value == null && System.currentTimeMillis() < end) { org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle(); Thread.sleep(50) }
        shot("10-stats") { StatsScreen(vm) {} }
    }

    @Test fun f4_settings() = shot("11-settings") { SettingsScreen(ChatViewModel(app), {}, {}, {}, {}) }

    @Test fun s7_privacy() = shot("7-privacy") { PrivacyScreen {} }

    // ---- 1.2 ----
    @Test fun g1_battle() {
        val vm = GamesViewModel(app)
        val c = Characters.byId("kavgatzis")!!
        vm.battleWith = c
        vm.battle.value = listOf(
            GameLine(null, "Είσαι τόσο αργός που σε προσπερνάει και το 040."),
            GameLine(c, "Κι εσύ τόσο βαρετός που σε άκουσα και κοιμήθηκε το Wi‑Fi.", 7, 8),
            GameLine(null, "Το Wi‑Fi κοιμήθηκε γιατί μπήκες εσύ και έπεσε το επίπεδο."),
            GameLine(c, "Ωπ! Αυτή πόνεσε. Κράτα την, θα την κλέψω.", 9, 6),
        )
        shot("12-battle") { BattleScreen({}, vm) }
    }

    @Test fun g2_plates() = shot("13-plates") { PlatesScreen {} }

    @Test fun g3_custom() {
        Engine.settings.setCustom(2, "Πεθερός", "Γκρινιάζει για όλα, ειδικά για το πώς παρκάρεις και τι ψήνεις στο μπάρμπεκιου.", 0xFF22D3EE)
        shot("14-custom") { CustomScreen(ChatViewModel(app), 2, {}) }
        Engine.settings.deleteCustom(2)
    }

    @Test fun g4_group() {
        val vm = GamesViewModel(app)
        val a = Characters.byId("mana")!!; val b = Characters.byId("taxitzis")!!
        vm.groupStart(a, b)
        vm.group.value = listOf(
            GameLine(null, "Θα πάω διακοπές στη Μύκονο."),
            GameLine(a, "Στη Μύκονο; Με τι λεφτά, παιδί μου; Πάρε και ζακέτα, έχει αέρα."),
            GameLine(b, "Άσ' τη Μύκονο, φίλε μου. Τήνος, αυγουστιάτικα, και θα με θυμηθείς. Και η κυρία έχει δίκιο για τη ζακέτα."),
        )
        shot("15-group") { GroupScreen({}, vm) }
    }
}
