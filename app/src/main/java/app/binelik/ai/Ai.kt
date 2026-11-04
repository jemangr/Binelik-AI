package app.binelik.ai

import app.binelik.extras.Report
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
    /** The default model (the OpenAI-compatible services: changeable in the settings, their lists change). */
    val model: String = "",
    /** OpenAI-compatible chat endpoint, for all but Gemini. */
    val chatUrl: String = "",
    /** The cheapest call that needs a valid key. */
    val testUrl: String = "",
    /** Free to use (with limits). */
    val free: Boolean = false,
) {
    GEMINI("Gemini", "https://aistudio.google.com/apikey", Regex("AIza[0-9A-Za-z_\\-]{30,45}"), free = true,
        testUrl = "https://generativelanguage.googleapis.com/v1beta/models?pageSize=1"),
    DEEPSEEK("DeepSeek", "https://platform.deepseek.com/api_keys", Regex("sk-[0-9A-Za-z]{20,64}"), "deepseek-chat",
        "https://api.deepseek.com/chat/completions", "https://api.deepseek.com/models"),
    /** 1.2: free tier, very fast; gpt-oss thinks, so its effort is kept low. */
    GROQ("Groq", "https://console.groq.com/keys", Regex("gsk_[0-9A-Za-z]{40,64}"), "openai/gpt-oss-120b",
        "https://api.groq.com/openai/v1/chat/completions", "https://api.groq.com/openai/v1/models", free = true),
    /** 1.2: many models, the «:free» ones without charge (the list changes every month). */
    OPENROUTER("OpenRouter", "https://openrouter.ai/settings/keys", Regex("sk-or-v1-[0-9a-f]{64}"), "google/gemma-4-31b-it:free",
        "https://openrouter.ai/api/v1/chat/completions", "https://openrouter.ai/api/v1/key", free = true),
    ;

    /** The key inside a copied text, if there is one (people often copy a space or a line too). */
    fun findKey(text: String?): String? = text?.let { keyPattern.find(it)?.value }
}

data class Turn(val fromUser: Boolean, val text: String)

data class Source(val title: String, val uri: String)

sealed class AiResult {
    /** [sources]: the pages a Google search found; [searches]: what was searched (shown as Google links). */
    data class Ok(val text: String, val sources: List<Source> = emptyList(), val searches: List<String> = emptyList()) : AiResult()
    data class Fail(val reason: Reason, val detail: String = "") : AiResult()
}

/** [BUSY]: the service is overloaded or down for a moment (HTTP 500/503/504), not the user's fault. */
enum class Reason { BAD_KEY, LIMIT, BLOCKED, NETWORK, BUSY, MODEL, OTHER }

object Ai {
    const val GEMINI_MODEL = "gemini-flash-latest"
    /** Lighter and less busy: used when the main model is overloaded (HTTP 503). */
    const val GEMINI_FALLBACK = "gemini-flash-lite-latest"
    const val DEEPSEEK_MODEL = "deepseek-chat"

    private val json = Json { ignoreUnknownKeys = true }
    private val JSON_TYPE = "application/json; charset=utf-8".toMediaType()
    private val http = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(40, TimeUnit.SECONDS)
        // A whole call never waits longer than this; then it is retried or reported.
        .callTimeout(45, TimeUnit.SECONDS)
        .build()

    /** The last turns sent to the AI: enough for the conversation, small enough to stay cheap. */
    const val HISTORY = 30

    /**
     * [search]: Gemini may search Google for current facts (weather, scores, news) before answering.
     * [think]: the Gemini 3 models think before answering (medium by default), which is slow and counts
     * against maxOutputTokens: «low» keeps the replies quick. Older models reject the field.
     */
    fun geminiBody(system: String, turns: List<Turn>, search: Boolean = false, think: Boolean = true): JsonObject = buildJsonObject {
        if (search) put("tools", buildJsonArray { add(buildJsonObject { put("google_search", buildJsonObject { }) }) })
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
        put("generationConfig", buildJsonObject {
            put("temperature", 1.0)
            // Room for the thinking as well as the reply (the prompt keeps the replies short).
            put("maxOutputTokens", 4096)
            if (think) put("thinkingConfig", buildJsonObject { put("thinkingLevel", "low") })
        })
    }

    fun deepseekBody(system: String, turns: List<Turn>): JsonObject = chatBody(Provider.DEEPSEEK, Provider.DEEPSEEK.model, system, turns)

    /** The OpenAI-compatible body (DeepSeek, Groq, OpenRouter). */
    fun chatBody(provider: Provider, model: String, system: String, turns: List<Turn>): JsonObject = buildJsonObject {
        put("model", model)
        put("messages", buildJsonArray {
            add(buildJsonObject { put("role", "system"); put("content", system) })
            turns.takeLast(HISTORY).forEach { t ->
                add(buildJsonObject { put("role", if (t.fromUser) "user" else "assistant"); put("content", t.text) })
            }
        })
        put("temperature", if (provider == Provider.DEEPSEEK) 1.1 else 1.0)
        when (provider) {
            // gpt-oss on Groq thinks first: little effort, and room for the thinking.
            Provider.GROQ -> { put("max_tokens", 2048); if (model.startsWith("openai/gpt-oss")) put("reasoning_effort", "low") }
            else -> {
                put("max_tokens", if (provider == Provider.DEEPSEEK) 400 else 1200)
                // Less repetition of the same phrases from turn to turn.
                put("frequency_penalty", 0.7)
                put("presence_penalty", 0.5)
            }
        }
    }

    /** The reply text in a Gemini answer, or why there is none. */
    fun geminiText(body: String): AiResult {
        val o = runCatching { json.parseToJsonElement(body).jsonObject }.getOrNull() ?: return AiResult.Fail(Reason.OTHER)
        val block = (o["promptFeedback"] as? JsonObject)?.get("blockReason")?.jsonPrimitive?.contentOrNull
        if (block != null) return AiResult.Fail(Reason.BLOCKED, block)
        val cand = (o["candidates"] as? JsonArray)?.firstOrNull()?.jsonObject ?: return AiResult.Fail(Reason.OTHER)
        val text = (cand["content"] as? JsonObject)?.get("parts")?.jsonArray
            ?.mapNotNull { (it as? JsonObject)?.get("text")?.jsonPrimitive?.contentOrNull }?.joinToString("")?.trim().orEmpty()
        if (text.isNotEmpty()) {
            val g = cand["groundingMetadata"] as? JsonObject
            val sources = (g?.get("groundingChunks") as? JsonArray).orEmpty().mapNotNull { ch ->
                val w = (ch as? JsonObject)?.get("web") as? JsonObject ?: return@mapNotNull null
                val uri = w["uri"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
                Source(w["title"]?.jsonPrimitive?.contentOrNull ?: uri, uri)
            }.distinctBy { it.uri }.take(5)
            val searches = (g?.get("webSearchQueries") as? JsonArray).orEmpty().mapNotNull { it.jsonPrimitive.contentOrNull }.take(3)
            return AiResult.Ok(text, sources, searches)
        }
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
        500, 502, 503, 504 -> Reason.BUSY
        // The model is gone (the free lists of Groq and OpenRouter change).
        404 -> Reason.MODEL
        else -> Reason.OTHER
    }

    /** Remembered for the session: the model rejected the thinking level / the Google search. */
    @Volatile private var noThink = false
    @Volatile private var noSearch = false

    suspend fun reply(provider: Provider, key: String, system: String, turns: List<Turn>, search: Boolean = true): AiResult {
        if (provider != Provider.GEMINI) return send(provider, key, system, turns, false, false)
            .also { if (it is AiResult.Fail) Report.note(provider.label, "${it.reason} ${it.detail}") }
        var r = send(provider, key, system, turns, search && !noSearch, !noThink)
        // An older model does not take the thinking level: once more without it, and from then on.
        if (!noThink && r is AiResult.Fail && r.detail == "HTTP 400") {
            noThink = true
            r = send(provider, key, system, turns, search && !noSearch, false)
        }
        // The Google search has its own daily limit, and not every model takes it: then without it.
        if (search && !noSearch && r is AiResult.Fail && (r.detail == "HTTP 400" || r.detail == "HTTP 429")) {
            if (r.detail == "HTTP 400") noSearch = true
            r = send(provider, key, system, turns, false, !noThink)
        }
        if (r is AiResult.Fail) Report.note(provider.label, "${r.reason} ${r.detail}")
        return r
    }

    /**
     * 5/11 (report of 1.1.2: «Unable to resolve host», then HTTP 503): a dropped connection or an
     * overloaded model is retried twice, after 1 and 2 seconds; Gemini then answers with the lighter model.
     */
    private suspend fun send(provider: Provider, key: String, system: String, turns: List<Turn>, search: Boolean, think: Boolean): AiResult {
        val started = System.currentTimeMillis()
        var model = GEMINI_MODEL
        var r = sendOnce(provider, key, system, turns, search, think, model)
        for (wait in listOf(1000L, 2000L)) {
            if (r !is AiResult.Fail || (r.reason != Reason.NETWORK && r.reason != Reason.BUSY)) break
            if (System.currentTimeMillis() - started > 30_000) break
            Report.note(provider.label, "${r.reason} ${r.detail} · retry")
            kotlinx.coroutines.delay(wait)
            if (r.reason == Reason.BUSY) model = GEMINI_FALLBACK
            r = sendOnce(provider, key, system, turns, search, think, model)
        }
        return r
    }

    private suspend fun sendOnce(provider: Provider, key: String, system: String, turns: List<Turn>, search: Boolean, think: Boolean, model: String): AiResult = withContext(Dispatchers.IO) {
        val req = when (provider) {
            Provider.GEMINI -> Request.Builder()
                .url("https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent")
                .header("x-goog-api-key", key)
                .post(geminiBody(system, turns, search, think).toString().toRequestBody(JSON_TYPE))
            else -> Request.Builder()
                .url(provider.chatUrl)
                .header("Authorization", "Bearer $key")
                .apply { if (provider == Provider.OPENROUTER) header("X-Title", "Binelik AI") }
                .post(chatBody(provider, app.binelik.voice.Engine.settings.model(provider), system, turns).toString().toRequestBody(JSON_TYPE))
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

    /**
     * What is left of a paid balance, as text («1,98 USD»). Only DeepSeek tells it through the API
     * key; Google shows Gemini's billing only in the Google Cloud console.
     */
    suspend fun balance(provider: Provider, key: String): String? = if (provider != Provider.DEEPSEEK) null else withContext(Dispatchers.IO) {
        runCatching {
            http.newCall(Request.Builder().url("https://api.deepseek.com/user/balance").header("Authorization", "Bearer $key").get().build()).execute().use { r ->
                if (!r.isSuccessful) return@use null
                balanceText(r.body.string())
            }
        }.getOrNull()
    }

    fun balanceText(body: String): String? {
        val o = runCatching { json.parseToJsonElement(body).jsonObject }.getOrNull() ?: return null
        val infos = (o["balance_infos"] as? JsonArray).orEmpty().mapNotNull { it as? JsonObject }
        return infos.mapNotNull { i ->
            val total = i["total_balance"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
            val cur = i["currency"]?.jsonPrimitive?.contentOrNull.orEmpty()
            "${total.replace('.', ',')} $cur".trim()
        }.joinToString(" · ").ifEmpty { null }
    }

    /** Checks a key with the cheapest call there is (the list of models). */
    suspend fun testKey(provider: Provider, key: String): AiResult = withContext(Dispatchers.IO) {
        val req = when (provider) {
            Provider.GEMINI -> Request.Builder().url(provider.testUrl).header("x-goog-api-key", key)
            else -> Request.Builder().url(provider.testUrl).header("Authorization", "Bearer $key")
        }.get().build()
        try {
            http.newCall(req).execute().use { r -> if (r.isSuccessful) AiResult.Ok("") else AiResult.Fail(reasonFor(r.code), "HTTP ${r.code}") }
        } catch (e: IOException) {
            AiResult.Fail(Reason.NETWORK, e.message.orEmpty())
        }
    }
}
