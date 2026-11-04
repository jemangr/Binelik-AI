package app.binelik.update

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * Is there a newer version on GitHub? One request to GitHub's public API (no account, nothing
 * of the user's sent), at most every few hours, and it can be turned off in the settings.
 */
object Updates {
    const val LATEST = "https://api.github.com/repos/jemangr/Binelik-AI/releases/latest"
    /** The APK of the latest release, under a fixed name. */
    const val APK = "https://github.com/jemangr/Binelik-AI/releases/latest/download/BinelikAI.apk"
    const val EVERY_MS = 6 * 60 * 60 * 1000L

    data class Release(val version: String, val notes: String, val apk: String, val page: String)

    /** "1.10.0" > "1.9.2"; a leading "v" and anything after "-" (e.g. "-beta") are ignored. */
    fun newer(candidate: String, current: String): Boolean {
        fun parts(v: String) = v.trim().removePrefix("v").substringBefore('-').split('.').map { it.toIntOrNull() ?: 0 }
        val a = parts(candidate); val b = parts(current)
        for (i in 0 until maxOf(a.size, b.size)) {
            val x = a.getOrElse(i) { 0 }; val y = b.getOrElse(i) { 0 }
            if (x != y) return x > y
        }
        return false
    }

    private val json = Json { ignoreUnknownKeys = true }

    fun parse(body: String): Release? {
        val o = runCatching { json.parseToJsonElement(body).jsonObject }.getOrNull() ?: return null
        val tag = o["tag_name"]?.jsonPrimitive?.contentOrNull ?: return null
        val assets = (o["assets"] as? JsonArray).orEmpty().mapNotNull { it as? JsonObject }
        val apk = assets.firstOrNull { it["name"]?.jsonPrimitive?.contentOrNull == "BinelikAI.apk" }
            ?: assets.firstOrNull { it["name"]?.jsonPrimitive?.contentOrNull?.endsWith(".apk") == true }
        val url = apk?.get("browser_download_url")?.jsonPrimitive?.contentOrNull ?: APK
        return Release(
            tag.removePrefix("v"),
            o["body"]?.jsonPrimitive?.contentOrNull.orEmpty().take(1500),
            url,
            o["html_url"]?.jsonPrimitive?.contentOrNull ?: "https://github.com/jemangr/Binelik-AI/releases",
        )
    }

    private val http = OkHttpClient.Builder().connectTimeout(10, TimeUnit.SECONDS).readTimeout(15, TimeUnit.SECONDS).build()

    /** The latest release if it is newer than [current]; null if not, or if GitHub can't be reached. */
    suspend fun check(current: String): Release? = withContext(Dispatchers.IO) {
        runCatching {
            val req = Request.Builder().url(LATEST)
                .header("Accept", "application/vnd.github+json")
                .header("User-Agent", "BinelikAI-app")
                .build()
            http.newCall(req).execute().use { r -> if (r.isSuccessful) parse(r.body.string()) else null }
        }.getOrNull()?.takeIf { newer(it.version, current) }
    }
}
