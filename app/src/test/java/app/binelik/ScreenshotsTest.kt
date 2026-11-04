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
import app.binelik.data.Db
import app.binelik.data.Message
import app.binelik.ui.BinelikTheme
import app.binelik.ui.C
import app.binelik.ui.CharacterList
import app.binelik.ui.ChatScreen
import app.binelik.ui.ChatViewModel
import app.binelik.ui.KeyStep
import app.binelik.ui.LiveScreen
import app.binelik.ui.Onboarding
import app.binelik.ui.PrivacyScreen
import app.binelik.ui.SafetyDialog
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

    @Test fun s7_privacy() = shot("7-privacy") { PrivacyScreen {} }
}
