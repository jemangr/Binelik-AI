package app.binelik.ui

import app.binelik.i18n.t
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.binelik.data.Character
import app.binelik.data.Characters
import app.binelik.data.Gender
import app.binelik.data.Intensity
import app.binelik.data.Message

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(vm: ChatViewModel, onSettings: () -> Unit, onCustom: () -> Unit, onLive: (() -> Unit)? = null) {
    val c by vm.character.collectAsState()
    val intensity by vm.intensity.collectAsState()
    val messages by vm.messages.collectAsState()
    val sending by vm.sending.collectAsState()
    val error by vm.error.collectAsState()
    val pause by vm.pause.collectAsState()
    var sheet by remember { mutableStateOf(false) }
    var input by remember { mutableStateOf("") }
    val accent = Color(c.color)
    val list = rememberLazyListState()
    LaunchedEffect(messages.size, sending) { if (messages.isNotEmpty()) list.animateScrollToItem(messages.size - 1 + if (sending) 1 else 0) }

    Column(Modifier.fillMaxSize().background(C.bg).statusBarsPadding().navigationBarsPadding().imePadding()) {
        Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 6.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Logo(28.dp); Spacer(Modifier.width(8.dp))
            Text("Binelik AI", fontWeight = FontWeight.ExtraBold, fontSize = 17.sp)
            Spacer(Modifier.width(8.dp))
            Text(
                vm.settings.provider.label.uppercase(), color = accent, fontFamily = FontFamily.Monospace, fontSize = 10.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.border(1.dp, accent.copy(alpha = 0.5f), RoundedCornerShape(6.dp)).padding(horizontal = 5.dp, vertical = 2.dp),
            )
            Spacer(Modifier.weight(1f))
            if (onLive != null) IconButton(onLive) { Icon(Icons.Default.GraphicEq, t("Live με φωνή", "Live voice"), tint = accent) }
            IconButton(onSettings) { Icon(Icons.Default.Settings, t("Ρυθμίσεις", "Settings"), tint = C.muted) }
        }
        UpdateBanner(vm)
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Orb(accent, if (messages.isEmpty()) 190.dp else 96.dp, active = sending || messages.isEmpty())
            Row(
                Modifier.padding(top = 6.dp).clip(RoundedCornerShape(50)).background(C.panel)
                    .border(1.dp, accent.copy(alpha = 0.45f), RoundedCornerShape(50))
                    .clickable(role = Role.Button) { sheet = true }.padding(horizontal = 14.dp, vertical = 8.dp)
                    .semantics { contentDescription = t("Χαρακτήρας: ${c.name}. Αλλαγή χαρακτήρα", "Character: ${c.name}. Change character") },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Dot(accent); Spacer(Modifier.width(8.dp))
                Text(c.name, color = accent, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp)
                Spacer(Modifier.width(8.dp)); Text(t("Αλλαγή ▾", "Change ▾"), color = C.muted, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            }
            Segmented(
                Intensity.entries, intensity, { it.label }, accent, vm::setIntensity,
                Modifier.padding(horizontal = 48.dp, vertical = 10.dp),
            )
        }
        LazyColumn(Modifier.weight(1f).fillMaxWidth(), state = list, contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 14.dp, vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (messages.isEmpty()) item {
                Text(
                    if (c.sample.isNotEmpty()) "«${c.sample}»" else c.tagline, color = C.muted, fontStyle = FontStyle.Italic,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
            }
            items(messages, key = { it.id }) { Bubble(it, accent) }
            if (sending) item { Text(t("${c.name} γράφει…", "${c.name} is typing…"), color = C.dim, fontSize = 12.sp, modifier = Modifier.padding(start = 6.dp)) }
        }
        error?.let { Text(it, color = C.danger, fontSize = 13.sp, modifier = Modifier.padding(horizontal = 18.dp, vertical = 4.dp)) }
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            TextField(
                input, { input = it }, Modifier.weight(1f), placeholder = { Text(t("Γράψ' τα όλα…", "Let it all out…")) }, maxLines = 4,
                shape = RoundedCornerShape(24.dp),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = C.panel, unfocusedContainerColor = C.panel,
                    focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent, cursorColor = accent,
                ),
            )
            Spacer(Modifier.width(8.dp))
            Box(
                Modifier.size(48.dp).clip(CircleShape).background(if (input.isBlank() || sending) C.panel2 else accent)
                    .clickable(enabled = input.isNotBlank() && !sending, role = Role.Button) { vm.send(input); input = "" },
                contentAlignment = Alignment.Center,
            ) {
                if (sending) CircularProgressIndicator(Modifier.size(20.dp), color = accent, strokeWidth = 2.dp)
                else Icon(Icons.AutoMirrored.Filled.Send, t("Αποστολή", "Send"), tint = if (input.isBlank()) C.dim else Color.Black)
            }
        }
    }

    if (sheet) ModalBottomSheet({ sheet = false }, containerColor = Color(0xFF0A1018)) {
        Text(t("Με ποιον θα τα πεις;", "Who do you want to talk to?"), fontWeight = FontWeight.ExtraBold, fontSize = 17.sp, modifier = Modifier.padding(horizontal = 18.dp, vertical = 4.dp))
        CharacterList(vm, Modifier.heightIn(max = 560.dp), onCustom = { sheet = false; onCustom() }) { vm.pick(it); sheet = false }
    }
    if (pause) SafetyDialog { vm.pause.value = false }
}

@Composable
private fun Bubble(m: Message, accent: Color) {
    val me = m.fromUser
    Box(Modifier.fillMaxWidth(), contentAlignment = if (me) Alignment.CenterEnd else Alignment.CenterStart) {
        Text(
            m.text, fontSize = 14.5.sp, lineHeight = 20.sp,
            color = if (m.pause) C.text else C.text,
            modifier = Modifier.widthIn(max = 300.dp)
                .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp, bottomStart = if (me) 16.dp else 4.dp, bottomEnd = if (me) 4.dp else 16.dp))
                .background(if (me) Color(0xFF1D2B3D) else if (m.pause) C.panel2 else accent.copy(alpha = 0.14f))
                .border(1.dp, if (me) Color.Transparent else if (m.pause) C.warn else accent.copy(alpha = 0.4f),
                    RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp, bottomStart = if (me) 16.dp else 4.dp, bottomEnd = if (me) 4.dp else 16.dp))
                .padding(horizontal = 12.dp, vertical = 9.dp),
        )
    }
}

/** The list of characters (bottom sheet and first-run step): colour, name, what it does, voice. */
@Composable
fun CharacterList(vm: ChatViewModel, modifier: Modifier = Modifier, onCustom: (() -> Unit)? = null, onPick: (Character) -> Unit) {
    val current by vm.character.collectAsState()
    val custom = vm.settings.custom()
    LazyColumn(modifier.fillMaxWidth(), contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 4.dp)) {
        items(Characters.all + listOfNotNull(custom), key = { it.id }) { c -> CharacterRow(c, vm.settings.gender(c), c.id == current.id) { onPick(c) } }
        if (onCustom != null) item {
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).clickable(role = Role.Button) { onCustom() }.padding(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(34.dp).clip(CircleShape).border(2.dp, C.dim, CircleShape), contentAlignment = Alignment.Center) { Text("+", color = C.muted, fontSize = 18.sp) }
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(if (custom == null) t("Φτιάξε τον δικό σου", "Make your own") else t("Άλλαξε τον δικό σου", "Change your own"), fontWeight = FontWeight.Bold, fontSize = 14.5.sp)
                    Text(t("Περιγραφή, φωνή, χρώμα", "Description, voice, colour"), color = C.muted, fontSize = 12.sp)
                }
            }
        }
        item { Spacer(Modifier.size(16.dp)) }
    }
}

@Composable
private fun CharacterRow(c: Character, gender: Gender, current: Boolean, onClick: () -> Unit) {
    val k = Color(c.color)
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
            .background(if (current) k.copy(alpha = 0.14f) else Color.Transparent)
            .border(1.dp, if (current) k.copy(alpha = 0.55f) else Color.Transparent, RoundedCornerShape(14.dp))
            .clickable(role = Role.Button, onClick = onClick).padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(34.dp).clip(CircleShape).background(androidx.compose.ui.graphics.Brush.radialGradient(listOf(Color.White.copy(alpha = 0.7f), k, k.copy(red = k.red * 0.35f, green = k.green * 0.35f, blue = k.blue * 0.35f)))))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(c.name, fontWeight = FontWeight.Bold, fontSize = 14.5.sp)
            Text(c.tagline, color = C.muted, fontSize = 12.sp, maxLines = 2)
        }
        Text(if (gender == Gender.MALE) t("Ανδρική", "Male") else t("Γυναικεία", "Female"), color = k, fontFamily = FontFamily.Monospace, fontSize = 10.sp)
    }
}

@Composable
fun SafetyDialog(onClose: () -> Unit) {
    val context = LocalContext.current
    AlertDialog(
        onDismissRequest = {}, containerColor = C.panel,
        title = { Text(t("Σταματάμε λίγο το παιχνίδι", "Let's stop the game for a moment"), fontWeight = FontWeight.ExtraBold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(t("Αυτό που είπες ακούγεται σοβαρό, και δεν είναι για πλάκα. Δεν είσαι μόνος σου. Μίλα τώρα με κάποιον που μπορεί να βοηθήσει:", "What you said sounds serious, and it isn't a joke. You're not alone. Talk now to someone who can help:"), color = C.muted, lineHeight = 20.sp)
                CallRow(t("24ωρη γραμμή για την αυτοκτονία", "Suicide line, 24 hours (Greece)"), "1018") { dial(context, "1018") }
                CallRow(t("Έκτακτη ανάγκη", "Emergency"), "112") { dial(context, "112") }
            }
        },
        confirmButton = { PrimaryButton(t("Θέλω να μιλήσω σε κάποιον", "I want to talk to someone"), { dial(context, "1018") }) },
        dismissButton = { SecondaryButton(t("Είμαι καλά, συνέχεια", "I'm OK, go on"), onClose) },
    )
}

@Composable
private fun CallRow(label: String, number: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(C.panel2).border(1.dp, C.line, RoundedCornerShape(14.dp))
            .clickable(role = Role.Button, onClick = onClick).padding(horizontal = 14.dp, vertical = 12.dp)
            .semantics { contentDescription = t("$label, κλήση $number", "$label, call $number") },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
        Text(number, color = C.ok, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 17.sp)
    }
}
