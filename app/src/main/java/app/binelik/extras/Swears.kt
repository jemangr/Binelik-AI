package app.binelik.extras

import app.binelik.safety.Safety

/**
 * Swear words: counted for the Binelik-meter and turned into «μπιπ» in bleep mode. A short list
 * of roots, Greek and English; good enough for a game, not a filter.
 */
object Swears {
    private val roots = listOf(
        "μαλακ", "γαμ", "πουστ", "μουν", "καριολ", "πουτα", "αρχιδ", "σκατ", "κωλ", "τσουλ", "παπαρ", "κερατ", "πουτσ", "ψωλ", "μπινελικ",
        "fuck", "shit", "bitch", "asshole", "bastard", "dick", "crap", "bollock", "wank",
    )
    // Not swear words, though they start the same way.
    private val not = listOf("γαμοσ", "γαμου", "γαμοι", "γαμπρ", "γαμηλ", "κωλυ", "κωλοβ", "σκατζοχ", "κερατο", "μουντ", "μουνουχ", "dickens")

    fun isSwear(word: String): Boolean {
        val n = Safety.normalize(word)
        return n.isNotEmpty() && roots.any { n.startsWith(it) } && not.none { n.startsWith(it) }
    }

    private val word = Regex("[\\p{L}]+")

    fun count(text: String): Int = word.findAll(text).count { isSwear(it.value) }

    /** The swear words in [text], normalized, for «the word of the week». */
    fun words(text: String): List<String> = word.findAll(text).map { it.value }.filter { isSwear(it) }.map { Safety.normalize(it) }.toList()

    /** Bleep mode: every swear word becomes «μπιπ» (or «beep»). */
    fun mask(text: String, en: Boolean = false): String = word.replace(text) { if (isSwear(it.value)) (if (en) "beep" else "μπιπ") else it.value }
}
