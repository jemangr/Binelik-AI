package app.binelik.extras

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager

/** The launcher icon: the logo, or the discreet «Σημειώσεις» (two aliases, one on at a time). */
object Icon {
    fun set(context: Context, discreet: Boolean) {
        val pm = context.packageManager
        fun state(alias: String, on: Boolean) = pm.setComponentEnabledSetting(
            ComponentName(context, "app.binelik.$alias"),
            if (on) PackageManager.COMPONENT_ENABLED_STATE_ENABLED else PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
            PackageManager.DONT_KILL_APP,
        )
        state("DiscreetLauncher", discreet)
        state("Launcher", !discreet)
    }
}
