package app.binelik.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

object C {
    val bg = Color(0xFF05080D)
    val panel = Color(0xFF0B1119)
    val panel2 = Color(0xFF101823)
    val line = Color(0xFF1C2A3A)
    val text = Color(0xFFE6F1FF)
    val muted = Color(0xFF8EA3BD)
    val dim = Color(0xFF5D7189)
    val danger = Color(0xFFFF4D4D)
    val ok = Color(0xFF38E08C)
    val amber = Color(0xFFFFB020)
    val warn = Color(0xFFF5B84A)
}

@Composable
fun BinelikTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = C.text, onPrimary = Color.Black, background = C.bg, onBackground = C.text,
            surface = C.panel, onSurface = C.text, surfaceVariant = C.panel2, onSurfaceVariant = C.muted,
            outline = C.line, error = C.danger,
        ),
    ) {
        // Text without its own colour is light on the dark background everywhere.
        val d = androidx.compose.ui.platform.LocalDensity.current
        androidx.compose.runtime.CompositionLocalProvider(
            androidx.compose.material3.LocalContentColor provides C.text,
            androidx.compose.ui.platform.LocalDensity provides androidx.compose.ui.unit.Density(d.density, d.fontScale * app.binelik.i18n.Ui.scale),
            content = content,
        )
    }
}

/** Logo Γ: a speech bubble with «#$%!» and a red «!». */
@Composable
fun Logo(size: Dp, color: Color = C.amber) {
    Box(Modifier.size(size).clearAndSetSemantics { }, contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(size)) {
            val s = this.size.width / 120f
            val p = Path().apply {
                moveTo(20 * s, 26 * s); lineTo(100 * s, 26 * s)
                quadraticTo(110 * s, 26 * s, 110 * s, 36 * s); lineTo(110 * s, 76 * s)
                quadraticTo(110 * s, 86 * s, 100 * s, 86 * s); lineTo(54 * s, 86 * s)
                lineTo(34 * s, 104 * s); lineTo(34 * s, 86 * s); lineTo(20 * s, 86 * s)
                quadraticTo(10 * s, 86 * s, 10 * s, 76 * s); lineTo(10 * s, 36 * s)
                quadraticTo(10 * s, 26 * s, 20 * s, 26 * s); close()
            }
            drawPath(p, C.panel)
            drawPath(p, color, style = Stroke(width = 5 * s))
            drawCircle(Color(0xFFFF3B3B), radius = 12 * s, center = Offset(98 * s, 26 * s))
            drawRoundRect(Color.White, Offset(96.5f * s, 18 * s), Size(3 * s, 9 * s), CornerRadius(1.5f * s))
            drawCircle(Color.White, radius = 1.8f * s, center = Offset(98 * s, 31.5f * s))
        }
        Text(
            "#$%!", color = color, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold,
            fontSize = (size.value * 0.19f).sp, modifier = Modifier,
        )
    }
}

/** The voice circle, in the character's colour. [active] moves it; paused, it stands still and dims. */
@Composable
fun Orb(color: Color, size: Dp, active: Boolean = true) {
    val col by animateColorAsState(color, tween(600), label = "orb")
    val t = rememberInfiniteTransition(label = "orb")
    val spin by t.animateFloat(0f, 360f, infiniteRepeatable(tween(18000, easing = LinearEasing)), label = "spin")
    val fast by t.animateFloat(360f, 0f, infiniteRepeatable(tween(6000, easing = LinearEasing)), label = "fast")
    val pulse by t.animateFloat(1f, 1.05f, infiniteRepeatable(tween(1200), RepeatMode.Reverse), label = "pulse")
    Canvas(Modifier.size(size).clearAndSetSemantics { }) {
        val r = this.size.minDimension / 2
        val c = center
        val dimmed = if (active) 1f else 0.45f
        drawCircle(Brush.radialGradient(listOf(col.copy(alpha = 0.18f * dimmed), Color.Transparent), c, r), r)
        drawCircle(col.copy(alpha = 0.7f * dimmed), r * 0.94f, c, style = Stroke(1.5.dp.toPx()))
        rotate(if (active) spin else 0f, c) {
            for (i in 0 until 60) {
                rotate(i * 6f, c) { drawLine(col.copy(alpha = 0.55f * dimmed), Offset(c.x, c.y - r * 0.99f), Offset(c.x, c.y - r * 0.93f), 1.dp.toPx()) }
            }
            drawCircle(col.copy(alpha = 0.4f * dimmed), r * 0.8f, c, style = Stroke(1.5.dp.toPx(), pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(10f, 12f))))
        }
        rotate(if (active) fast else 0f, c) {
            drawArc(col.copy(alpha = dimmed), 0f, 70f, false, Offset(c.x - r * 0.66f, c.y - r * 0.66f), Size(r * 1.32f, r * 1.32f), style = Stroke(3.dp.toPx(), cap = StrokeCap.Round))
            drawArc(col.copy(alpha = 0.4f * dimmed), 180f, 70f, false, Offset(c.x - r * 0.66f, c.y - r * 0.66f), Size(r * 1.32f, r * 1.32f), style = Stroke(3.dp.toPx(), cap = StrokeCap.Round))
        }
        val core = r * 0.48f * (if (active) pulse else 1f)
        drawCircle(Brush.radialGradient(listOf(col.copy(alpha = 0.5f * dimmed), Color.Transparent), c, core * 2.2f), core * 2.2f)
        drawCircle(
            Brush.radialGradient(listOf(Color.White.copy(alpha = 0.85f * dimmed), col.copy(alpha = dimmed), col.copy(red = col.red * 0.3f, green = col.green * 0.3f, blue = col.blue * 0.3f)), Offset(c.x - core * 0.25f, c.y - core * 0.3f), core * 1.3f),
            core, c,
        )
    }
}
