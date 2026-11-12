package app.upcroc

import android.app.Application
import app.upcroc.di.AppContainer

class UpcrocApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        container.start()
    }
}
