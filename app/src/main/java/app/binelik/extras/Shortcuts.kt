package app.binelik.extras

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import app.binelik.MainActivity
import app.binelik.data.Character

/** Long press on the icon: straight into Live with the three characters the user talks to most. */
object Shortcuts {
    const val EXTRA_CHARACTER = "app.binelik.CHARACTER"

    fun update(context: Context, top: List<Character>) {
        val list = top.take(3).mapIndexed { i, c ->
            ShortcutInfoCompat.Builder(context, "live_${c.id}")
                .setShortLabel(c.label.take(24))
                .setLongLabel("Live: ${c.label}".take(40))
                .setIcon(IconCompat.createWithBitmap(dot(c.color.toInt())))
                .setRank(i)
                .setIntent(Intent(context, MainActivity::class.java).setAction(Intent.ACTION_VIEW).putExtra(EXTRA_PLAY, true).putExtra(EXTRA_CHARACTER, c.id))
                .build()
        }
        runCatching { ShortcutManagerCompat.setDynamicShortcuts(context, list) }
    }

    private fun dot(color: Int): Bitmap {
        val b = Bitmap.createBitmap(108, 108, Bitmap.Config.ARGB_8888)
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { shader = RadialGradient(44f, 40f, 60f, intArrayOf(android.graphics.Color.WHITE, color, android.graphics.Color.BLACK), floatArrayOf(0f, 0.55f, 1f), Shader.TileMode.CLAMP) }
        Canvas(b).apply { drawColor(android.graphics.Color.rgb(5, 8, 13)); drawCircle(54f, 54f, 40f, p) }
        return b
    }
}
