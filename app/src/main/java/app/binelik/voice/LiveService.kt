package app.binelik.voice

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import app.binelik.R
import app.binelik.i18n.t

/**
 * Keeps the voice conversation going with the screen off or in the car: a foreground service of
 * type microphone, with a notification that says who you're talking to and a Pause button.
 */
class LiveService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_PAUSE) {
            Engine.stop(); stopSelf(); return START_NOT_STICKY
        }
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CHANNEL, t("Live", "Live"), NotificationManager.IMPORTANCE_LOW))
        val c = Engine.character()
        val pause = PendingIntent.getService(this, 1, Intent(this, LiveService::class.java).setAction(ACTION_PAUSE), PendingIntent.FLAG_IMMUTABLE)
        val open = PendingIntent.getActivity(this, 2, openApp(this), PendingIntent.FLAG_IMMUTABLE)
        val n: Notification = NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat)
            .setContentTitle(t("Live με: ${c.name}", "Live with: ${c.name}"))
            .setContentText(t("Το μικρόφωνο είναι ανοιχτό για την κουβέντα.", "The microphone is open for the conversation."))
            .setColor(c.color.toInt())
            .setOngoing(true)
            .setContentIntent(open)
            .addAction(0, t("Παύση", "Pause"), pause)
            .build()
        runCatching {
            ServiceCompat.startForeground(this, ID, n, if (Build.VERSION.SDK_INT >= 30) ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE else 0)
        }.onFailure { stopSelf() }
        return START_NOT_STICKY
    }

    companion object {
        private const val CHANNEL = "live"
        private const val ID = 7
        private const val ACTION_PAUSE = "app.binelik.PAUSE"

        fun start(c: Context) = runCatching { androidx.core.content.ContextCompat.startForegroundService(c, Intent(c, LiveService::class.java)) }
        fun stop(c: Context) = runCatching { c.stopService(Intent(c, LiveService::class.java)) }
    }
}
