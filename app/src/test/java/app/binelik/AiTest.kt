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
