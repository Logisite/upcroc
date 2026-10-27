package com.upcroc

import android.app.Application
import androidx.room.Room
import com.upcroc.auth.SessionStore
import com.upcroc.auth.TokenStore
import com.upcroc.core.AuthCoordinator
import com.upcroc.core.Notifier
import com.upcroc.core.Watcher
import com.upcroc.data.UpcrocDatabase
import com.upcroc.ui.UiModel

/**
 * Manual dependency graph. One instance per process, created lazily so nothing
 * touches disk before the app is actually used.
 */
class AppContainer(app: Application) {
    val db: UpcrocDatabase by lazy {
        Room.databaseBuilder(app, UpcrocDatabase::class.java, "upcroc.db")
            // The watcher owns schema; a destructive fallback would wipe history.
            .fallbackToDestructiveMigration(dropAllTables = true)
            .build()
    }

    val tokens: TokenStore = TokenStore(app)
    val sessions: SessionStore = SessionStore(app)
    val notifier: Notifier by lazy { Notifier(app) }
    val watcher: Watcher by lazy { Watcher(db, tokens, notifier) }

    /**
     * Held at process scope, not per-Activity: the sign-in attempt outlives the
     * screen, and a killed process can pick the attempt back up from disk.
     */
    val auth: AuthCoordinator by lazy { AuthCoordinator(app, sessions, tokens) }

    val ui: UiModel by lazy { UiModel(app, db, auth, watcher) }
}

class UpcrocApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}