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
)

object StatsCalc {
    private const val DAY = 24 * 60 * 60 * 1000L

    fun compute(all: List<Message>, anger: List<Anger>, now: Long = System.currentTimeMillis(), zone: ZoneId = ZoneId.systemDefault()): Stats {
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
        return Stats(swearsToday, swearsWeek, swearsTotal, week.size, topWord, topCharacter, drop, weekAnger, streak, badges)
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
