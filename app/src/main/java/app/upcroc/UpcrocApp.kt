package app.upcroc

import android.app.Application
import app.upcroc.di.AppContainer

class UpcrocApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        if (BuildConfig.DEBUG) {
            dev.chrisbanes.haze.HazeLogger.enabled = true
        }
        container = AppContainer(this)
        container.start()
    }
}
