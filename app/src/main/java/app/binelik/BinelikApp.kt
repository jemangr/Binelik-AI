package app.binelik

import android.app.Application
import app.binelik.voice.Engine

class BinelikApp : Application() {
    override fun onCreate() {
        super.onCreate()
        app.binelik.extras.Crash.install(this)
        Engine.init(this)
        if (Engine.settings.dailyLine) app.binelik.extras.Daily.schedule(this, true)
    }
}
