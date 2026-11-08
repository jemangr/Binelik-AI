package app.binelik

import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import app.binelik.extras.Crash
import app.binelik.extras.EXTRA_PLAY
import app.binelik.extras.Share
import app.binelik.i18n.t
import app.binelik.ui.BinelikTheme
import app.binelik.ui.C
import app.binelik.ui.ChatScreen
import app.binelik.ui.ChatViewModel
import app.binelik.ui.CustomScreen
import app.binelik.ui.EmailScreen
import app.binelik.ui.FightScreen
import app.binelik.ui.FunScreen
import app.binelik.ui.KeyScreen
import app.binelik.ui.LiveScreen
import app.binelik.ui.Logo
import app.binelik.ui.Onboarding
import app.binelik.ui.PrimaryButton
import app.binelik.ui.PrivacyScreen
import app.binelik.ui.ReportScreen
import app.binelik.ui.SettingsScreen
import app.binelik.ui.StatsScreen
import app.binelik.ui.ScreamScreen
import app.binelik.ui.PlatesScreen
import app.binelik.ui.BurnScreen
import app.binelik.ui.BattleScreen
import app.binelik.ui.RehearsalScreen
import app.binelik.ui.BreatheScreen
import app.binelik.ui.GroupScreen
import app.binelik.ui.PacksScreen

class MainActivity : FragmentActivity() {
    private val vm: ChatViewModel by viewModels()
    /** Locked until the fingerprint, face or screen lock (if the lock is on). */
    private var locked by mutableStateOf(false)
    private var autoPlay by mutableStateOf(false)
    private var leftAt = 0L
    /** The error the app closed on last time, if any (shown once, to copy and send). */
    private var crash by mutableStateOf<String?>(null)
    /** A friend's character from a QR or a link, waiting to be saved in a Custom slot. */
    private var imported by mutableStateOf<app.binelik.extras.ShareCharacter.Shared?>(null)
    private var slot by mutableStateOf(1)
    /** Three strong shakes: «Report a problem» opens (see [Shake]). */
    private var shaken by mutableStateOf(false)
    private val shake = app.binelik.extras.Shake { shaken = true }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(SystemBarStyle.dark(android.graphics.Color.TRANSPARENT), SystemBarStyle.dark(android.graphics.Color.TRANSPARENT))
        locked = vm.settings.lock
        autoPlay = intent?.getBooleanExtra(EXTRA_PLAY, false) == true
        pickFrom(intent)
        crash = Crash.last(this)
        imported = app.binelik.extras.ShareCharacter.read(intent?.dataString)
        setContent {
            BinelikTheme {
                crash?.let { CrashDialog(it) { Crash.clear(this); crash = null } }
                if (locked) { LockScreen(); return@BinelikTheme }
                val ready = vm.settings.adult && vm.keys.has(vm.settings.provider)
                var screen by rememberSaveable { mutableStateOf(if (!ready) "start" else if (vm.canVoice()) "live" else "chat") }
                if (autoPlay && ready && vm.canVoice()) screen = "live"
                if (imported != null && ready && screen != "custom") {
                    slot = app.binelik.data.Characters.CUSTOM_SLOTS.firstOrNull { vm.settings.custom(it) == null } ?: 2
                    screen = "custom"
                }
                val home = { if (vm.canVoice()) "live" else "chat" }
                if (shaken) {
                    shaken = false
                    if (vm.settings.shakeReport && screen !in setOf("start", "plates", "report")) screen = "report"
                }
                val back = when (screen) {
                    "key", "privacy", "report" -> "settings"
                    "fight", "email", "stats", "scream", "plates", "burn", "battle", "rehearsal", "breathe", "group", "packs" -> "fun"
                    else -> home()
                }
                if (screen != "live" && screen != "start" && !(screen == "chat" && !vm.canVoice())) BackHandler { screen = back }
                when (screen) {
                    "start" -> Onboarding(vm) { screen = home() }
                    "settings" -> SettingsScreen(vm, { screen = home() }, { screen = "key" }, { slot = it; screen = "custom" }, { screen = "privacy" }, onLock = ::setLock, onReport = { screen = "report" })
                    "key" -> KeyScreen(vm) { screen = "settings" }
                    "custom" -> CustomScreen(vm, slot, { imported = null; vm.refresh(); screen = home() }, imported)
                    "live" -> LiveScreen(vm, { screen = "chat" }, { screen = "settings" }, { slot = it; screen = "custom" }, { screen = "fun" }, autoPlay, onCalm = { screen = "calm" }).also { autoPlay = false }
                    "privacy" -> PrivacyScreen { screen = "settings" }
                    "report" -> ReportScreen(vm) { screen = "settings" }
                    "fun" -> FunScreen(vm, { screen = home() }) { screen = if (it == "home") home() else it }
                    "fight" -> FightScreen(vm) { screen = "fun" }
                    "email" -> EmailScreen(vm) { screen = "fun" }
                    "stats" -> StatsScreen(vm) { screen = "fun" }
                    "scream" -> ScreamScreen { screen = "fun" }
                    "plates" -> PlatesScreen { screen = "fun" }
                    "burn" -> BurnScreen { screen = "fun" }
                    "battle" -> BattleScreen({ screen = "fun" })
                    "rehearsal" -> RehearsalScreen({ screen = "fun" })
                    "breathe" -> BreatheScreen { screen = "fun" }
                    // From the Live screen, after a pause: back goes home.
                    "calm" -> BreatheScreen { screen = home() }
                    "group" -> GroupScreen({ screen = "fun" })
                    "packs" -> PacksScreen({ vm.refresh(); screen = "fun" })
                    else -> ChatScreen(vm, { screen = "settings" }, { slot = it; screen = "custom" }, if (vm.canVoice()) ({ screen = "live" }) else null, { screen = "fun" })
                }
            }
        }
        if (locked) unlock()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        app.binelik.extras.ShareCharacter.read(intent.dataString)?.let { imported = it }
        pickFrom(intent)
        if (intent.getBooleanExtra(EXTRA_PLAY, false)) autoPlay = true
    }

    /** A shortcut of the icon names the character to talk to. */
    private fun pickFrom(i: Intent?) {
        val id = i?.getStringExtra(app.binelik.extras.Shortcuts.EXTRA_CHARACTER) ?: return
        vm.pick(vm.settings.character(id))
    }

    override fun onStop() {
        super.onStop()
        leftAt = System.currentTimeMillis()
        // 8/11 (Giannis): after closing the app, Spotify could not play until a force stop, because a voice
        // conversation was still running (microphone and «call» audio). Now it ends when you leave the app with
        // the screen on. It goes on only with the screen off (phone in the pocket) or in the car (Android Auto).
        val screenOn = getSystemService(android.os.PowerManager::class.java)?.isInteractive ?: true
        if (!isChangingConfigurations && screenOn && !app.binelik.voice.Engine.inCar && app.binelik.voice.Engine.on) app.binelik.voice.Engine.stop()
    }

    override fun onResume() { super.onResume(); shake.start(this) }
    override fun onPause() { super.onPause(); shake.stop() }

    override fun onStart() {
        super.onStart()
        // every time the app comes to the front (at most every hour): a newer version shows as a popup
        vm.checkUpdate()
        // Locks again after half a minute away (not while a voice conversation runs in the background).
        if (vm.settings.lock && leftAt > 0 && System.currentTimeMillis() - leftAt > 30_000 && !app.binelik.voice.Engine.on) { locked = true; unlock() }
    }

    private fun prompt(onOk: () -> Unit) {
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Binelik AI")
            .setSubtitle(t("Ξεκλείδωμα", "Unlock"))
            .apply {
                if (Build.VERSION.SDK_INT >= 30) setAllowedAuthenticators(BIOMETRIC_WEAK or DEVICE_CREDENTIAL)
                else @Suppress("DEPRECATION") setDeviceCredentialAllowed(true)
            }
            .build()
        BiometricPrompt(this, ContextCompat.getMainExecutor(this), object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) = onOk()
        }).authenticate(info)
    }

    private fun unlock() = prompt { locked = false }

    /** Turning the lock on asks for the fingerprint first, so nobody locks themselves out. */
    private fun setLock(on: Boolean) {
        if (!on) { vm.settings.lock = false; return }
        prompt { vm.settings.lock = true }
    }

    @androidx.compose.runtime.Composable
    private fun LockScreen() {
        Column(
            Modifier.fillMaxSize().background(C.bg).padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
        ) {
            Logo(96.dp)
            Text("Binelik AI", fontWeight = FontWeight.ExtraBold, fontSize = 22.sp, modifier = Modifier.padding(vertical = 16.dp))
            PrimaryButton(t("Ξεκλείδωμα", "Unlock"), { unlock() })
        }
    }

    @androidx.compose.runtime.Composable
    private fun CrashDialog(report: String, onClose: () -> Unit) {
        val clip = androidx.compose.ui.platform.LocalClipboardManager.current
        androidx.compose.material3.AlertDialog(
            onDismissRequest = onClose, containerColor = C.panel,
            title = { Text(t("Η εφαρμογή έκλεισε απότομα", "The app closed suddenly"), fontWeight = FontWeight.ExtraBold) },
            text = {
                Column {
                    Text(t("Στείλε αυτό το κείμενο στον δημιουργό για να το διορθώσει. Έχει μόνο το τεχνικό σφάλμα, όχι τις κουβέντες σου.",
                        "Send this text to the developer so it can be fixed. It only has the technical error, not your conversations."), color = C.muted, fontSize = 13.sp)
                    Text(report, fontSize = 10.sp, color = C.dim, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                        modifier = Modifier.padding(top = 10.dp).heightIn(max = 260.dp).verticalScroll(rememberScrollState()))
                }
            },
            confirmButton = { androidx.compose.material3.TextButton({
                val text = app.binelik.extras.Report.text(this, vm.settings, t("Η εφαρμογή έκλεισε απότομα.", "The app closed suddenly."))
                lifecycleScope.launch {
                    if (app.binelik.extras.Report.send(text, "")) android.widget.Toast.makeText(this@MainActivity, t("Στάλθηκε. Ευχαριστώ!", "Sent. Thank you!"), android.widget.Toast.LENGTH_SHORT).show()
                    else app.binelik.extras.Report.email(this@MainActivity, text)
                }
                onClose()
            }) { Text(t("📤 Αποστολή", "📤 Send"), color = C.amber) } },
            dismissButton = {
                androidx.compose.foundation.layout.Row {
                    androidx.compose.material3.TextButton({ clip.setText(androidx.compose.ui.text.AnnotatedString(report)) }) { Text(t("Αντιγραφή", "Copy"), color = C.muted) }
                    androidx.compose.material3.TextButton(onClose) { Text(t("Κλείσιμο", "Close"), color = C.muted) }
                }
            },
        )
    }
}
