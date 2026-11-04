package app.binelik.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * The update from inside the app: the APK of the release is downloaded to the app's cache and
 * Android's installer opens (the user confirms). The first time, Android asks to allow
 * «install unknown apps» for Binelik AI.
 */
object Installer {
    private val http = OkHttpClient.Builder().connectTimeout(15, TimeUnit.SECONDS).readTimeout(60, TimeUnit.SECONDS).followRedirects(true).build()

    /** Downloads [url]; [progress] 0–100. Null if it failed. */
    suspend fun download(context: Context, url: String, progress: (Int) -> Unit): File? = withContext(Dispatchers.IO) {
        runCatching {
            val dir = File(context.cacheDir, "updates").apply { mkdirs(); listFiles()?.forEach { it.delete() } }
            val f = File(dir, "BinelikAI.apk")
            http.newCall(Request.Builder().url(url).header("User-Agent", "BinelikAI-app").build()).execute().use { r ->
                if (!r.isSuccessful) return@use null
                val total = r.body.contentLength()
                r.body.byteStream().use { input ->
                    f.outputStream().use { out ->
                        val buf = ByteArray(64 * 1024); var done = 0L; var n: Int
                        while (input.read(buf).also { n = it } > 0) {
                            out.write(buf, 0, n); done += n
                            if (total > 0) progress((done * 100 / total).toInt())
                        }
                    }
                }
                f
            }
        }.getOrNull()
    }

    fun canInstall(context: Context) = context.packageManager.canRequestPackageInstalls()

    fun allowInstalls(context: Context) {
        context.startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    fun install(context: Context, apk: File) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", apk)
        context.startActivity(
            Intent(Intent.ACTION_VIEW).setDataAndType(uri, "application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }
}
