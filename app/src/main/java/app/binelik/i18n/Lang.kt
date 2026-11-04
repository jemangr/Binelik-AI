package app.binelik.i18n

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * The app's language: Greek or English. The screens read it through [t], so changing it in the
 * settings redraws them at once. The characters speak the same language.
 */
object Lang {
    var en by mutableStateOf(false)

    /** BCP-47 tag for speech (Live, the phone's recogniser and voice). */
    val tag: String get() = if (en) "en-US" else "el-GR"
}

/** The Greek or the English text, by the app's language. */
fun t(el: String, en: String): String = if (Lang.en) en else el
