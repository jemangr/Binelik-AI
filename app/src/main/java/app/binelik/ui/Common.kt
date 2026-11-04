package app.binelik.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

fun openUrl(context: Context, url: String) {
    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
}

fun dial(context: Context, number: String) {
    runCatching { context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$number")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
}

@Composable
fun Hud(text: String, modifier: Modifier = Modifier) {
    Text(text, color = C.dim, fontFamily = FontFamily.Monospace, fontSize = 10.sp, letterSpacing = 1.sp, modifier = modifier)
}

@Composable
fun PrimaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    Button(
        onClick, modifier.fillMaxWidth().heightIn(min = 50.dp), enabled = enabled, shape = RoundedCornerShape(14.dp),
        colors = ButtonDefaults.buttonColors(containerColor = C.text, contentColor = Color.Black, disabledContainerColor = C.panel2, disabledContentColor = C.dim),
    ) { Text(text, fontWeight = FontWeight.Bold, fontSize = 15.sp) }
}

@Composable
fun SecondaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    OutlinedButton(
        onClick, modifier.fillMaxWidth().heightIn(min = 48.dp), shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, C.line), colors = ButtonDefaults.outlinedButtonColors(contentColor = C.muted),
    ) { Text(text, fontWeight = FontWeight.SemiBold) }
}

/** A row of choices where one is selected (intensity, provider, voice). */
@Composable
fun <T> Segmented(options: List<T>, selected: T, label: (T) -> String, accent: Color, onPick: (T) -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(C.panel2).border(1.dp, C.line, RoundedCornerShape(12.dp)).padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        options.forEach { o ->
            val on = o == selected
            Box(
                Modifier.weight(1f).clip(RoundedCornerShape(9.dp)).background(if (on) accent else Color.Transparent)
                    .selectable(on, role = Role.RadioButton) { onPick(o) }.heightIn(min = 40.dp).padding(horizontal = 6.dp),
                contentAlignment = Alignment.Center,
            ) { Text(label(o), color = if (on) Color.Black else C.muted, fontWeight = FontWeight.SemiBold, fontSize = 13.sp) }
        }
    }
}

@Composable
fun Panel(modifier: Modifier = Modifier, padding: PaddingValues = PaddingValues(14.dp), content: @Composable () -> Unit) {
    Column(modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(C.panel).border(1.dp, C.line, RoundedCornerShape(16.dp)).padding(padding)) { content() }
}

@Composable
fun Dot(color: Color, size: Int = 12) {
    Box(Modifier.size(size.dp).clip(CircleShape).background(color))
}
