package app.binelik.extras

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.os.Build
import android.service.quicksettings.TileService
import android.widget.RemoteViews
import app.binelik.R
import app.binelik.voice.openApp

/** «Ξέσπα τώρα»: opens the app straight into Live (from the home-screen widget or the quick settings tile). */
fun ventNow(c: Context): Intent = openApp(c).putExtra(EXTRA_PLAY, true)
const val EXTRA_PLAY = "app.binelik.PLAY"

class VentWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        val pi = PendingIntent.getActivity(context, 21, ventNow(context), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        ids.forEach { id ->
            val v = RemoteViews(context.packageName, R.layout.widget_vent)
            v.setOnClickPendingIntent(R.id.widget_root, pi)
            manager.updateAppWidget(id, v)
        }
    }
}

class VentTile : TileService() {
    override fun onClick() {
        val pi = PendingIntent.getActivity(this, 22, ventNow(this), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        if (Build.VERSION.SDK_INT >= 34) startActivityAndCollapse(pi)
        else @Suppress("DEPRECATION") startActivityAndCollapse(ventNow(this))
    }
}
