package app.binelik.safety

import java.text.Normalizer

/** What the local check found in what the user said. */
enum class SafetyHit { NONE, SELF_HARM, VIOLENCE_INTENT }

/**
 * The first safety layer, on the phone, before anything goes to the AI (the second is the rules in
 * the system prompt). Rules agreed with Giannis (4/11/2026):
 * - suicide or self-harm: stop at once, even at a plain mention;
 * - violence: stop only with a serious intent (a plan, a weapon, a time or place, «το εννοώ");
 *   ordinary angry phrases like «θα τον σκοτώσω τον μαλάκα" carry on.
 */
object Safety {

    /** Lower case, without accents or diaeresis, ς → σ, punctuation as spaces. */
    fun normalize(s: String): String {
        val d = Normalizer.normalize(s.lowercase(), Normalizer.Form.NFD).replace(Regex("\\p{Mn}+"), "")
        return d.replace('ς', 'σ').replace(Regex("[^\\p{L}\\p{N}]+"), " ").trim()
    }

    private val selfHarm = listOf(
        Regex("αυτοκτον"), Regex("αυτοτραυματ"), Regex("αυτοκαταστροφ"),
        Regex("θελω να πεθανω"), Regex("να πεθανω(?! απ?ο? τα γελια)"), Regex("δεν θελω να ζω"), Regex("δε θελω να ζω"),
        Regex("δεν αξιζει να ζω"), Regex("να τελειωσω τη ζωη"), Regex("βαλω τελοσ στη ζωη"), Regex("βαλω τελοσ σε ολα"), Regex("τελειωσω τη ζωη"),
        Regex("κοψω τισ φλεβεσ"), Regex("κοβω τισ φλεβεσ"), Regex("να κρεμαστω"), Regex("να πεσω απο τη γεφυρα"),
        Regex("να πηδηξω απο"), Regex("να φυγω απο τη ζωη"),
        Regex("\\bsuicid"), Regex("kill myself"), Regex("want to die"), Regex("end my life"), Regex("self harm"),
    )

    private val harm = Regex(
        "σκοτωσ|σκοτωνω|φονευ|φονο|φονι|μαχαιρωσ|μαχαιρι|πυροβολ|να τον καθαρισω|να την καθαρισω|" +
            "\\bkill\\b|\\bmurder|\\bshoot\\b|\\bstab\\b",
    )

    private val intent = Regex(
        "το εννοω|σοβαρα το λεω|δεν αστειευομαι|δεν κανω πλακα|εχω σχεδιο|το εχω σχεδιασει|" +
            "εχω (?:ενα )?(?:οπλο|πιστολι|περιστροφο|καραμπινα|τουφεκι|μαχαιρι)|πηρα (?:ενα )?(?:οπλο|πιστολι|μαχαιρι)|" +
            "ξερω που μενει|θα παω σπιτι του|θα παω σπιτι τησ|τον περιμενω εξω|την περιμενω εξω|" +
            "αυριο το πρωι|αποψε|στισ \\d|\\bi mean it\\b|\\bi have a gun\\b|\\bi have a plan\\b",
    )

    fun check(text: String): SafetyHit {
        val n = normalize(text)
        if (n.isEmpty()) return SafetyHit.NONE
        if (selfHarm.any { it.containsMatchIn(n) }) return SafetyHit.SELF_HARM
        if (harm.containsMatchIn(n) && intent.containsMatchIn(n)) return SafetyHit.VIOLENCE_INTENT
        return SafetyHit.NONE
    }
}
