package app.binelik

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import app.binelik.ui.BinelikTheme
import app.binelik.ui.ChatScreen
import app.binelik.ui.ChatViewModel
import app.binelik.ui.CustomScreen
import app.binelik.ui.KeyScreen
import app.binelik.ui.Onboarding
import app.binelik.ui.PrivacyScreen
import app.binelik.ui.SettingsScreen

class MainActivity : ComponentActivity() {
    private val vm: ChatViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(SystemBarStyle.dark(android.graphics.Color.TRANSPARENT), SystemBarStyle.dark(android.graphics.Color.TRANSPARENT))
        setContent {
            BinelikTheme {
                val ready = vm.settings.adult && vm.keys.has(vm.settings.provider)
                var screen by rememberSaveable { mutableStateOf(if (ready) "chat" else "start") }
                if (screen != "chat" && screen != "start") BackHandler { screen = if (screen == "custom" || screen == "key" || screen == "privacy") "settings" else "chat" }
                when (screen) {
                    "start" -> Onboarding(vm) { screen = "chat" }
                    "settings" -> SettingsScreen(vm, { screen = "chat" }, { screen = "key" }, { screen = "custom" }, { screen = "privacy" })
                    "key" -> KeyScreen(vm) { screen = "settings" }
                    "custom" -> CustomScreen(vm) { vm.refresh(); screen = "chat" }
                    "privacy" -> PrivacyScreen { screen = "settings" }
                    else -> ChatScreen(vm, { screen = "settings" }, { screen = "custom" })
                }
            }
        }
    }
}
