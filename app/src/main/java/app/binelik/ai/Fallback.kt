package app.binelik.ai

import app.binelik.data.KeyVault
import app.binelik.data.Settings
import app.binelik.i18n.t

/**
 * 1.3: when the chosen AI can't answer (the free limit is over, it's overloaded, the model is gone),
 * the reply comes from another AI the user has a key for, and the app says so in one line.
 * Never with a wrong key (that is the user's to fix) and never without the user's own keys.
 */
object Fallback {
    /** The order the others are tried in: the free and fast ones first. */
    private val ORDER = listOf(Provider.GEMINI, Provider.GROQ, Provider.OPENROUTER, Provider.DEEPSEEK)

    fun order(primary: Provider, has: (Provider) -> Boolean): List<Provider> =
        listOf(primary) + ORDER.filter { it != primary && has(it) }

    /** Only these failures are worth another AI. */
    fun worthSwitching(r: Reason) = r == Reason.LIMIT || r == Reason.BUSY || r == Reason.MODEL

    /** [result] from [provider]; [from]: the chosen AI that couldn't answer, if another one did. */
    data class Answer(val result: AiResult, val provider: Provider, val from: Provider? = null)

    suspend fun reply(settings: Settings, keys: KeyVault, system: String, turns: List<Turn>, search: Boolean = true): Answer {
        val primary = settings.provider
        val list = if (settings.fallback) order(primary, keys::has) else listOf(primary)
        var first: Answer? = null
        for (p in list) {
            val key = keys.get(p) ?: continue
            settings.used(p, text = 1)
            val r = Ai.reply(p, key, system, turns, search)
            val a = Answer(r, p, if (p != primary) primary else null)
            if (r is AiResult.Ok) return a
            if (first == null) first = a
            if (r !is AiResult.Fail || !worthSwitching(r.reason)) break
        }
        return first ?: Answer(AiResult.Fail(Reason.BAD_KEY), primary)
    }

    /** The line shown when another AI answered. */
    fun notice(a: Answer): String? = a.from?.let { from ->
        t("Το ${from.label} δεν μπορούσε να απαντήσει (όριο ή φόρτος), οπότε απάντησε το ${a.provider.label}.",
            "${from.label} couldn't answer (limit or load), so ${a.provider.label} answered.")
    }
}
