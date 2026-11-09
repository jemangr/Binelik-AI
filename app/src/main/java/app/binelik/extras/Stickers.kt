package app.binelik.extras

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.UriMatcher
import android.content.res.AssetFileDescriptor
import android.database.Cursor
import android.database.MatrixCursor
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import androidx.core.content.FileProvider
import app.binelik.data.Character
import app.binelik.i18n.t
import java.io.File

/**
 * 1.3: a character's line as a sticker (512 × 512 WebP, transparent), to send in Viber, WhatsApp,
 * Messenger or anywhere that takes an image, and an own «Binelik AI» pack for WhatsApp's sticker tray
 * (WhatsApp wants 3 to 30 stickers in a pack). Only on the phone; nothing is uploaded anywhere.
 */
object Stickers {
    const val PACK_ID = "binelik_mine"
    const val MIN = 3
    const val MAX = 30

    fun dir(context: Context) = File(context.filesDir, "stickers").apply { mkdirs() }
    fun files(context: Context): List<File> = dir(context).listFiles { f -> f.name.endsWith(".webp") }.orEmpty().sortedBy { it.name }

    /** The sticker: a speech bubble in the character's colour with the line, on a transparent square. */
    fun bitmap(c: Character, line: String): Bitmap {
        val size = 512
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val cv = Canvas(bmp)
        val col = c.color.toInt()
        val box = RectF(16f, 40f, size - 16f, size - 96f)
        val bubble = Path().apply {
            addRoundRect(box, 48f, 48f, Path.Direction.CW)
            moveTo(120f, box.bottom - 2); lineTo(96f, box.bottom + 64); lineTo(200f, box.bottom - 2); close()
        }
        cv.drawPath(bubble, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(12, 16, 22) })
        cv.drawPath(bubble, Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 10f; color = col })
        val text = "«${line.trim().take(140)}»"
        var ts = 46f
        var layout: StaticLayout
        // The biggest letters that fit in the bubble.
        while (true) {
            val p = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; textSize = ts; typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD) }
            layout = StaticLayout.Builder.obtain(text, 0, text.length, p, (box.width() - 56).toInt()).setAlignment(Layout.Alignment.ALIGN_CENTER).setLineSpacing(4f, 1f).build()
            if (layout.height <= box.height() - 48 || ts <= 22f) break
            ts -= 2f
        }
        cv.save(); cv.translate(box.left + 28, box.top + (box.height() - layout.height) / 2f); layout.draw(cv); cv.restore()
        val name = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = col; textSize = 34f; typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            setShadowLayer(6f, 0f, 2f, Color.BLACK)
        }
        cv.drawText(c.name.take(22), 216f, size - 30f, name)
        return bmp
    }

    /** WhatsApp: static stickers exactly 512 × 512 and at most 100 KB. */
    fun webp(bmp: Bitmap, file: File) {
        for (q in listOf(90, 75, 60, 45)) {
            // 1.3.4 (Lint): WEBP_LOSSY exists from Android 11; on Android 10 the old WEBP (lossy with q < 100)
            val fmt = if (android.os.Build.VERSION.SDK_INT >= 30) Bitmap.CompressFormat.WEBP_LOSSY else @Suppress("DEPRECATION") Bitmap.CompressFormat.WEBP
            file.outputStream().use { bmp.compress(fmt, q, it) }
            if (file.length() <= 100 * 1024) return
        }
    }

    /** Sends the sticker as an image (Viber, Messenger, Telegram, WhatsApp chat…). */
    fun share(context: Context, c: Character, line: String) {
        val f = File(File(context.cacheDir, "share").apply { mkdirs() }, "binelik-sticker.webp")
        webp(bitmap(c, line), f)
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", f)
        val send = Intent(Intent.ACTION_SEND).setType("image/webp").putExtra(Intent.EXTRA_STREAM, uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        context.startActivity(Intent.createChooser(send, t("Στείλε το sticker", "Send the sticker")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    /** Keeps the sticker in the own pack (the oldest goes when there are [MAX]); returns how many there are. */
    fun keep(context: Context, c: Character, line: String): Int {
        val all = files(context)
        if (all.size >= MAX) all.first().delete()
        webp(bitmap(c, line), File(dir(context), "s${System.currentTimeMillis()}.webp"))
        icon(context)
        return files(context).size
    }

    /** The tray icon WhatsApp shows for the pack: 96 × 96 PNG. */
    private fun icon(context: Context) {
        val f = File(dir(context), "tray.png")
        if (f.exists()) return
        val b = Bitmap.createBitmap(96, 96, Bitmap.Config.ARGB_8888)
        val cv = Canvas(b)
        cv.drawRoundRect(RectF(4f, 4f, 92f, 92f), 22f, 22f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(12, 16, 22) })
        cv.drawRoundRect(RectF(4f, 4f, 92f, 92f), 22f, 22f, Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 5f; color = Color.rgb(255, 176, 32) })
        cv.drawText("#$%!", 48f, 58f, TextPaint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(255, 176, 32); textSize = 26f; typeface = Typeface.MONOSPACE; textAlign = Paint.Align.CENTER })
        f.outputStream().use { b.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    fun whatsappInstalled(context: Context): Boolean = listOf("com.whatsapp", "com.whatsapp.w4b").any {
        runCatching { context.packageManager.getPackageInfo(it, 0) }.isSuccess
    }

    /** Asks WhatsApp to add (or update) the pack; the user confirms in WhatsApp. */
    fun addToWhatsApp(context: Context) {
        val i = Intent("com.whatsapp.intent.action.ENABLE_STICKER_PACK")
            .putExtra("sticker_pack_id", PACK_ID)
            .putExtra("sticker_pack_authority", "${context.packageName}.stickers")
            .putExtra("sticker_pack_name", "Binelik AI")
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(i) }
    }
}

/**
 * The provider WhatsApp reads the own pack from (its sticker-pack contract): the pack's details,
 * the list of stickers and the files. Read only by WhatsApp (permission com.whatsapp.sticker.READ).
 */
class StickerProvider : ContentProvider() {
    private lateinit var matcher: UriMatcher
    private val authority get() = "${context!!.packageName}.stickers"

    override fun onCreate(): Boolean {
        matcher = UriMatcher(UriMatcher.NO_MATCH).apply {
            addURI(authority, "metadata", 1)
            addURI(authority, "metadata/*", 2)
            addURI(authority, "stickers/*", 3)
            addURI(authority, "stickers_asset/*/*", 4)
        }
        return true
    }

    override fun query(uri: Uri, projection: Array<out String>?, selection: String?, args: Array<out String>?, sort: String?): Cursor? {
        val ctx = context ?: return null
        return when (matcher.match(uri)) {
            1, 2 -> MatrixCursor(arrayOf(
                "sticker_pack_identifier", "sticker_pack_name", "sticker_pack_publisher", "sticker_pack_icon",
                "android_play_store_link", "ios_app_download_link", "sticker_pack_publisher_email", "sticker_pack_publisher_website",
                "sticker_pack_privacy_policy_website", "sticker_pack_license_agreement_website", "image_data_version",
                "whatsapp_will_not_cache_stickers", "animated_sticker_pack",
            )).apply {
                addRow(arrayOf<Any>(
                    Stickers.PACK_ID, "Binelik AI", "Binelik AI", "tray.png", "", "", "info@3wsol.gr",
                    "https://github.com/jemangr/Binelik-AI", "https://github.com/jemangr/Binelik-AI/blob/main/PRIVACY.md",
                    "https://github.com/jemangr/Binelik-AI/blob/main/LICENSE", Stickers.files(ctx).size.toString(), 0, 0,
                ))
            }
            3 -> MatrixCursor(arrayOf("sticker_file_name", "sticker_emoji", "sticker_accessibility_text")).apply {
                Stickers.files(ctx).forEach { addRow(arrayOf<Any>(it.name, "😤", "Binelik AI")) }
            }
            else -> null
        }
    }

    override fun openAssetFile(uri: Uri, mode: String): AssetFileDescriptor? {
        val ctx = context ?: return null
        if (matcher.match(uri) != 4) return null
        val name = uri.lastPathSegment ?: return null
        // Only the pack's own files, never a path outside the folder.
        if (name.contains('/') || name.contains("..")) return null
        val f = File(Stickers.dir(ctx), name)
        if (!f.exists()) return null
        return AssetFileDescriptor(ParcelFileDescriptor.open(f, ParcelFileDescriptor.MODE_READ_ONLY), 0, f.length())
    }

    override fun getType(uri: Uri): String? = when (matcher.match(uri)) {
        1 -> "vnd.android.cursor.dir/vnd.$authority.metadata"
        2 -> "vnd.android.cursor.item/vnd.$authority.metadata"
        3 -> "vnd.android.cursor.dir/vnd.$authority.stickers"
        4 -> if (uri.lastPathSegment?.endsWith(".png") == true) "image/png" else "image/webp"
        else -> null
    }

    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, args: Array<out String>?): Int = 0
    override fun update(uri: Uri, values: ContentValues?, selection: String?, args: Array<out String>?): Int = 0
}
