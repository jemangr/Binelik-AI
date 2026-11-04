package app.binelik.ui

import app.binelik.i18n.t
import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import app.binelik.data.Intensity
import app.binelik.voice.LiveState

/** The Live home: the voice circle, Pause/Play in the middle, the two Mutes beside it. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LiveScreen(vm: ChatViewModel, onChat: () -> Unit, onSettings: () -> Unit, onCustom: () -> Unit, onFun: () -> Unit = {}, autoPlay: Boolean = false) {
    val context = LocalContext.current
    val c by vm.character.collectAsState()
    val intensity by vm.intensity.collectAsState()
    val state by vm.live.collectAsState()
    val mic by vm.micMuted.collectAsState()
    val voice by vm.voiceMuted.collectAsState()
    val err by vm.liveError.collectAsState()
    val heard by vm.heardLine.collectAsState()
    val said by vm.saidLine.collectAsState()
    val pause by vm.pause.collectAsState()
    var sheet by remember { mutableStateOf(false) }
    val accent = Color(c.color)
    val on = state == LiveState.CONNECTING || state == LiveState.LISTENING || state == LiveState.SPEAKING

    val askMic = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        if (ok) vm.play() else { vm.liveError.value = t("Το Live θέλει το μικρόφωνο. Δώσ' του άδεια, ή γράψε στην κουβέντα με κείμενο.", "Live needs the microphone. Allow it, or type in the text chat."); vm.live.value = LiveState.ERROR }
    }
    // Android 13+: the notification with the Pause button while the conversation runs.
    val askNotify = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    // The screen stays on while talking (Settings).
    val view = androidx.compose.ui.platform.LocalView.current
    androidx.compose.runtime.DisposableEffect(on) {
        view.keepScreenOn = on && vm.settings.keepScreenOn
        onDispose { view.keepScreenOn = false }
    }
    // The anger meter: before Play and after Pause (Settings can turn it off).
    var anger by remember { mutableStateOf<Boolean?>(null) }
    fun start() {
        when {
            !vm.canVoice() -> { vm.liveError.value = t("Δεν υπάρχει κλειδί για το AI. Βάλ' το από τις Ρυθμίσεις.", "There is no AI key. Add it in the Settings."); vm.live.value = LiveState.ERROR }
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED -> {
                vm.play()
                if (android.os.Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) askNotify.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
            else -> askMic.launch(Manifest.permission.RECORD_AUDIO)
        }
    }
    fun playPause() {
        if (on) { vm.stopLive(); if (vm.settings.angerMeter && vm.angerOpen) anger = false }
        else if (vm.settings.angerMeter && !vm.angerOpen) anger = true
        else start()
    }
    // From the widget or the quick settings tile: straight into Live.
    androidx.compose.runtime.LaunchedEffect(autoPlay) { if (autoPlay && !on) start() }

    val hud = when (state) {
        LiveState.OFF -> t("ΣΕ ΠΑΥΣΗ · ΠΑΤΑ PLAY", "PAUSED · PRESS PLAY")
        LiveState.CONNECTING -> t("ΣΥΝΔΕΣΗ…", "CONNECTING…")
        LiveState.LISTENING -> if (mic) t("ΔΕΝ ΣΕ ΑΚΟΥΕΙ", "NOT LISTENING") else t("ΣΕ ΑΚΟΥΕΙ", "LISTENING")
        LiveState.SPEAKING -> if (voice) t("ΜΙΛΑΕΙ (ΣΕ ΣΙΓΑΣΗ)", "SPEAKING (MUTED)") else t("ΜΙΛΑΕΙ", "SPEAKING")
        LiveState.ERROR -> t("ΣΤΑΜΑΤΗΣΕ", "STOPPED")
    }

    Column(Modifier.fillMaxSize().background(C.bg).statusBarsPadding().navigationBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 6.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Logo(28.dp); Spacer(Modifier.width(8.dp))
            Text("Binelik AI", fontWeight = FontWeight.ExtraBold, fontSize = 17.sp)
            Spacer(Modifier.width(8.dp))
            Text(
                if (on) "LIVE" else t("ΠΑΥΣΗ", "PAUSED"), color = accent, fontFamily = FontFamily.Monospace, fontSize = 10.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.border(1.dp, accent.copy(alpha = 0.5f), RoundedCornerShape(6.dp)).padding(horizontal = 5.dp, vertical = 2.dp),
            )
            Spacer(Modifier.weight(1f))
            IconButton(onFun) { Text("🎲", fontSize = 20.sp) }
            IconButton(onChat) { Icon(Icons.AutoMirrored.Filled.Chat, t("Κουβέντα με κείμενο", "Text chat"), tint = C.muted) }
            IconButton(onSettings) { Icon(Icons.Default.Settings, t("Ρυθμίσεις", "Settings"), tint = C.muted) }
        }
        UpdateBanner(vm)
        androidx.compose.runtime.LaunchedEffect(Unit) { vm.loadBalance() }
        val bal by vm.balance.collectAsState()
        bal?.let { Hud(t("ΥΠΟΛΟΙΠΟ: $it", "BALANCE: $it"), Modifier.align(Alignment.CenterHorizontally)) }
        Hud((if (vm.settings.provider == app.binelik.ai.Provider.GEMINI) "GEMINI LIVE · " else "DEEPSEEK · ") + hud, Modifier.align(Alignment.CenterHorizontally).padding(top = 4.dp).semantics { liveRegion = LiveRegionMode.Polite })
        Box(Modifier.fillMaxWidth().padding(top = 6.dp), contentAlignment = Alignment.Center) {
            // Tap the circle while it speaks to stop it and talk (needed with the phone's voice).
            Box(Modifier.clip(CircleShape).clickable(enabled = state == LiveState.SPEAKING, role = Role.Button) { app.binelik.voice.Engine.interrupt() }
                .semantics { contentDescription = t("Διακοπή του χαρακτήρα", "Interrupt the character") }) {
                Orb(accent, 250.dp, active = on && !(state == LiveState.LISTENING && mic))
            }
        }
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Row(
                Modifier.padding(top = 4.dp).clip(RoundedCornerShape(50)).background(C.panel)
                    .border(1.dp, accent.copy(alpha = 0.45f), RoundedCornerShape(50))
                    .clickable(role = Role.Button) { sheet = true }.padding(horizontal = 14.dp, vertical = 8.dp)
                    .semantics { contentDescription = t("Χαρακτήρας: ${c.name}. Αλλαγή χαρακτήρα", "Character: ${c.name}. Change character") },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Dot(accent); Spacer(Modifier.width(8.dp))
                Text(c.name, color = accent, fontWeight = FontWeight.ExtraBold, fontSize = 19.sp)
                Spacer(Modifier.width(8.dp)); Text(t("Αλλαγή ▾", "Change ▾"), color = C.muted, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            }
            Text(
                when {
                    said.isNotEmpty() -> said
                    heard.isNotEmpty() -> "«$heard»"
                    c.sample.isNotEmpty() -> "«${c.sample}»"
                    else -> c.tagline
                },
                color = if (heard.isNotEmpty() && said.isEmpty()) C.text else C.muted, fontSize = 14.sp, lineHeight = 20.sp, textAlign = TextAlign.Center,
                maxLines = 3, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp).heightIn(min = 44.dp),
            )
            Segmented(Intensity.entries, intensity, { it.label }, accent, vm::setIntensity, Modifier.padding(horizontal = 48.dp))
            val banner = listOfNotNull(if (mic) t("Δεν σε ακούει (μικρόφωνο κλειστό)", "Not listening (microphone off)") else null, if (voice) t("Δεν τον ακούς (φωνή AI σε σίγαση)", "You can't hear it (AI voice muted)") else null).joinToString(" · ")
            (err ?: banner.ifEmpty { null })?.let {
                Text(
                    it, color = Color(0xFFFFB3B3), fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 8.dp).fillMaxWidth().clip(RoundedCornerShape(12.dp))
                        .background(C.danger.copy(alpha = 0.12f)).border(1.dp, C.danger.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                        .padding(horizontal = 12.dp, vertical = 8.dp).semantics { liveRegion = LiveRegionMode.Polite },
                )
            }
        }
        Spacer(Modifier.weight(1f))
        Row(Modifier.fillMaxWidth().padding(bottom = 26.dp), horizontalArrangement = Arrangement.spacedBy(26.dp, Alignment.CenterHorizontally), verticalAlignment = Alignment.CenterVertically) {
            MuteButton(mic, if (mic) Icons.Default.MicOff else Icons.Default.Mic, if (mic) t("Κλειστό", "Off") else t("Μικρόφωνο", "Microphone"), t("Μικρόφωνο", "Microphone"), vm::toggleMic)
            Box(
                Modifier.size(88.dp).clip(CircleShape).background(if (on) Color.White else C.panel2)
                    .border(6.dp, accent.copy(alpha = 0.25f), CircleShape)
                    .clickable(role = Role.Button) { playPause() }
                    .semantics { contentDescription = if (on) t("Παύση", "Pause") else t("Έναρξη Live", "Start Live") },
                contentAlignment = Alignment.Center,
            ) { Icon(if (on) Icons.Default.Pause else Icons.Default.PlayArrow, null, tint = if (on) Color.Black else accent, modifier = Modifier.size(40.dp)) }
            MuteButton(voice, if (voice) Icons.AutoMirrored.Filled.VolumeOff else Icons.AutoMirrored.Filled.VolumeUp, if (voice) t("Σε σίγαση", "Muted") else t("Φωνή AI", "AI voice"), t("Φωνή AI", "AI voice"), vm::toggleVoice)
        }
    }

    if (sheet) ModalBottomSheet({ sheet = false }, containerColor = Color(0xFF0A1018)) {
        Text(t("Με ποιον θα τα πεις;", "Who do you want to talk to?"), fontWeight = FontWeight.ExtraBold, fontSize = 17.sp, modifier = Modifier.padding(horizontal = 18.dp, vertical = 4.dp))
        CharacterList(vm, Modifier.heightIn(max = 560.dp), onCustom = { sheet = false; onCustom() }) { vm.pick(it); sheet = false }
    }
    if (pause) SafetyDialog { vm.pause.value = false }
    anger?.let { before ->
        AngerDialog(before, accent) { v ->
            anger = null
            if (before) { if (v != null) vm.angerBefore(v); start() } else if (v != null) vm.angerAfter(v)
        }
    }
}

@Composable
private fun MuteButton(muted: Boolean, icon: ImageVector, label: String, name: String, onClick: () -> Unit) {
    Column(
        Modifier.clip(RoundedCornerShape(16.dp)).clickable(role = Role.Switch, onClick = onClick).padding(4.dp)
            .semantics { contentDescription = name; stateDescription = if (muted) t("Σε σίγαση", "Muted") else t("Ανοιχτό", "On") },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier.size(56.dp).clip(CircleShape).background(if (muted) C.danger else C.panel2)
                .border(if (muted) 4.dp else 1.dp, if (muted) C.danger.copy(alpha = 0.3f) else C.line, CircleShape),
            contentAlignment = Alignment.Center,
        ) { Icon(icon, null, tint = if (muted) Color.White else C.muted) }
        Spacer(Modifier.height(6.dp))
        Text(label, color = if (muted) C.danger else C.muted, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
    }
}
