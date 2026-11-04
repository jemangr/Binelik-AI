package app.binelik.ai

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

/** The AI services the user can choose. Only their own key is used; there is no server of ours. */
enum class Provider(
    val label: String,
    val keyPage: String,
    /** What a copied key looks like, for the automatic paste. */
    val keyPattern: Regex,
) {
    GEMINI("Gemini", "https://aistudio.google.com/apikey", Regex("AIza[0-9A-Za-z_\\-]{30,45}")),
    DEEPSEEK("DeepSeek", "https://platform.deepseek.com/api_keys", Regex("sk-[0-9A-Za-z]{20,64}")),
    ;

    /** The key inside a copied text, if there is one (people often copy a space or a line too). */
    fun findKey(text: String?): String? = text?.let { keyPattern.find(it)?.value }
}

data class Turn(val fromUser: Boolean, val text: String)

sealed class AiResult {
    data class Ok(val text: String) : AiResult()
    data class Fail(val reason: Reason, val detail: String = "") : AiResult()
}

enum class Reason { BAD_KEY, LIMIT, BLOCKED, NETWORK, OTHER }

object Ai {
    const val GEMINI_MODEL = "gemini-flash-latest"
    const val DEEPSEEK_MODEL = "deepseek-chat"

    private val json = Json { ignoreUnknownKeys = true }
    private val JSON_TYPE = "application/json; charset=utf-8".toMediaType()
    private val http = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    /** The last turns sent to the AI: enough for the conversation, small enough to stay cheap. */
    const val HISTORY = 30

    fun geminiBody(system: String, turns: List<Turn>): JsonObject = buildJsonObject {
        put("systemInstruction", buildJsonObject { put("parts", buildJsonArray { add(buildJsonObject { put("text", system) }) }) })
        put("contents", buildJsonArray {
            turns.takeLast(HISTORY).forEach { t ->
                add(buildJsonObject {
                    put("role", if (t.fromUser) "user" else "model")
                    put("parts", buildJsonArray { add(buildJsonObject { put("text", t.text) }) })
                })
            }
        })
        // Insults at the character are the point of the app, so harassment is not blocked;
        // hate speech, sexual and dangerous content stay blocked as usual.
        put("safetySettings", buildJsonArray {
            listOf(
                "HARM_CATEGORY_HARASSMENT" to "BLOCK_NONE",
                "HARM_CATEGORY_HATE_SPEECH" to "BLOCK_MEDIUM_AND_ABOVE",
                "HARM_CATEGORY_SEXUALLY_EXPLICIT" to "BLOCK_MEDIUM_AND_ABOVE",
                "HARM_CATEGORY_DANGEROUS_CONTENT" to "BLOCK_MEDIUM_AND_ABOVE",
            ).forEach { (c, t) -> add(buildJsonObject { put("category", c); put("threshold", t) }) }
        })
        put("generationConfig", buildJsonObject { put("temperature", 1.0); put("maxOutputTokens", 600) })
    }

    fun deepseekBody(system: String, turns: List<Turn>): JsonObject = buildJsonObject {
        put("model", DEEPSEEK_MODEL)
        put("messages", buildJsonArray {
            add(buildJsonObject { put("role", "system"); put("content", system) })
            turns.takeLast(HISTORY).forEach { t ->
                add(buildJsonObject { put("role", if (t.fromUser) "user" else "assistant"); put("content", t.text) })
            }
        })
        put("temperature", 1.1)
        put("max_tokens", 400)
    }

    /** The reply text in a Gemini answer, or why there is none. */
    fun geminiText(body: String): AiResult {
        val o = runCatching { json.parseToJsonElement(body).jsonObject }.getOrNull() ?: return AiResult.Fail(Reason.OTHER)
        val block = (o["promptFeedback"] as? JsonObject)?.get("blockReason")?.jsonPrimitive?.contentOrNull
        if (block != null) return AiResult.Fail(Reason.BLOCKED, block)
        val cand = (o["candidates"] as? JsonArray)?.firstOrNull()?.jsonObject ?: return AiResult.Fail(Reason.OTHER)
        val text = (cand["content"] as? JsonObject)?.get("parts")?.jsonArray
            ?.mapNotNull { (it as? JsonObject)?.get("text")?.jsonPrimitive?.contentOrNull }?.joinToString("")?.trim().orEmpty()
        if (text.isNotEmpty()) return AiResult.Ok(text)
        val finish = cand["finishReason"]?.jsonPrimitive?.contentOrNull
        return AiResult.Fail(if (finish == "SAFETY" || finish == "PROHIBITED_CONTENT") Reason.BLOCKED else Reason.OTHER, finish ?: "")
    }

    fun deepseekText(body: String): AiResult {
        val o = runCatching { json.parseToJsonElement(body).jsonObject }.getOrNull() ?: return AiResult.Fail(Reason.OTHER)
        val text = (o["choices"] as? JsonArray)?.firstOrNull()?.jsonObject?.get("message")?.jsonObject
            ?.get("content")?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
        return if (text.isNotEmpty()) AiResult.Ok(text) else AiResult.Fail(Reason.OTHER)
    }

    fun reasonFor(code: Int): Reason = when (code) {
        400, 401, 403 -> Reason.BAD_KEY
        402 -> Reason.LIMIT // DeepSeek: no balance left
        429 -> Reason.LIMIT
        else -> Reason.OTHER
    }

    suspend fun reply(provider: Provider, key: String, system: String, turns: List<Turn>): AiResult = withContext(Dispatchers.IO) {
        val req = when (provider) {
            Provider.GEMINI -> Request.Builder()
                .url("https://generativelanguage.googleapis.com/v1beta/models/$GEMINI_MODEL:generateContent")
                .header("x-goog-api-key", key)
                .post(geminiBody(system, turns).toString().toRequestBody(JSON_TYPE))
            Provider.DEEPSEEK -> Request.Builder()
                .url("https://api.deepseek.com/chat/completions")
                .header("Authorization", "Bearer $key")
                .post(deepseekBody(system, turns).toString().toRequestBody(JSON_TYPE))
        }.build()
        try {
            http.newCall(req).execute().use { r ->
                val body = r.body.string()
                if (!r.isSuccessful) return@withContext AiResult.Fail(reasonFor(r.code), "HTTP ${r.code}")
                if (provider == Provider.GEMINI) geminiText(body) else deepseekText(body)
            }
        } catch (e: IOException) {
            AiResult.Fail(Reason.NETWORK, e.message.orEmpty())
        }
    }

    /** Checks a key with the cheapest call there is (the list of models). */
    suspend fun testKey(provider: Provider, key: String): AiResult = withContext(Dispatchers.IO) {
        val req = when (provider) {
            Provider.GEMINI -> Request.Builder().url("https://generativelanguage.googleapis.com/v1beta/models?pageSize=1").header("x-goog-api-key", key)
            Provider.DEEPSEEK -> Request.Builder().url("https://api.deepseek.com/models").header("Authorization", "Bearer $key")
        }.get().build()
        try {
            http.newCall(req).execute().use { r -> if (r.isSuccessful) AiResult.Ok("") else AiResult.Fail(reasonFor(r.code), "HTTP ${r.code}") }
        } catch (e: IOException) {
            AiResult.Fail(Reason.NETWORK, e.message.orEmpty())
        }
    }
}
