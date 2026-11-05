package app.binelik.extras

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import app.binelik.R
import app.binelik.data.Db
import app.binelik.data.Settings
import app.binelik.i18n.Lang
import app.binelik.i18n.t
import app.binelik.voice.openApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.Calendar

/**
 * 1.3: «Θυμόμετρο εβδομάδας»: a home-screen widget, and on Friday evening one notification
 * («Πώς πήγε η βδομάδα;»). Everything is counted on the phone, without AI and without internet.
 */
object Weekly {
    data class Week(val vents: Int, val swears: Int, val angerBefore: Double?, val angerDrop: Double?)

    /** 0–10: how stormy the week was, from the anger readings, or from the swearing when there are none. */
    fun meter(w: Week): Int = when {
        w.angerBefore != null -> w.angerBefore.toInt().coerceIn(0, 10)
        w.vents == 0 -> 0
        else -> (2 + w.swears / 5).coerceIn(1, 10)
    }

    fun bar(level: Int): String = "🟥".repeat(level.coerceIn(0, 10) / 2) + "⬛".repeat(5 - level.coerceIn(0, 10) / 2)

    fun line(w: Week): String = when {
        w.vents == 0 -> t("Ήσυχη βδομάδα. Κανένα ξέσπασμα. Ζεν!", "A quiet week. No venting at all. Zen!")
        w.angerDrop != null && w.angerDrop >= 2 -> t("${w.vents} ξεσπάσματα, και ο θυμός έπεφτε κατά ${"%.1f".format(w.angerDrop)} κάθε φορά.", "${w.vents} vents, and the anger dropped by ${"%.1f".format(w.angerDrop)} each time.")
        else -> t("${w.vents} ξεσπάσματα και ${w.swears} βρισιές αυτή τη βδομάδα.", "${w.vents} vents and ${w.swears} swear words this week.")
    }

    suspend fun week(context: Context, now: Long = System.currentTimeMillis()): Week {
        val db = Db.get(context)
        val since = now - 7 * 24 * 3600 * 1000L
        val mine = db.messages().since(since).filter { it.fromUser && !it.pause }
        val anger = db.anger().since(since)
        val done = anger.filter { it.after > 0 }
        return Week(
            vents = mine.size,
            swears = mine.sumOf { Swears.count(it.text) },
            angerBefore = anger.takeIf { it.isNotEmpty() }?.map { it.before.toDouble() }?.average(),
            angerDrop = done.takeIf { it.isNotEmpty() }?.map { (it.before - it.after).toDouble() }?.average(),
        )
    }

    /** Redraws the widgets (after a message, and every few hours). */
    fun refreshWidgets(context: Context) {
        val m = AppWidgetManager.getInstance(context)
        val ids = m.getAppWidgetIds(ComponentName(context, WeekWidget::class.java))
        if (ids.isEmpty()) return
        context.sendBroadcast(Intent(context, WeekWidget::class.java).setAction(AppWidgetManager.ACTION_APPWIDGET_UPDATE).putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids))
    }

    private fun pi(context: Context) = PendingIntent.getBroadcast(context, 31, Intent(context, WeeklyReceiver::class.java), PendingIntent.FLAG_IMMUTABLE)

    /** Friday at 18:00 (inexact, as Android sees fit), every week. */
    fun schedule(context: Context, on: Boolean) {
        val am = context.getSystemService(AlarmManager::class.java)
        am.cancel(pi(context))
        if (!on) return
        am.setInexactRepeating(AlarmManager.RTC, nextFriday().timeInMillis, AlarmManager.INTERVAL_DAY * 7, pi(context))
    }

    fun nextFriday(from: Calendar = Calendar.getInstance()): Calendar = (from.clone() as Calendar).apply {
        set(Calendar.HOUR_OF_DAY, 18); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        while (get(Calendar.DAY_OF_WEEK) != Calendar.FRIDAY || timeInMillis <= from.timeInMillis) add(Calendar.DAY_OF_YEAR, 1)
    }

    fun notify(context: Context, w: Week) {
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel("weekly", t("Πώς πήγε η βδομάδα;", "How did the week go?"), NotificationManager.IMPORTANCE_DEFAULT))
        val text = bar(meter(w)) + "  " + line(w)
        val n = NotificationCompat.Builder(context, "weekly").setSmallIcon(R.drawable.ic_stat)
            .setContentTitle(t("Πώς πήγε η βδομάδα;", "How did the week go?")).setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text + "\n" + t("Πες τα όλα πριν το Σαββατοκύριακο.", "Let it all out before the weekend.")))
            .setAutoCancel(true)
            .setContentIntent(PendingIntent.getActivity(context, 32, ventNow(context), PendingIntent.FLAG_IMMUTABLE)).build()
        runCatching { NotificationManagerCompat.from(context).notify(32, n) }
    }
}

class WeeklyReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val s = Settings(context)
        Lang.en = s.english
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED -> Weekly.schedule(context, s.fridayNote)
            else -> if (s.fridayNote) {
                val done = goAsync()
                CoroutineScope(Dispatchers.IO).launch {
                    runCatching { Weekly.notify(context, Weekly.week(context)) }
                    done.finish()
                }
            }
        }
    }
}

/** The «Θυμόμετρο εβδομάδας» widget: the week's meter, the vents and the swear words; a tap opens Live. */
class WeekWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        Lang.en = Settings(context).english
        val done = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            runCatching {
                val w = Weekly.week(context)
                val pi = PendingIntent.getActivity(context, 33, ventNow(context), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
                ids.forEach { id ->
                    val v = RemoteViews(context.packageName, R.layout.widget_week)
                    v.setTextViewText(R.id.week_title, t("Θυμόμετρο εβδομάδας", "Week's anger meter"))
                    v.setTextViewText(R.id.week_bar, Weekly.bar(Weekly.meter(w)))
                    v.setTextViewText(R.id.week_line, Weekly.line(w))
                    v.setOnClickPendingIntent(R.id.week_root, pi)
                    manager.updateAppWidget(id, v)
                }
            }
            done.finish()
        }
    }
}
