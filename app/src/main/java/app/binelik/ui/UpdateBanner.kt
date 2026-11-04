package app.binelik.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.binelik.BuildConfig
import app.binelik.i18n.t

/** «Νέα έκδοση X»: download (the APK opens in the browser) or later (not shown again for this one). */
@Composable
fun UpdateBanner(vm: ChatViewModel) {
    val u by vm.update.collectAsState()
    val r = u ?: return
    val context = LocalContext.current
    Row(
        Modifier.padding(horizontal = 12.dp, vertical = 4.dp).fillMaxWidth().clip(RoundedCornerShape(14.dp))
            .background(C.amber.copy(alpha = 0.12f)).border(1.dp, C.amber.copy(alpha = 0.5f), RoundedCornerShape(14.dp))
            .padding(start = 14.dp, end = 4.dp, top = 6.dp, bottom = 6.dp)
            .semantics { liveRegion = LiveRegionMode.Polite },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(t("Νέα έκδοση ${r.version}", "New version ${r.version}"), fontWeight = FontWeight.Bold, fontSize = 14.sp, color = C.amber)
            Text(t("Έχεις την ${BuildConfig.VERSION_NAME}", "You have ${BuildConfig.VERSION_NAME}"), color = C.muted, fontSize = 12.sp)
        }
        TextButton({ vm.later() }) { Text(t("Αργότερα", "Later"), color = C.muted) }
        TextButton({ openUrl(context, r.apk) }) { Text(t("Κατέβασμα", "Download"), color = C.amber, fontWeight = FontWeight.Bold) }
    }
}
