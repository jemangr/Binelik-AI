package app.binelik.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.binelik.extras.Report
import app.binelik.extras.Share
import app.binelik.i18n.t

/** «Αναφορά προβλήματος»: write what happened; copy or share the text (with the version, the phone and the last errors). */
@Composable
fun ReportScreen(vm: ChatViewModel, onBack: () -> Unit) {
    val context = LocalContext.current
    val clip = LocalClipboardManager.current
    var what by remember { mutableStateOf("") }
    var copied by remember { mutableStateOf(false) }
    var noMail by remember { mutableStateOf(false) }
    val report = Report.text(context, vm.settings, what)
    Screen(t("Αναφορά προβλήματος", "Report a problem"), onBack) {
        Panel {
            Text(t("Γράψε τι έκανες και τι έγινε. Πηγαίνει στο ${Report.EMAIL}. Η εφαρμογή προσθέτει την έκδοση, το κινητό και τα τελευταία σφάλματα, ποτέ τα κλειδιά ή τις κουβέντες σου.",
                "Write what you did and what happened. It goes to ${Report.EMAIL}. The app adds the version, the phone and the last errors, never your keys or conversations."), color = C.muted, fontSize = 13.5.sp)
            OutlinedTextField(what, { what = it.take(2000); copied = false }, Modifier.fillMaxWidth().padding(top = 10.dp).heightIn(min = 120.dp),
                label = { Text(t("Τι έγινε;", "What happened?")) }, placeholder = { Text(t("Π.χ. πάτησα OK στο θυμόμετρο και έκλεισε", "E.g. I pressed OK on the anger meter and it closed")) })
            PrimaryButton(t("✉️ Αποστολή με email", "✉️ Send by email"), { if (!Report.email(context, report)) noMail = true }, Modifier.fillMaxWidth().padding(top = 10.dp))
            if (noMail) Text(t("Δεν βρέθηκε εφαρμογή email. Πάτα Αντιγραφή και στείλ' το στο ${Report.EMAIL}.", "No email app found. Press Copy and send it to ${Report.EMAIL}."), color = C.danger, fontSize = 12.5.sp, modifier = Modifier.padding(top = 6.dp))
            Row(Modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton({ clip.setText(AnnotatedString(report)); copied = true }, Modifier.weight(1f)) { Text(if (copied) t("Αντιγράφηκε ✓", "Copied ✓") else t("Αντιγραφή", "Copy"), color = C.text) }
                OutlinedButton({ Share.shareText(context, report) }, Modifier.weight(1f)) { Text(t("Κοινοποίηση", "Share"), color = C.text) }
            }
            TextButton({ openUrl(context, Report.issueUrl(report)) }) { Text(t("Ή άνοιξε θέμα στο GitHub ↗", "Or open an issue on GitHub ↗"), color = C.amber) }
        }
        Panel {
            Hud(t("ΤΙ ΘΑ ΣΤΑΛΕΙ", "WHAT WILL BE SENT"), Modifier.padding(bottom = 8.dp))
            Text(report, fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = C.dim, lineHeight = 15.sp)
        }
    }
}
