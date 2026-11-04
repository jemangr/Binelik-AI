package app.binelik.extras

import app.binelik.data.Anger
import app.binelik.data.Characters
import app.binelik.data.Message
import app.binelik.i18n.t
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** A badge: earned or not yet, with what it takes. */
data class Badge(val icon: String, val title: String, val how: String, val earned: Boolean)

/** The Binelik-meter, the anger meter and the badges, all from what is on the phone. */
data class Stats(
    val swearsToday: Int,
    val swearsWeek: Int,
    val swearsTotal: Int,
    val messagesWeek: Int,
    /** The swear word of the week (normalized), if any. */
    val topWord: String?,
    /** Who the user talked to most this week. */
    val topCharacter: String?,
    /** On average, how many points the anger dropped (before minus after), this week. */
    val angerDrop: Double?,
    val angerReadings: List<Anger>,
    /** Days in a row with at least one message, up to today. */
    val streak: Int,
    val badges: List<Badge>,
    /** The anger calendar: the last 35 days, average anger before Live (null: no reading). */
    val calendar: List<Pair<LocalDate, Double?>> = emptyList(),
    /** The day of the week the user is angriest (1 = Monday), with its average, from at least 3 readings. */
    val angriestDay: Pair<Int, Double>? = null,
)

object StatsCalc {
    private const val DAY = 24 * 60 * 60 * 1000L

    /** The 1.2 game counters, from the settings (scream record, plates, battles won, burns, rehearsals). */
    data class Games(val scream: Int = 0, val plates: Int = 0, val battlesWon: Int = 0, val burns: Int = 0, val rehearsals: Int = 0)

    fun compute(all: List<Message>, anger: List<Anger>, now: Long = System.currentTimeMillis(), zone: ZoneId = ZoneId.systemDefault(), games: Games = Games()): Stats {
        fun day(ms: Long) = Instant.ofEpochMilli(ms).atZone(zone).toLocalDate()
        val today = day(now)
        val mine = all.filter { it.fromUser && !it.pause }
        val week = mine.filter { it.time >= now - 7 * DAY }
        val swearsTotal = mine.sumOf { Swears.count(it.text) }
        val swearsWeek = week.sumOf { Swears.count(it.text) }
        val swearsToday = mine.filter { day(it.time) == today }.sumOf { Swears.count(it.text) }
        val topWord = week.flatMap { Swears.words(it.text) }.groupingBy { it }.eachCount().maxByOrNull { it.value }?.key
        val topCharacter = week.groupingBy { it.character }.eachCount().maxByOrNull { it.value }?.key
            ?.let { id -> Characters.byId(id)?.name ?: id }
        val done = anger.filter { it.after > 0 }
        val weekAnger = done.filter { it.time >= now - 7 * DAY }
        val drop = weekAnger.takeIf { it.isNotEmpty() }?.map { (it.before - it.after).toDouble() }?.average()

        val days = mine.map { day(it.time) }.toSet()
        var streak = 0
        var d: LocalDate = today
        while (d in days) { streak++; d = d.minusDays(1) }
        val bestStreak = longestRun(days)

        val month = mine.filter { it.time >= now - 30 * DAY }
        val byCharacter = mine.groupingBy { it.character }.eachCount()
        val badges = listOf(
            Badge("💥", t("Πρώτο ξέσπασμα", "First vent"), t("Το πρώτο σου μήνυμα", "Your first message"), mine.isNotEmpty()),
            Badge("🔥", t("Σερί 3 ημερών", "3-day streak"), t("Ξέσπασες 3 μέρες στη σειρά", "Vent 3 days in a row"), bestStreak >= 3),
            Badge("🏆", t("Σερί εβδομάδας", "Week streak"), t("Ξέσπασες 7 μέρες στη σειρά", "Vent 7 days in a row"), bestStreak >= 7),
            Badge("🤬", t("Μπινελικάς", "Potty mouth"), t("100 βρισιές συνολικά", "100 swear words in total"), swearsTotal >= 100),
            Badge("🧘", t("Ψυχραιμία", "Cool head"), t("Ο θυμός σου έπεσε 5 βαθμούς σε μία κουβέντα", "Your anger dropped 5 points in one conversation"), done.any { it.before - it.after >= 5 }),
            Badge("🎭", t("Τους γνώρισες όλους", "Met them all"), t("Μίλησες με 10 χαρακτήρες", "Talk to 10 characters"), byCharacter.keys.size >= 10),
            Badge("🥊", t("Καβγατζής του μήνα", "Brawler of the month"), t("50 μηνύματα στον Καβγατζή σε 30 μέρες", "50 messages to the Brawler in 30 days"), month.count { it.character == "kavgatzis" } >= 50),
            Badge("🕊️", t("Ειρηνοποιός", "Peacemaker"), t("20 μηνύματα στον Υπομονετικό ή στον Ψυχαναλυτή", "20 messages to the Patient one or the Shrink"), (byCharacter["ypomonetikos"] ?: 0) + (byCharacter["psychanalytis"] ?: 0) >= 20),
        )
        val more = badges + listOf(
            Badge("🗣️", t("Λαρύγγι", "Big lungs"), t("90+ στο ουρλιαχτόμετρο", "90+ on the scream meter"), games.scream >= 90),
            Badge("🏺", t("Ζορμπάς", "Zorba"), t("100 σπασμένα πιάτα", "100 smashed plates"), games.plates >= 100),
            Badge("🎤", t("Βασιλιάς της κόντρας", "Battle king"), t("Κέρδισες μια κόντρα βρισιών", "Win an insult battle"), games.battlesWon >= 1),
            Badge("🔥", t("Πυρομανής", "Pyromaniac"), t("Έκαψες 10 κείμενα", "Burn 10 texts"), games.burns >= 10),
            Badge("🎭", t("Έτοιμος για όλα", "Ready for anything"), t("Έκανες 3 πρόβες", "Do 3 rehearsals"), games.rehearsals >= 3),
        )
        val byDay = anger.groupBy { day(it.time) }.mapValues { (_, l) -> l.map { it.before.toDouble() }.average() }
        val calendar = (34 downTo 0).map { k -> today.minusDays(k.toLong()).let { it to byDay[it] } }
        val angriest = anger.groupBy { day(it.time).dayOfWeek.value }.filter { it.value.size >= 3 }
            .mapValues { (_, l) -> l.map { it.before.toDouble() }.average() }.maxByOrNull { it.value }?.toPair()
        return Stats(swearsToday, swearsWeek, swearsTotal, week.size, topWord, topCharacter, drop, weekAnger, streak, more, calendar, angriest)
    }

    fun longestRun(days: Set<LocalDate>): Int {
        var best = 0
        for (d in days) if (d.minusDays(1) !in days) {
            var n = 0; var x = d
            while (x in days) { n++; x = x.plusDays(1) }
            best = maxOf(best, n)
        }
        return best
    }
}
