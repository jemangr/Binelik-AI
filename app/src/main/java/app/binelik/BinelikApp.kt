package app.binelik

import android.app.Application
import app.binelik.voice.Engine

class BinelikApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Engine.init(this)
        if (Engine.settings.dailyLine) app.binelik.extras.Daily.schedule(this, true)
    }
}
