package com.upcroc.core

import com.upcroc.core.Notifier
import com.upcroc.data.K
import com.upcroc.data.SettingsEntity
import com.upcroc.data.UpcrocDatabase
import kotlin.math.min

/** Retention. Runs on every tick, so it holds even while checks are failing. */
internal suspend fun prune(db: UpcrocDatabase, settings: SettingsEntity) {
    val cutoff = Parse.now() - settings.retentionDays * 86_400L
    db.jobs().prune(cutoff)
    db.events().prune(cutoff)
    val last = db.state().getLong(K.LAST_VACUUM)
    if (Parse.now() - last > 86_400) db.state().setLong(K.LAST_VACUUM, Parse.now())
}

/** The session cap. Zero means never started, so no cap has been reached. */
internal suspend fun sessionExpired(db: UpcrocDatabase, settings: SettingsEntity): Boolean {
    val started = db.state().getLong(K.SESSION_STARTED)
    if (started == 0L) return false
    return Parse.now() - started > settings.sessionCapHours * 3_600L
}

/** Exponential backoff on failure, capped, so a bad network is not hammered. */
internal suspend fun backoff(db: UpcrocDatabase): Long {
    val level = db.state().getLong(K.BACKOFF_LEVEL) + 1
    val wait = min(30L shl min(level.toInt(), 6), 1_800L)
    db.state().setLong(K.BACKOFF_LEVEL, level)
    db.state().setLong(K.BACKOFF_UNTIL, Parse.now() + wait)
    TickerState.update { it.copy(backoffUntil = Parse.now() + wait) }
    return wait
}

/** Nudge for a new sign-in, at most every six hours so it cannot pile up. */
internal suspend fun nagReauth(db: UpcrocDatabase, notifier: Notifier) {
    if (Parse.now() - db.state().getLong(K.LAST_REAUTH_NAG) > 6 * 3_600) {
        notifier.authRequired()
        db.state().setLong(K.LAST_REAUTH_NAG, Parse.now())
    }
}