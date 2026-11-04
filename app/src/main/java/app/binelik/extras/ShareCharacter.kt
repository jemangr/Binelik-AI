package app.binelik.extras

import android.graphics.Bitmap
import android.graphics.Color
import android.util.Base64
import app.binelik.data.Gender
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** A user's own character as a link or a QR code, to give it to a friend. No server: everything is in the link. */
object ShareCharacter {
    @Serializable
    data class Shared(val n: String, val d: String, val c: Long, val g: String)

    const val PREFIX = "binelik://character?d="
    private val json = Json { ignoreUnknownKeys = true }

    fun link(name: String, description: String, color: Long, gender: Gender): String {
        val data = json.encodeToString(Shared.serializer(), Shared(name.take(40), description.take(600), color, gender.name))
        return PREFIX + Base64.encodeToString(data.toByteArray(), Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
    }

    /** The character inside a link or a copied text (the link may come with other text around it). */
    fun read(text: String?): Shared? {
        val code = text?.let { Regex("binelik://character\\?d=([A-Za-z0-9_\\-]+)").find(it)?.groupValues?.get(1) } ?: return null
        return runCatching {
            val s = json.decodeFromString(Shared.serializer(), String(Base64.decode(code, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)))
            s.copy(n = s.n.take(40), d = s.d.take(600))
        }.getOrNull()?.takeIf { it.d.isNotBlank() }
    }

    fun gender(s: Shared) = if (s.g == Gender.FEMALE.name) Gender.FEMALE else Gender.MALE

    fun qr(text: String, size: Int = 720): Bitmap {
        val m = QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, size, size, mapOf(EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M, EncodeHintType.MARGIN to 2))
        val px = IntArray(size * size) { i -> if (m[i % size, i / size]) Color.BLACK else Color.WHITE }
        return Bitmap.createBitmap(px, size, size, Bitmap.Config.ARGB_8888)
    }
}
