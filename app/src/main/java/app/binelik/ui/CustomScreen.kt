package app.binelik.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.binelik.data.Characters
import app.binelik.data.Gender
import app.binelik.extras.Share
import app.binelik.extras.ShareCharacter
import app.binelik.i18n.t

/** One of the user's own characters (slot 1 or 2): name, description, voice, colour; share it as a QR or a link, or import one. */
@Composable
fun CustomScreen(vm: ChatViewModel, slot: Int, onBack: () -> Unit, imported: ShareCharacter.Shared? = null) {
    val s = vm.settings
    val context = LocalContext.current
    val clip = LocalClipboardManager.current
    val id = Characters.customId(slot)
    var name by remember { mutableStateOf(imported?.n ?: s.customName(slot)) }
    var desc by remember { mutableStateOf(imported?.d ?: s.customDescription(slot)) }
    var color by remember { mutableStateOf(imported?.c ?: s.customColor(slot)) }
    var gender by remember { mutableStateOf(imported?.let { ShareCharacter.gender(it) } ?: s.genderOf(id, Gender.MALE)) }
    var qr by remember { mutableStateOf<String?>(null) }
    var note by remember { mutableStateOf<String?>(null) }
    var delete by remember { mutableStateOf(false) }
    val accent = Color(color)
    Screen(t("Custom $slot", "Custom $slot"), onBack) {
        if (imported != null) Text(t("Ένας φίλος σου έστειλε αυτόν τον χαρακτήρα. Δες τον και πάτα Αποθήκευση.", "A friend sent you this character. Check it and press Save."), color = C.amber, fontSize = 13.sp)
        OutlinedTextField(name, { name = it.take(40) }, Modifier.fillMaxWidth(), singleLine = true,
            label = { Text(t("Όνομα, π.χ. Παναγιώτης ή Πεθερός", "Name, e.g. Panagiotis or Father-in-law")) },
            supportingText = { Text(t("Στη λίστα: «Custom: ${name.ifBlank { "…" }}»", "In the list: “Custom: ${name.ifBlank { "…" }}”")) })
        OutlinedTextField(
            desc, { desc = it.take(600) }, Modifier.fillMaxWidth().heightIn(min = 140.dp),
            label = { Text(t("Πώς μιλάει και τι κάνει", "How it talks and what it does")) },
            supportingText = { Text(t("${desc.length}/600 · π.χ. «Τα ξέρει όλα, έχει άποψη για όλα, κάθε κουβέντα τη γυρίζει στο ποδόσφαιρο».", "${desc.length}/600 · e.g. “Knows it all, has an opinion on everything, turns every conversation to football”.")) },
        )
        Panel {
            Hud(t("ΦΩΝΗ", "VOICE"), Modifier.padding(bottom = 8.dp))
            Segmented(listOf(Gender.MALE, Gender.FEMALE), gender, { if (it == Gender.MALE) t("Ανδρική", "Male") else t("Γυναικεία", "Female") }, accent, { gender = it })
            Spacer(Modifier.height(12.dp))
            Hud(t("ΧΡΩΜΑ", "COLOUR"), Modifier.padding(bottom = 8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                swatches.forEach { sw ->
                    Box(
                        Modifier.size(32.dp).clip(CircleShape).background(Color(sw))
                            .border(2.dp, if (sw == color) Color.White else Color.Transparent, CircleShape)
                            .clickable(role = Role.RadioButton) { color = sw }
                            .semantics { selected = sw == color; contentDescription = t("Χρώμα", "Colour") },
                    )
                }
            }
        }
        Text(t("Ισχύουν οι ίδιοι κανόνες ασφάλειας με όλους τους χαρακτήρες: χωρίς σεξουαλικό περιεχόμενο, χωρίς μίσος, χωρίς ενθάρρυνση βίας.", "The same safety rules apply as for every character: no sexual content, no hate, no encouragement of violence."), color = C.dim, fontSize = 12.sp)
        PrimaryButton(t("Αποθήκευση", "Save"), {
            s.setCustom(slot, name, desc, color); s.setGender(id, gender)
            s.custom(slot)?.let { vm.pick(it) }; onBack()
        }, Modifier.fillMaxWidth(), enabled = desc.isNotBlank())
        Panel {
            Hud(t("ΔΩΣ' ΤΟΝ ΣΕ ΦΙΛΟ", "GIVE IT TO A FRIEND"), Modifier.padding(bottom = 8.dp))
            Text(t("Ο φίλος σκανάρει το QR με την κάμερα (ή του στέλνεις το link) και τον βάζει στο δικό του Binelik AI. Δεν περνάει από κανέναν server.",
                "Your friend scans the QR with the camera (or you send the link) and adds it to their own Binelik AI. It goes through no server."), color = C.muted, fontSize = 12.5.sp)
            Row(Modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton({ qr = ShareCharacter.link(name, desc, color, gender) }, Modifier.weight(1f), enabled = desc.isNotBlank()) { Text("QR", color = C.text) }
                OutlinedButton({
                    Share.shareText(context, t("Ο χαρακτήρας μου «$name» για το Binelik AI. Αντέγραψε όλο το μήνυμα και πάτα «Επικόλληση» στο Custom: ", "My character “$name” for Binelik AI. Copy this whole message and press “Paste” in Custom: ") + ShareCharacter.link(name, desc, color, gender))
                }, Modifier.weight(1f), enabled = desc.isNotBlank()) { Text(t("Link", "Link"), color = C.text) }
            }
            TextButton({
                val got = ShareCharacter.read(clip.getText()?.text)
                if (got == null) note = t("Δεν βρήκα χαρακτήρα στο πρόχειρο. Αντέγραψε όλο το μήνυμα με το link.", "No character in the clipboard. Copy the whole message with the link.")
                else { name = got.n; desc = got.d; color = got.c; gender = ShareCharacter.gender(got); note = t("Μπήκε. Πάτα Αποθήκευση.", "Done. Press Save.") }
            }) { Text(t("📋 Επικόλληση χαρακτήρα από φίλο", "📋 Paste a character from a friend"), color = accent) }
            note?.let { Text(it, color = C.muted, fontSize = 12.5.sp) }
        }
        if (s.custom(slot) != null) TextButton({ delete = true }) { Text(t("Σβήσιμο του Custom $slot", "Delete Custom $slot"), color = C.danger) }
    }
    qr?.let { link ->
        val bmp = remember(link) { ShareCharacter.qr(link) }
        AlertDialog(
            onDismissRequest = { qr = null }, containerColor = C.panel,
            title = { Text("Custom: $name", fontWeight = FontWeight.ExtraBold) },
            text = {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Image(bmp.asImageBitmap(), t("QR του χαρακτήρα", "The character's QR"), Modifier.size(260.dp).clip(androidx.compose.foundation.shape.RoundedCornerShape(12.dp)))
                    Text(t("Σκανάρισμα με την κάμερα του άλλου κινητού.", "Scan it with the other phone's camera."), color = C.muted, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
                }
            },
            confirmButton = { TextButton({ qr = null }) { Text("OK", color = accent) } },
        )
    }
    if (delete) AlertDialog(
        onDismissRequest = { delete = false }, containerColor = C.panel,
        title = { Text(t("Να σβηστεί ο Custom: $name;", "Delete Custom: $name?")) },
        confirmButton = { TextButton({ s.deleteCustom(slot); if (s.characterId == id) vm.pick(Characters.all.first()); delete = false; onBack() }) { Text(t("Σβήσιμο", "Delete"), color = C.danger) } },
        dismissButton = { TextButton({ delete = false }) { Text(t("Άκυρο", "Cancel"), color = C.muted) } },
    )
}
