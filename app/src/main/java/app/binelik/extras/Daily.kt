package app.binelik.extras

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import app.binelik.R
import app.binelik.data.Characters
import app.binelik.data.Settings
import app.binelik.i18n.Lang
import app.binelik.i18n.t
import app.binelik.voice.openApp
import java.util.Calendar

/**
 * «Ατάκα της ημέρας»: once a day (around 11:00, as Android sees fit), a line from a random
 * character. Off by default; no AI and no internet: the lines are in the app.
 */
object Daily {
    private val extraEl = listOf(
        "Πάλι εσύ; Έλα, πες τα να ξεσπάσεις.", "Σήμερα θα σε εκνευρίσει κάποιος. Εγώ είμαι εδώ.", "Ήπιες τον καφέ σου ή θα τα φάω εγώ;",
        "Κάποιος παρκάρει διπλοθέσιο αυτή τη στιγμή. Θες να το συζητήσουμε;", "Πέντε λεπτά ξέσπασμα, και μετά πίσω στη δουλειά.",
    )
    private val extraEn = listOf(
        "You again? Come on, let it all out.", "Someone will annoy you today. I'm here.", "Had your coffee, or am I getting it in the neck?",
        "Someone is double-parking right now. Want to talk about it?", "Five minutes of venting, then back to work.",
    )

    fun line(random: java.util.Random = java.util.Random()): Pair<String, String> {
        val cs = Characters.all
        val c = cs[random.nextInt(cs.size)]
        val pool = listOf(c.sample) + (if (Lang.en) extraEn else extraEl)
        return c.name to pool[random.nextInt(pool.size)]
    }

    private fun pi(context: Context) = PendingIntent.getBroadcast(context, 11, Intent(context, DailyReceiver::class.java), PendingIntent.FLAG_IMMUTABLE)

    fun schedule(context: Context, on: Boolean) {
        val am = context.getSystemService(AlarmManager::class.java)
        am.cancel(pi(context))
        if (!on) return
        val next = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 11); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0)
            if (timeInMillis <= System.currentTimeMillis()) add(Calendar.DAY_OF_YEAR, 1)
        }
        am.setInexactRepeating(AlarmManager.RTC, next.timeInMillis, AlarmManager.INTERVAL_DAY, pi(context))
    }

    @android.annotation.SuppressLint("MissingPermission") // canNotify() checks it
    fun show(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel("daily", t("Ατάκα της ημέρας", "Line of the day"), NotificationManager.IMPORTANCE_DEFAULT))
        val (who, what) = line()
        val n = NotificationCompat.Builder(context, "daily").setSmallIcon(R.drawable.ic_stat).setContentTitle(who).setContentText(what)
            .setStyle(NotificationCompat.BigTextStyle().bigText(what)).setAutoCancel(true)
            .setContentIntent(PendingIntent.getActivity(context, 12, openApp(context), PendingIntent.FLAG_IMMUTABLE)).build()
        if (canNotify(context)) runCatching { NotificationManagerCompat.from(context).notify(12, n) }
    }
}

class DailyReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val s = Settings(context)
        Lang.en = s.english
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED -> Daily.schedule(context, s.dailyLine)
            else -> if (s.dailyLine) Daily.show(context)
        }
    }
}

/** 1.3.4 (Lint): notifications only when Android allows them (Android 13+ asks the user). */
fun canNotify(context: android.content.Context): Boolean =
    android.os.Build.VERSION.SDK_INT < 33 ||
        androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.POST_NOTIFICATIONS) == android.content.pm.PackageManager.PERMISSION_GRANTED
