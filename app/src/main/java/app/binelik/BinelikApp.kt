package app.binelik

import android.app.Application
import app.binelik.voice.Engine

class BinelikApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Engine.init(this)
    }
}
