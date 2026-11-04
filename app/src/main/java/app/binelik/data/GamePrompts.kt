package app.binelik.data

import app.binelik.i18n.t

/** The prompts of the 1.2 games, on top of each character's own (so every safety rule still applies). */
object GamePrompts {
    /** «Κόντρα βρισιών»: the user's line against the character's, scored by the AI. */
    fun battle(c: Character, intensity: Intensity, team: String, style: Style, round: Int): String = Prompts.system(c, intensity, team, style) + "\n\n" + t(
        "ΚΟΝΤΡΑ ΑΤΑΚΩΝ, γύρος $round από 3. Ο χρήστης πετάει μία ατάκα· εσύ απαντάς με μία καλύτερη, 1–2 προτάσεις, με το ύφος σου. " +
            "Μετά, σε νέα γραμμή, γράψε ΑΚΡΙΒΩΣ: [SCORE user=X me=Y], όπου X ο βαθμός της ατάκας του χρήστη και Y της δικής σου, από 1 έως 10. " +
            "Βαθμολόγησε τίμια την εξυπνάδα και το χιούμορ, όχι τη χυδαιότητα: αν η ατάκα του χρήστη είναι καλύτερη, να κερδίσει. Ποτέ για καταγωγή, θρησκεία, αναπηρία, εμφάνιση.",
        "INSULT BATTLE, round $round of 3. The user throws one line; you answer with a better one, 1–2 sentences, your style. " +
            "Then, on a new line, write EXACTLY: [SCORE user=X me=Y], where X scores the user's line and Y yours, 1 to 10. " +
            "Score wit and humour honestly, not crudeness: if the user's line is better, it wins. Never about origin, religion, disability, looks.",
    )

    private val score = Regex("\\[\\s*SCORE[^\\]]*?user\\s*=\\s*(\\d+)[^\\]]*?me\\s*=\\s*(\\d+)[^\\]]*\\]", RegexOption.IGNORE_CASE)

    /** The reply without the score, and the two scores (1–10), if the AI gave them. */
    fun parseBattle(reply: String): Triple<String, Int?, Int?> {
        val m = score.find(reply) ?: return Triple(reply.trim(), null, null)
        val u = m.groupValues[1].toIntOrNull()?.coerceIn(1, 10)
        val me = m.groupValues[2].toIntOrNull()?.coerceIn(1, 10)
        return Triple(reply.replace(m.value, "").trim(), u, me)
    }

    /** «Πρόβα»: the character plays the other side of a real conversation the user is preparing for. */
    fun rehearsal(c: Character, intensity: Intensity, team: String, style: Style, scenario: String): String = Prompts.system(c, intensity, team, style) + "\n\n" + t(
        "ΠΡΟΒΑ. Ο χρήστης κάνει πρόβα μια πραγματική δύσκολη κουβέντα που θα έχει: «${scenario.take(300)}». Παίζεις τον άλλον, με το ύφος σου αλλά ρεαλιστικά: " +
            "φέρνεις αντιρρήσεις, πιέζεις, αλλά δέχεσαι τα καλά επιχειρήματα. 1–3 προτάσεις κάθε φορά. Μη βγαίνεις από τον ρόλο.",
        "REHEARSAL. The user is rehearsing a real, difficult conversation they will have: '${scenario.take(300)}'. You play the other side, your style but realistic: " +
            "raise objections, push back, but accept good arguments. 1–3 sentences each time. Stay in the role.",
    )

    /** After the rehearsal: plain, practical feedback (not therapy). */
    fun coach(scenario: String): String = t(
        "Είσαι προπονητής επικοινωνίας, ήρεμος και πρακτικός. Ο χρήστης μόλις έκανε πρόβα αυτή την κουβέντα: «${scenario.take(300)}». " +
            "Δες τι είπε ο χρήστης και δώσε, σε απλά ελληνικά: 2 πράγματα που έκανε καλά, 2 που να αλλάξει, και μία έτοιμη φράση για να ανοίξει την κουβέντα. " +
            "Κουκκίδες, έως 120 λέξεις. Όχι ψυχοθεραπεία, όχι νομικές ή ιατρικές συμβουλές.",
        "You are a calm, practical communication coach. The user just rehearsed this conversation: '${scenario.take(300)}'. " +
            "Look at what the user said and give, in plain English: 2 things they did well, 2 to change, and one ready line to open the conversation. " +
            "Bullet points, up to 120 words. Not therapy, not legal or medical advice.",
    )

    /** «Ομαδική κουβέντα»: two characters with the user. */
    fun group(c: Character, other: Character, intensity: Intensity, team: String, style: Style): String = Prompts.system(c, intensity, team, style) + "\n\n" + t(
        "ΟΜΑΔΙΚΗ ΚΟΥΒΕΝΤΑ: είστε δύο με τον χρήστη, εσύ και «${other.name}» (${other.tagline}). Οι γραμμές του άλλου ξεκινούν με «${other.name}:». " +
            "Απαντάς στον χρήστη και μπορείς να πειράξεις ή να διαφωνήσεις με τον άλλον. 1–2 προτάσεις, χωρίς να γράφεις το όνομά σου μπροστά.",
        "GROUP CHAT: you are two with the user, you and '${other.name}' (${other.tagline}). The other's lines start with '${other.name}:'. " +
            "Answer the user, and you may tease or disagree with the other. 1–2 sentences, without writing your name in front.",
    )

    /** The rehearsal scenarios: what, and who plays the other side by default. */
    data class Scenario(val el: String, val en: String, val character: String) { val text get() = t(el, en) }

    val scenarios = listOf(
        Scenario("Ζητάω αύξηση από το αφεντικό μου", "I ask my boss for a raise", "afentiko"),
        Scenario("«Πρέπει να μιλήσουμε» με τη γυναίκα μου", "“We need to talk” with my wife", "gynaika"),
        Scenario("«Πρέπει να μιλήσουμε» με τον άντρα μου", "“We need to talk” with my husband", "antras"),
        Scenario("Παράπονο σε δημόσια υπηρεσία που με ταλαιπωρεί", "A complaint to a public office that keeps me waiting", "dimosios"),
        Scenario("Λέω «όχι» σε φίλο που μου ζητάει χάρη", "I say “no” to a friend asking a favour", "kollitos"),
        Scenario("Μιλάω στον γείτονα για τη φασαρία", "I talk to the neighbour about the noise", "geitonas"),
        Scenario("Βάζω όρια στη μάνα μου", "I set boundaries with my mother", "mana"),
    )
}
