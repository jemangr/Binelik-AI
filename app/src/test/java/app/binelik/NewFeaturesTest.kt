package app.binelik

import androidx.test.core.app.ApplicationProvider
import app.binelik.ai.Ai
import app.binelik.ai.Provider
import app.binelik.ai.Turn
import app.binelik.data.Characters
import app.binelik.data.GamePrompts
import app.binelik.data.Gender
import app.binelik.data.Settings
import app.binelik.extras.Packs
import app.binelik.extras.ShareCharacter
import app.binelik.ui.screamScore
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/** 1.2: games, packs, providers, the second custom character and sharing. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class NewFeaturesTest {
    @Test fun battleScoresAreReadAndRemoved() {
        val (text, u, me) = GamePrompts.parseBattle("Κάτσε καλά, μικρέ.\n[SCORE user=7 me=9]")
        assertEquals("Κάτσε καλά, μικρέ.", text); assertEquals(7, u); assertEquals(9, me)
        val (t2, u2, _) = GamePrompts.parseBattle("Χωρίς βαθμό")
        assertEquals("Χωρίς βαθμό", t2); assertNull(u2)
        assertEquals(10, GamePrompts.parseBattle("x [score user = 14 me = 0]").second)
    }

    @Test fun screamScoreGoesFromSilenceToFullScale() {
        assertEquals(0, screamScore(0.0))
        assertEquals(100, screamScore(32767.0))
        assertTrue(screamScore(327.0) in 30..36) // −40 dB
    }

    @Test fun thePackInTheRepositoryIsValid() {
        val text = listOf(File("../packs/characters.json"), File("packs/characters.json")).first { it.exists() }.readText()
        val packs = Packs.decode(text)
        assertEquals(5, packs.size)
        val c = Packs.character(packs.first())
        assertTrue(c.id.startsWith("pack:"))
        assertTrue(c.persona.isNotBlank())
        assertEquals(packs, Packs.decode(Packs.encode(packs)))
    }

    @Test fun newProvidersKeysAndBodies() {
        val groq = "gsk_" + "a".repeat(52)
        val or = "sk-or-v1-" + "0123456789abcdef".repeat(4)
        assertEquals(groq, Provider.GROQ.findKey("key $groq "))
        assertEquals(or, Provider.OPENROUTER.findKey(or))
        assertNull(Provider.DEEPSEEK.findKey(or))
        val g = Ai.chatBody(Provider.GROQ, "openai/gpt-oss-120b", "s", listOf(Turn(true, "x")))
        assertEquals("\"low\"", g["reasoning_effort"].toString())
        assertFalse("frequency_penalty" in g)
        assertTrue("frequency_penalty" in Ai.chatBody(Provider.OPENROUTER, "m:free", "s", listOf(Turn(true, "x"))))
    }

    @Test fun twoCustomCharactersWithTheirNames() {
        val s = Settings(ApplicationProvider.getApplicationContext())
        s.setCustom(2, "Πεθερός", "Γκρινιάζει για όλα", 0xFF22D3EE)
        val c = s.custom(2)!!
        assertEquals("custom2", c.id)
        assertEquals("Custom: Πεθερός", c.label)
        assertEquals(c.id, s.character("custom2").id)
        assertTrue(s.everyone().any { it.id == "custom2" })
        s.deleteCustom(2)
        assertNull(s.custom(2))
        assertEquals(Characters.all.first().id, s.character("custom2").id)
    }

    @Test fun aSharedCharacterTravelsInsideItsLink() {
        val link = ShareCharacter.link("Παναγιώτης", "Λέει «ρε φίλε» σε κάθε πρόταση", 0xFFFF5FA2, Gender.FEMALE)
        assertTrue(link.startsWith(ShareCharacter.PREFIX))
        val got = ShareCharacter.read("Δες τον: $link !")!!
        assertEquals("Παναγιώτης", got.n); assertEquals(Gender.FEMALE, ShareCharacter.gender(got)); assertEquals(0xFFFF5FA2, got.c)
        assertNull(ShareCharacter.read("binelik://character?d=!!!"))
        assertNull(ShareCharacter.read(null))
    }
}
