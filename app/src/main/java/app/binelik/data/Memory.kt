package app.binelik.data

import android.content.Context
import app.binelik.ai.Turn
import app.binelik.i18n.t
import app.binelik.safety.Safety
import app.binelik.safety.SafetyHit

/**
 * 1.3: each character remembers a few short things from earlier conversations («πάλι το αφεντικό;»).
 * Only on the phone, at most [MAX] lines per character, never health, names or anything sensitive.
 * The user can turn it off (Settings) or make a character forget («Ξέχνα με»).
 */
class Memory(context: Context) {
    private val p = context.getSharedPreferences("memory", Context.MODE_PRIVATE)

    fun lines(id: String): List<String> = p.getString("m_$id", null).orEmpty().lines().map { it.trim() }.filter { it.isNotEmpty() }.take(MAX)

    fun save(id: String, lines: List<String>) {
        p.edit().putString("m_$id", lines.take(MAX).joinToString("\n")).putInt("since_$id", 0).apply()
    }

    fun forget(id: String) = p.edit().remove("m_$id").remove("since_$id").apply()
    fun forgetAll() = p.edit().clear().apply()

    /** Counts the user's turns since the last update; true when it's time to update the memory. */
    fun countTurn(id: String): Boolean {
        val n = p.getInt("since_$id", 0) + 1
        p.edit().putInt("since_$id", n).apply()
        return n >= EVERY
    }

    companion object {
        const val MAX = 4
        /** After this many of the user's messages to a character, its memory is refreshed. */
        const val EVERY = 6

        /** What goes into the character's instructions. */
        fun promptPart(lines: List<String>): String = if (lines.isEmpty()) "" else buildString {
            appendLine(t(
                "Θυμάσαι από παλιότερες κουβέντες με τον χρήστη (χρησιμοποίησέ τα φυσικά, μόνο όταν ταιριάζει, π.χ. «πάλι το αφεντικό;»· μην τα απαριθμείς):",
                "You remember from earlier conversations with the user (use them naturally, only when it fits, e.g. \"the boss again?\"; don't list them):",
            ))
            lines.forEach { appendLine("- $it") }
        }.trim()

        /** The request to the AI that writes the memory from the old one and the latest conversation. */
        fun updatePrompt(old: List<String>): String = t(
            "Γράφεις τη μνήμη ενός χαρακτήρα για τον χρήστη μιας εφαρμογής ξεσπάσματος. Από την παλιά μνήμη και την κουβέντα, γράψε έως $MAX πολύ σύντομες γραμμές (έως 12 λέξεις η καθεμία) για το τι τον εκνευρίζει και για ποιον μιλάει, με ρόλους και όχι με ονόματα (π.χ. «Τον εκνευρίζει το αφεντικό του που τον φορτώνει υπερωρίες»). " +
                "ΠΟΤΕ υγεία, ψυχική υγεία, αυτοκτονία ή αυτοτραυματισμός, σεξουαλική ζωή, θρησκεία, πολιτικά, οικονομικά στοιχεία, διευθύνσεις, τηλέφωνα ή ονόματα. Κράτα τα πιο πρόσφατα και τα πιο συχνά. Μόνο οι γραμμές, μία σε κάθε σειρά, χωρίς αρίθμηση. Αν δεν υπάρχει κάτι, γράψε μόνο «-».\n" +
                "Παλιά μνήμη:\n" + old.joinToString("\n").ifEmpty { "(καμία)" },
            "You write a character's memory about the user of a venting app. From the old memory and the conversation, write up to $MAX very short lines (up to 12 words each) about what annoys them and who they talk about, by role and never by name (e.g. \"Annoyed by a boss who piles on overtime\"). " +
                "NEVER health, mental health, suicide or self-harm, sex life, religion, politics, money details, addresses, phone numbers or names. Keep the most recent and the most frequent. Only the lines, one per row, no numbering. If there is nothing, write only \"-\".\n" +
                "Old memory:\n" + old.joinToString("\n").ifEmpty { "(none)" },
        )

        /** The AI's answer as memory lines: short, safe, no numbering, at most [MAX]. */
        fun parse(reply: String): List<String> = reply.lines()
            .map { it.trim().trimStart('-', '•', '*', ' ').replace(Regex("^\\d+[.)]\\s*"), "").trim() }
            .filter { it.length in 4..120 }
            .filter { Safety.check(it) == SafetyHit.NONE }
            .filterNot { Regex("\\d{6,}|@|https?://").containsMatchIn(it) }
            .distinct()
            .take(MAX)

        /** The conversation sent for the update: only the user's turns and the character's, shortened. */
        fun transcript(turns: List<Turn>): List<Turn> =
            listOf(Turn(true, turns.takeLast(24).joinToString("\n") { (if (it.fromUser) t("Χρήστης: ", "User: ") else t("Χαρακτήρας: ", "Character: ")) + it.text.take(240) }))
    }
}
