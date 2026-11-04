package app.binelik.car

import android.content.Intent
import androidx.car.app.CarAppService
import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.Session
import androidx.car.app.model.Action
import androidx.car.app.model.ActionStrip
import androidx.car.app.model.CarColor
import androidx.car.app.model.ItemList
import androidx.car.app.model.ListTemplate
import androidx.car.app.model.Pane
import androidx.car.app.model.PaneTemplate
import androidx.car.app.model.Row
import androidx.car.app.model.Template
import androidx.car.app.validation.HostValidator
import androidx.lifecycle.lifecycleScope
import app.binelik.data.Characters
import app.binelik.i18n.t
import app.binelik.voice.Engine
import app.binelik.voice.LiveState
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Binelik AI in Android Auto. Works only with «unknown sources» on in Android Auto's developer
 * settings (Google allows only some kinds of apps in the car), so any host is accepted.
 */
class CarService : CarAppService() {
    override fun createHostValidator(): HostValidator = HostValidator.ALLOW_ALL_HOSTS_VALIDATOR

    override fun onCreateSession(): Session = object : Session() {
        override fun onCreateScreen(intent: Intent): Screen {
            Engine.init(application)
            // In the car, the taxi driver by default (Settings → Android Auto).
            if (Engine.settings.carTaxi && !Engine.on && Engine.settings.characterId != "taxitzis") {
                Engine.settings.characterId = "taxitzis"; Engine.changed()
            }
            return LiveCarScreen(carContext)
        }
    }
}

private fun color(c: Long) = CarColor.createCustom(c.toInt(), c.toInt())

/** The conversation: who, what state, the last line, and Play/Pause with the two Mutes. */
class LiveCarScreen(ctx: CarContext) : Screen(ctx) {
    init {
        lifecycleScope.launch { Engine.version.collectLatest { invalidate() } }
        lifecycleScope.launch { Engine.state.collectLatest { invalidate() } }
    }

    override fun onGetTemplate(): Template {
        val c = Engine.character()
        val on = Engine.on
        val status = when (Engine.state.value) {
            LiveState.OFF -> t("Σε παύση · πάτα Play", "Paused · press Play")
            LiveState.CONNECTING -> t("Σύνδεση…", "Connecting…")
            LiveState.LISTENING -> if (Engine.micMuted.value) t("Δεν σε ακούει (μικρόφωνο κλειστό)", "Not listening (mic off)") else t("Σε ακούει", "Listening")
            LiveState.SPEAKING -> if (Engine.voiceMuted.value) t("Μιλάει (σε σίγαση)", "Speaking (muted)") else t("Μιλάει", "Speaking")
            LiveState.ERROR -> Engine.error.value ?: t("Σταμάτησε", "Stopped")
        }
        val line = Engine.saidLine.value.ifEmpty { Engine.heardLine.value }.ifEmpty { c.sample.ifEmpty { c.tagline } }
        val pane = Pane.Builder()
            .addRow(Row.Builder().setTitle(c.name).addText(status).build())
            .addRow(Row.Builder().setTitle(t("Τελευταίο", "Last")).addText(line.take(120)).build())
            .addAction(
                Action.Builder().setTitle(if (on) t("Παύση", "Pause") else "Play").setBackgroundColor(color(c.color))
                    .setFlags(Action.FLAG_PRIMARY).setOnClickListener { if (Engine.on) Engine.stop() else Engine.play(); invalidate() }.build(),
            )
            .addAction(
                Action.Builder().setTitle(if (Engine.micMuted.value) t("Μικρόφωνο: κλειστό", "Mic: off") else t("Μικρόφωνο", "Mic"))
                    .setOnClickListener { Engine.toggleMic(); invalidate() }.build(),
            )
            .build()
        val strip = ActionStrip.Builder()
            .addAction(Action.Builder().setTitle(if (Engine.voiceMuted.value) t("Φωνή: σίγαση", "Voice: muted") else t("Φωνή", "Voice")).setOnClickListener { Engine.toggleVoice(); invalidate() }.build())
            .addAction(Action.Builder().setTitle(t("Χαρακτήρες", "Characters")).setOnClickListener { screenManager.push(CharactersCarScreen(carContext)) }.build())
            .build()
        return PaneTemplate.Builder(pane).setTitle("Binelik AI").setHeaderAction(Action.APP_ICON).setActionStrip(strip).build()
    }
}

/** The list of characters; picking one goes back, and a running conversation continues with it. */
class CharactersCarScreen(ctx: CarContext) : Screen(ctx) {
    override fun onGetTemplate(): Template {
        val list = ItemList.Builder()
        (Characters.all + listOfNotNull(Engine.settings.custom())).take(6 * 4).forEach { c ->
            list.addItem(
                Row.Builder().setTitle(c.name).addText(c.tagline).setOnClickListener {
                    Engine.settings.characterId = c.id
                    Engine.changed()
                    screenManager.pop()
                }.build(),
            )
        }
        return ListTemplate.Builder().setTitle(t("Με ποιον θα τα πεις;", "Who do you want to talk to?")).setHeaderAction(Action.BACK).setSingleList(list.build()).build()
    }
}
