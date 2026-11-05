package app.binelik.extras

import android.content.Context
import android.os.Build
import app.binelik.BuildConfig
import app.binelik.data.Settings
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * The bug report: what the user writes, plus the version, the phone, the main settings and the last
 * errors. Never the keys, never the conversations. It stays on the phone until the user sends it.
 */
object Report {
    private val errors = ArrayDeque<String>()

    /** The last errors of this session (the AI's answer codes, Live stops), newest last. */
    fun note(where: String, what: String) = synchronized(errors) {
        errors.addLast("${SimpleDateFormat("HH:mm:ss", Locale.ROOT).format(Date())} $where: ${what.take(200)}")
        while (errors.size > 20) errors.removeFirst()
    }

    fun recent(): List<String> = synchronized(errors) { errors.toList() }

    fun text(context: Context, s: Settings, what: String): String = buildString {
        appendLine("Binelik AI · bug report")
        appendLine()
        appendLine(what.trim().ifEmpty { "-" })
        appendLine()
        appendLine("Version: ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
        appendLine("Android ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT}) · ${Build.MANUFACTURER} ${Build.MODEL}")
        appendLine("AI: ${s.provider.label} · language: ${if (s.english) "en" else "el"} · character: ${s.characterId}")
        appendLine("Anger meter: ${s.angerMeter} · voice volume: ${s.voiceGain} · lock: ${s.lock}")
        val r = recent()
        if (r.isNotEmpty()) { appendLine(); appendLine("Last errors:"); r.forEach { appendLine(it) } }
        Crash.last(context)?.let { appendLine(); appendLine("Last crash:"); append(it.take(4000)) }
    }.trim()

    const val EMAIL = "info@3wsol.gr"
    const val SUBJECT = "Bug report Binelik AI"
    /** The report goes straight to us through platebit.app (nothing kept there); the user's email only if they write it. */
    const val ENDPOINT = "https://platebit.app/report.php"
    const val APP = "binelik"

    /** The JSON that goes to [ENDPOINT]. */
    fun payload(text: String, email: String): String = org.json.JSONObject()
        .put("app", APP).put("version", "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
        .put("text", text).put("email", email.trim()).put("website", "").toString()

    /** Sends the report directly, without the email app. True when it arrived. */
    suspend fun send(text: String, email: String): Boolean = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        runCatching {
            val c = java.net.URL(ENDPOINT).openConnection() as java.net.HttpURLConnection
            try {
                c.requestMethod = "POST"; c.doOutput = true; c.connectTimeout = 10_000; c.readTimeout = 15_000
                c.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                c.outputStream.use { it.write(payload(text, email).toByteArray()) }
                c.responseCode == 200 && c.inputStream.bufferedReader().readText().contains("\"ok\":true")
            } finally { c.disconnect() }
        }.getOrDefault(false)
    }

    /** Opens the phone's email app with the address, the subject and the report filled in. */
    fun email(context: Context, text: String): Boolean {
        val i = android.content.Intent(android.content.Intent.ACTION_SENDTO, android.net.Uri.parse("mailto:"))
            .putExtra(android.content.Intent.EXTRA_EMAIL, arrayOf(EMAIL))
            .putExtra(android.content.Intent.EXTRA_SUBJECT, SUBJECT)
            .putExtra(android.content.Intent.EXTRA_TEXT, text)
            .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
        return runCatching { context.startActivity(i) }.isSuccess
    }

    fun issueUrl(text: String): String =
        "https://github.com/jemangr/Binelik-AI/issues/new?title=" + java.net.URLEncoder.encode("Bug: ", "UTF-8") +
            "&body=" + java.net.URLEncoder.encode(text.take(5000), "UTF-8")
}
