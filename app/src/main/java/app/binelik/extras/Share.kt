package app.binelik.extras

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import androidx.core.content.FileProvider
import app.binelik.data.Character
import app.binelik.data.Message
import app.binelik.i18n.t
import java.io.File

/** Sharing: a card with a character's line (optionally with the user's line before it), or a conversation as text. */
object Share {
    /** The card, 1080 × 1080: dark, the character's colour, the line, the logo's name and the repository. */
    fun card(c: Character, line: String, mine: String?): Bitmap {
        val size = 1080
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val cv = Canvas(bmp)
        val col = c.color.toInt()
        cv.drawColor(Color.rgb(5, 8, 13))
        val glow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = RadialGradient(size / 2f, 300f, 520f, intArrayOf((col and 0x00FFFFFF) or 0x55000000, Color.TRANSPARENT), null, Shader.TileMode.CLAMP)
        }
        cv.drawCircle(size / 2f, 300f, 520f, glow)
        val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 6f; color = col }
        cv.drawCircle(size / 2f, 230f, 120f, ring)
        val core = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = RadialGradient(size / 2f - 30, 200f, 130f, intArrayOf(Color.WHITE, col, Color.BLACK), floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP)
        }
        cv.drawCircle(size / 2f, 230f, 78f, core)

        val name = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { color = col; textSize = 58f; typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD); textAlign = Paint.Align.CENTER }
        cv.drawText(c.name, size / 2f, 430f, name)

        var y = 490f
        if (!mine.isNullOrBlank()) {
            val p = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(142, 163, 189); textSize = 36f }
            y = text(cv, t("Εγώ: ", "Me: ") + "«${mine.take(140)}»", p, y, 3) + 28f
        }
        val q = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(230, 241, 255); textSize = 50f; typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD) }
        text(cv, "«${line.take(260)}»", q, y, 7)

        val bubble = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 5f; color = Color.rgb(255, 176, 32) }
        cv.drawRoundRect(RectF(80f, 960f, 168f, 1020f), 12f, 12f, bubble)
        val logo = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(255, 176, 32); textSize = 24f; typeface = Typeface.MONOSPACE }
        cv.drawText("#$%!", 92f, 998f, logo)
        val brand = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; textSize = 34f; typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD) }
        cv.drawText("Binelik AI", 190f, 1000f, brand)
        val repo = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(93, 113, 137); textSize = 26f; textAlign = Paint.Align.RIGHT }
        cv.drawText("github.com/jemangr/Binelik-AI", size - 80f, 1000f, repo)
        return bmp
    }

    private fun text(cv: Canvas, s: String, p: TextPaint, y: Float, maxLines: Int): Float {
        val l = StaticLayout.Builder.obtain(s, 0, s.length, p, 920).setAlignment(Layout.Alignment.ALIGN_CENTER)
            .setMaxLines(maxLines).setEllipsize(android.text.TextUtils.TruncateAt.END).setLineSpacing(8f, 1f).build()
        cv.save(); cv.translate(80f, y); l.draw(cv); cv.restore()
        return y + l.height
    }

    fun shareCard(context: Context, c: Character, line: String, mine: String?) {
        val dir = File(context.cacheDir, "share").apply { mkdirs() }
        val f = File(dir, "binelik.png")
        f.outputStream().use { card(c, line, mine).compress(Bitmap.CompressFormat.PNG, 100, it) }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", f)
        val send = Intent(Intent.ACTION_SEND).setType("image/png").putExtra(Intent.EXTRA_STREAM, uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        context.startActivity(Intent.createChooser(send, t("Κοινοποίηση", "Share")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    /** A conversation as plain text. */
    fun conversationText(c: Character, messages: List<Message>): String = buildString {
        appendLine("Binelik AI · ${c.name}")
        appendLine()
        messages.forEach { m -> appendLine((if (m.fromUser) t("Εγώ", "Me") else c.name) + ": " + m.text); appendLine() }
    }.trim()

    fun shareText(context: Context, text: String) {
        val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
        context.startActivity(Intent.createChooser(send, t("Κοινοποίηση", "Share")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}
