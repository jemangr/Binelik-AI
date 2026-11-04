package app.binelik

import app.binelik.ai.Ai
import app.binelik.ai.AiResult
import app.binelik.ai.Provider
import app.binelik.ai.Reason
import app.binelik.ai.Turn
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AiTest {
    @Test fun findsTheKeyInACopiedText() {
        val g = "AIzaSyD" + "a".repeat(32)
        assertEquals(g, Provider.GEMINI.findKey("  $g\n"))
        assertNull(Provider.GEMINI.findKey("γεια σου"))
        val d = "sk-" + "0123456789abcdef0123456789abcdef"
        assertEquals(d, Provider.DEEPSEEK.findKey("key: $d"))
        assertNull(Provider.DEEPSEEK.findKey(g))
    }

    @Test fun geminiBodyHasRolesAndSafetySettings() {
        val b = Ai.geminiBody("system", listOf(Turn(true, "γεια"), Turn(false, "τι θες;")))
        val contents = b["contents"]!!.jsonArray
        assertEquals("user", contents[0].jsonObject["role"]!!.jsonPrimitive.content)
        assertEquals("model", contents[1].jsonObject["role"]!!.jsonPrimitive.content)
        val safety = b["safetySettings"]!!.jsonArray.associate { it.jsonObject["category"]!!.jsonPrimitive.content to it.jsonObject["threshold"]!!.jsonPrimitive.content }
        assertEquals("BLOCK_NONE", safety["HARM_CATEGORY_HARASSMENT"])
        assertEquals("BLOCK_MEDIUM_AND_ABOVE", safety["HARM_CATEGORY_HATE_SPEECH"])
        assertEquals("BLOCK_MEDIUM_AND_ABOVE", safety["HARM_CATEGORY_SEXUALLY_EXPLICIT"])
    }

    @Test fun onlyTheLastTurnsAreSent() {
        val turns = (1..50).map { Turn(it % 2 == 1, "μήνυμα $it") }
        assertEquals(Ai.HISTORY, Ai.geminiBody("s", turns)["contents"]!!.jsonArray.size)
        assertEquals(Ai.HISTORY + 1, Ai.deepseekBody("s", turns)["messages"]!!.jsonArray.size)
    }

    @Test fun readsGeminiAnswers() {
        assertEquals(AiResult.Ok("Έλα ρε!"), Ai.geminiText("""{"candidates":[{"content":{"parts":[{"text":"Έλα "},{"text":"ρε!"}]}}]}"""))
        assertEquals(Reason.BLOCKED, (Ai.geminiText("""{"promptFeedback":{"blockReason":"SAFETY"}}""") as AiResult.Fail).reason)
        assertEquals(Reason.BLOCKED, (Ai.geminiText("""{"candidates":[{"finishReason":"SAFETY"}]}""") as AiResult.Fail).reason)
        assertEquals(Reason.OTHER, (Ai.geminiText("not json") as AiResult.Fail).reason)
    }

    @Test fun readsDeepseekAnswers() {
        assertEquals(AiResult.Ok("Άσ' τα"), Ai.deepseekText("""{"choices":[{"message":{"role":"assistant","content":" Άσ' τα "}}]}"""))
    }

    @Test fun httpCodes() {
        assertEquals(Reason.BAD_KEY, Ai.reasonFor(401))
        assertEquals(Reason.BAD_KEY, Ai.reasonFor(400))
        assertEquals(Reason.LIMIT, Ai.reasonFor(429))
        assertEquals(Reason.LIMIT, Ai.reasonFor(402))
        assertEquals(Reason.OTHER, Ai.reasonFor(500))
    }
}

class LiveTest {
    private val json = kotlinx.serialization.json.Json

    @Test fun setupHasVoiceLanguageAndTranscriptions() {
        val s = json.parseToJsonElement(app.binelik.ai.LiveProtocol.setup("m", "sys", app.binelik.ai.LiveProtocol.Options("Kore", "el-GR", null))).jsonObject["setup"]!!.jsonObject
        assertEquals("models/m", s["model"]!!.jsonPrimitive.content)
        val g = s["generationConfig"]!!.jsonObject
        assertEquals("AUDIO", g["responseModalities"]!!.jsonArray[0].jsonPrimitive.content)
        val speech = g["speechConfig"]!!.jsonObject
        assertEquals("el-GR", speech["languageCode"]!!.jsonPrimitive.content)
        assertEquals("Kore", speech["voiceConfig"]!!.jsonObject["prebuiltVoiceConfig"]!!.jsonObject["voiceName"]!!.jsonPrimitive.content)
        org.junit.Assert.assertTrue("inputAudioTranscription" in s && "outputAudioTranscription" in s)
        assertEquals("sys", s["systemInstruction"]!!.jsonObject["parts"]!!.jsonArray[0].jsonObject["text"]!!.jsonPrimitive.content)
    }

    @Test fun retryDropsWhatTheModelRefuses() {
        val o = app.binelik.ai.LiveProtocol.Options("Kore", "el-GR", null)
        assertEquals(o.copy(language = null), app.binelik.ai.LiveProtocol.retry("Invalid language code", o))
        assertEquals(o.copy(voice = null), app.binelik.ai.LiveProtocol.retry("voice not found", o))
        assertEquals(o.copy(thinking = "low"), app.binelik.ai.LiveProtocol.retry("thinking level required", o))
        assertNull(app.binelik.ai.LiveProtocol.retry("API key not valid", o))
    }

    @Test fun readsServerMessages() {
        val audio = java.util.Base64.getEncoder().encodeToString(byteArrayOf(1, 2, 3))
        val ev = app.binelik.ai.LiveProtocol.parse("""{"serverContent":{"modelTurn":{"parts":[{"inlineData":{"mimeType":"audio/pcm;rate=24000","data":"$audio"}}]},"outputTranscription":{"text":"Έλα"},"turnComplete":true}}""")
        assertEquals(3, (ev[0] as app.binelik.ai.LiveEvent.Audio).pcm.size)
        assertEquals(app.binelik.ai.LiveEvent.Said("Έλα"), ev[1])
        assertEquals(app.binelik.ai.LiveEvent.TurnComplete, ev[2])
        assertEquals(listOf(app.binelik.ai.LiveEvent.Ready), app.binelik.ai.LiveProtocol.parse("""{"setupComplete":{}}"""))
        assertEquals(listOf(app.binelik.ai.LiveEvent.Heard("γεια"), app.binelik.ai.LiveEvent.Interrupted), app.binelik.ai.LiveProtocol.parse("""{"serverContent":{"inputTranscription":{"text":"γεια"},"interrupted":true}}"""))
    }

    @Test fun eachCharacterHasAVoiceOfItsGender() {
        app.binelik.data.Characters.all.forEach { c ->
            val v = app.binelik.ai.LiveProtocol.voiceFor(c, c.gender, null)
            org.junit.Assert.assertTrue(c.id, v in app.binelik.ai.LiveProtocol.voices(c.gender))
            assertEquals("Leda", app.binelik.ai.LiveProtocol.voiceFor(c, app.binelik.data.Gender.FEMALE, 2))
        }
    }

    @Test fun livePromptAsksForTheSpokenPauseAndKeepsTheConversation() {
        val c = app.binelik.data.Characters.all.first()
        val p = app.binelik.data.Prompts.live(c, app.binelik.data.Intensity.MEDIUM, "ΑΕΚ", listOf(true to "ρε μαλάκα", false to "εμένα;"))
        org.junit.Assert.assertTrue(p.contains(app.binelik.data.Prompts.LIVE_PAUSE))
        org.junit.Assert.assertFalse(p.contains("ξεκίνα την απάντηση με ${app.binelik.data.Prompts.PAUSE}"))
        org.junit.Assert.assertTrue(p.contains("Χρήστης: ρε μαλάκα"))
    }
}

class GroundingTest {
    @Test fun readsSourcesAndSearches() {
        val r = Ai.geminiText("""{"candidates":[{"content":{"parts":[{"text":"Αύριο 18 βαθμοί, ρε."}]},
          "groundingMetadata":{"webSearchQueries":["καιρός Αθήνα αύριο"],"groundingChunks":[{"web":{"uri":"https://x/1","title":"meteo.gr"}},{"web":{"uri":"https://x/1","title":"meteo.gr"}}]}}]}""") as AiResult.Ok
        assertEquals(listOf(app.binelik.ai.Source("meteo.gr", "https://x/1")), r.sources)
        assertEquals(listOf("καιρός Αθήνα αύριο"), r.searches)
    }

    @Test fun searchToolOnlyWhenAsked() {
        org.junit.Assert.assertTrue("tools" in Ai.geminiBody("s", listOf(Turn(true, "x")), search = true))
        org.junit.Assert.assertFalse("tools" in Ai.geminiBody("s", listOf(Turn(true, "x"))))
        val live = kotlinx.serialization.json.Json.parseToJsonElement(app.binelik.ai.LiveProtocol.setup("m", "s", app.binelik.ai.LiveProtocol.Options(null, null, null))).jsonObject["setup"]!!.jsonObject
        org.junit.Assert.assertTrue("tools" in live)
        assertEquals(app.binelik.ai.LiveProtocol.Options(null, null, null, search = false), app.binelik.ai.LiveProtocol.retry("Unsupported tool: googleSearch", app.binelik.ai.LiveProtocol.Options(null, null, null)))
    }

    @Test fun deepseekBalance() {
        assertEquals("1,98 USD", Ai.balanceText("""{"is_available":true,"balance_infos":[{"currency":"USD","total_balance":"1.98","granted_balance":"0.00","topped_up_balance":"1.98"}]}"""))
        assertNull(Ai.balanceText("""{"balance_infos":[]}"""))
    }

    @Test fun promptsAskForRealAnswersWithTheDate() {
        val p = app.binelik.data.Prompts.system(app.binelik.data.Characters.all.first(), app.binelik.data.Intensity.MEDIUM, "", app.binelik.data.Style(city = "Καλλιθέα"))
        org.junit.Assert.assertTrue(p.contains("ΠΡΑΓΜΑΤΙΚΗ") && p.contains("Καλλιθέα") && p.contains(java.time.Year.now().toString()))
    }
}
