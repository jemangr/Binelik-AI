package app.binelik

import app.binelik.data.Anger
import app.binelik.data.Characters
import app.binelik.data.Intensity
import app.binelik.data.Message
import app.binelik.data.Prompts
import app.binelik.data.Region
import app.binelik.data.Style
import app.binelik.extras.StatsCalc
import app.binelik.extras.Swears
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneOffset

class ExtrasTest {
    @Test fun countsAndMasksSwearWords() {
        assertEquals(3, Swears.count("Ρε μαλάκα, γαμώτο, τι σκατά έγινε;"))
        assertEquals(0, Swears.count("Ο γάμος ήταν ωραίος και ο γαμπρός ευγενικός"))
        assertEquals("Ρε μπιπ, τι έγινε;", Swears.mask("Ρε μαλάκα, τι έγινε;"))
        assertEquals("what the beep", Swears.mask("what the fuck", en = true))
        assertEquals(listOf("μαλακα", "μαλακα"), Swears.words("μαλάκα ΜΑΛΑΚΑ"))
    }

    @Test fun styleGoesIntoThePrompt() {
        val c = Characters.all.first()
        val p = Prompts.system(c, Intensity.MEDIUM, "", Style(Region.CRETE, bleep = true))
        assertTrue(p.contains("κρητικό"))
        assertTrue(p.contains("μπιπ"))
        assertFalse(Prompts.system(c, Intensity.MEDIUM).contains("μπιπ"))
        // The rules stay last, after any style.
        assertTrue(p.indexOf(Prompts.rules) > p.indexOf("κρητικό"))
    }

    @Test fun fightAndEmailPromptsKeepTheRules() {
        val a = Characters.byId("mana")!!; val b = Characters.byId("gynaika")!!
        val f = Prompts.fight(a, b, Intensity.MEDIUM, "", "το φαγητό")
        assertTrue(f.contains(Prompts.rules) && f.contains(b.name) && f.contains("το φαγητό"))
        assertTrue(Prompts.angryEmail(a, Intensity.HEAVY, "").contains(Prompts.rules))
        assertTrue(Prompts.politeEmail.isNotBlank() && Prompts.roast.isNotBlank() && Prompts.week.isNotBlank())
    }

    private val zone = ZoneOffset.UTC
    private fun at(day: LocalDate, hour: Int = 12) = day.atTime(hour, 0).toInstant(zone).toEpochMilli()

    @Test fun statsStreakSwearsAndBadges() {
        val today = LocalDate.of(2026, 11, 4)
        val now = at(today, 20)
        val msgs = listOf(
            Message(1, "kavgatzis", true, "ρε μαλάκα", at(today.minusDays(2))),
            Message(2, "kavgatzis", false, "εμένα;", at(today.minusDays(2))),
            Message(3, "kollitos", true, "γαμώτο, πάλι", at(today.minusDays(1))),
            Message(4, "kollitos", true, "μαλάκας είναι", at(today)),
            Message(5, "kollitos", true, "παύση", at(today), pause = true),
        )
        val anger = listOf(Anger(1, "kollitos", 9, 3, at(today)), Anger(2, "kollitos", 6, 0, at(today)))
        val s = StatsCalc.compute(msgs, anger, now, zone)
        assertEquals(3, s.streak)
        assertEquals(3, s.swearsTotal)
        assertEquals(1, s.swearsToday)
        assertEquals("μαλακα", s.topWord)
        assertEquals(Characters.byId("kollitos")!!.name, s.topCharacter)
        assertEquals(6.0, s.angerDrop!!, 0.01)
        assertTrue(s.badges.first { it.icon == "💥" }.earned)
        assertTrue(s.badges.first { it.icon == "🔥" }.earned)
        assertFalse(s.badges.first { it.icon == "🏆" }.earned)
        assertTrue(s.badges.first { it.icon == "🧘" }.earned)
    }

    @Test fun emptyStats() {
        val s = StatsCalc.compute(emptyList(), emptyList())
        assertEquals(0, s.streak); assertNull(s.topWord); assertNull(s.angerDrop)
        assertTrue(s.badges.none { it.earned })
    }
}

class SpeakerTest {
    @Test fun boostIsLouderAndClips() {
        val pcm = byteArrayOf(0x00, 0x10, 0x00, 0x70.toByte()) // 4096, 28672
        val b = app.binelik.ai.Speaker.boost(pcm, 2f)
        fun s(a: ByteArray, i: Int) = ((a[i + 1].toInt() shl 8) or (a[i].toInt() and 0xFF)).toShort().toInt()
        assertEquals(8192, s(b, 0))
        assertEquals(Short.MAX_VALUE.toInt(), s(b, 2))
        org.junit.Assert.assertSame(pcm, app.binelik.ai.Speaker.boost(pcm, 1f))
    }
}
