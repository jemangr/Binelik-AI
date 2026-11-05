package app.binelik.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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

/**
 * «Νέα έκδοση X» as a popup on the home screen (Live or chat) as soon as it is found: update (the APK
 * downloads and Android installs it), later (comes back at the next check) or skip this version.
 */
@Composable
fun UpdateBanner(vm: ChatViewModel) {
    val u by vm.update.collectAsState()
    val r = u ?: return
    val context = LocalContext.current
    val dl by vm.downloading.collectAsState()
    androidx.compose.material3.AlertDialog(
        onDismissRequest = { if (dl == null) vm.later() }, containerColor = C.panel,
        title = { Text(t("Νέα έκδοση ${r.version}", "New version ${r.version}"), fontWeight = FontWeight.ExtraBold, color = C.amber) },
        text = {
            Column(Modifier.semantics { liveRegion = LiveRegionMode.Polite }) {
                Text(t("Έχεις την ${BuildConfig.VERSION_NAME}.", "You have ${BuildConfig.VERSION_NAME}."), color = C.muted, fontSize = 13.sp)
                if (r.notes.isNotBlank()) Text(r.notes.take(600), color = C.dim, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp).heightIn(max = 220.dp).verticalScroll(rememberScrollState()))
                Text(t("Την πρώτη φορά το Android ζητά να επιτρέψεις την εγκατάσταση από το Binelik AI.", "The first time, Android asks you to allow installs from Binelik AI."), color = C.dim, fontSize = 11.5.sp, modifier = Modifier.padding(top = 8.dp))
            }
        },
        confirmButton = {
            TextButton({ vm.installUpdate(context) }, enabled = dl == null) {
                Text(dl?.let { "$it%" } ?: t("Ενημέρωση", "Update"), color = C.amber, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton({ vm.skip() }, enabled = dl == null) { Text(t("Παράλειψη", "Skip"), color = C.dim) }
                TextButton({ vm.later() }, enabled = dl == null) { Text(t("Αργότερα", "Later"), color = C.muted) }
            }
        },
    )
}
