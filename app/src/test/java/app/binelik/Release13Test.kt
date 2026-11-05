package app.binelik

import android.graphics.BitmapFactory
import androidx.test.core.app.ApplicationProvider
import app.binelik.ai.Fallback
import app.binelik.ai.PhoneVoice
import app.binelik.ai.Provider
import app.binelik.ai.Reason
import app.binelik.ai.Turn
import app.binelik.data.Characters
import app.binelik.data.Memory
import app.binelik.extras.Stickers
import app.binelik.extras.Weekly
import app.binelik.ui.Demo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.util.Calendar

/** 1.3: memory, the second AI, talking over the phone voice, the try without a key, stickers, the week. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class Release13Test {
    @Test fun memoryKeepsShortSafeLinesOnly() {
        val lines = Memory.parse(
            """
            1. Τον εκνευρίζει το αφεντικό που τον φορτώνει υπερωρίες
            - Η κίνηση στην Κηφισίας κάθε πρωί
            • Θέλει να αυτοκτονήσει
            Το τηλέφωνό του είναι 6912345678
            ok
            Η κίνηση στην Κηφισίας κάθε πρωί
            Ο γείτονας με το τρυπάνι
            Ο διαιτητής του Σαββάτου
            """.trimIndent(),
        )
        assertEquals(listOf("Τον εκνευρίζει το αφεντικό που τον φορτώνει υπερωρίες", "Η κίνηση στην Κηφισίας κάθε πρωί", "Ο γείτονας με το τρυπάνι", "Ο διαιτητής του Σαββάτου"), lines)
        assertTrue(Memory.parse("-").isEmpty())
    }

    @Test fun memoryIsStoredPerCharacterAndForgotten() {
        val m = Memory(ApplicationProvider.getApplicationContext())
        m.forgetAll()
        m.save("mana", listOf("α", "β", "γ", "δ", "ε"))
        assertEquals(4, m.lines("mana").size)
        assertTrue(m.lines("taxitzis").isEmpty())
        repeat(Memory.EVERY - 1) { assertFalse(m.countTurn("mana")) }
        assertTrue(m.countTurn("mana"))
        m.forget("mana")
        assertTrue(m.lines("mana").isEmpty())
        assertTrue(Memory.promptPart(emptyList()).isEmpty())
        assertTrue(Memory.promptPart(listOf("Το αφεντικό")).contains("- Το αφεντικό"))
    }

    @Test fun theTranscriptForTheMemoryIsShort() {
        val turns = (1..60).map { Turn(it % 2 == 0, "x".repeat(500)) }
        val t = Memory.transcript(turns).single().text
        assertTrue(t.lines().size == 24)
        assertTrue(t.length < 24 * 260)
    }

    @Test fun secondAiOnlyForLimitsLoadAndGoneModels() {
        assertEquals(listOf(Provider.GROQ, Provider.GEMINI, Provider.DEEPSEEK), Fallback.order(Provider.GROQ) { it != Provider.OPENROUTER })
        assertEquals(listOf(Provider.GEMINI), Fallback.order(Provider.GEMINI) { false })
        assertTrue(Fallback.worthSwitching(Reason.LIMIT))
        assertTrue(Fallback.worthSwitching(Reason.BUSY))
        assertTrue(Fallback.worthSwitching(Reason.MODEL))
        assertFalse(Fallback.worthSwitching(Reason.BAD_KEY))
        assertFalse(Fallback.worthSwitching(Reason.BLOCKED))
        assertFalse(Fallback.worthSwitching(Reason.NETWORK))
    }

    @Test fun talkingOverThePhoneIsNotItsOwnEcho() {
        val saying = "Κανένα πρόβλημα, πες μου τι έγινε με το αφεντικό σου σήμερα"
        assertFalse(PhoneVoice.userOver("με το αφεντικό σου", saying))
        assertFalse(PhoneVoice.userOver("ναι", saying))
        assertTrue(PhoneVoice.userOver("στάσου ρε φίλε άκου με", saying))
    }

    @Test fun theTryWithoutAKeyHasARealReplyForEveryLine() {
        Demo.ids.forEach { id ->
            assertTrue(Characters.byId(id) != null)
            Demo.lines.indices.forEach { assertTrue(Demo.reply(id, it).length > 20) }
        }
    }

    @Test fun weekMeterAndFriday() {
        assertEquals(0, Weekly.meter(Weekly.Week(0, 0, null, null)))
        assertEquals(8, Weekly.meter(Weekly.Week(5, 3, 8.4, 3.0)))
        assertEquals(6, Weekly.meter(Weekly.Week(10, 20, null, null)))
        assertEquals(5, Weekly.bar(10).codePointCount(0, Weekly.bar(10).length))
        val wed = Calendar.getInstance().apply { set(2026, Calendar.NOVEMBER, 4, 12, 0) }
        val f = Weekly.nextFriday(wed)
        assertEquals(Calendar.FRIDAY, f.get(Calendar.DAY_OF_WEEK))
        assertEquals(6, f.get(Calendar.DAY_OF_MONTH))
        assertEquals(18, f.get(Calendar.HOUR_OF_DAY))
        val friLate = Calendar.getInstance().apply { set(2026, Calendar.NOVEMBER, 6, 19, 0) }
        assertEquals(13, Weekly.nextFriday(friLate).get(Calendar.DAY_OF_MONTH))
    }

    @Test fun stickerIs512AndUnder100Kb() {
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        val c = Characters.byId("kavgatzis")!!
        val f = File(ctx.cacheDir, "s.webp")
        Stickers.webp(Stickers.bitmap(c, "Υπερωρίες; Και τι του είπες, «ναι κύριε»; Αν δεν του πεις ένα όχι, θα σε φορτώνει μέχρι τη σύνταξη!"), f)
        assertTrue(f.length() in 1..100 * 1024)
        val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(f.path, o)
        assertEquals(512, o.outWidth)
        assertEquals(512, o.outHeight)
    }
}
