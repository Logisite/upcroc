package com.upcroc.core

import com.upcroc.auth.PendingAuth
import com.upcroc.auth.TokenStore
import com.upcroc.auth.Tokens
import com.upcroc.data.DEFAULT_QUERIES
import com.upcroc.data.DEFAULT_SETTINGS
import com.upcroc.data.EventEntity
import com.upcroc.data.K
import com.upcroc.data.SettingsEntity
import com.upcroc.data.StateEntity
import com.upcroc.data.UpcrocDatabase
import com.upcroc.net.AuthException
import com.upcroc.net.UpworkMcp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlin.random.Random

private data class Outcome(val status: String, val detail: String = "")

/**
 * The bucket engine.
 *
 * Cadence is a bucket of `base + random(0..rotation)` seconds, drawn after each
 * check and drained one second at a time. Both numbers are user settings. The
 * bucket lives in the database, so a killed process resumes on the same
 * countdown instead of starting over.
 */
class Watcher(
    private val db: UpcrocDatabase,
    private val tokens: TokenStore,
    private val notifier: Notifier,
) {

    /**
     * Checks run on their own supervisor scope so a failure inside the MCP SDK
     * can never cancel the ticker. The child converts every outcome into a plain
     * [Outcome] so `await` cannot throw. Cancelled by [shutdown] on service
     * teardown, otherwise a check in flight outlives the service that owns it.
     */
    private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun shutdown() {
        ioScope.cancel()
    }

    suspend fun ensureSeed() {
        if (db.settings().get() == null) db.settings().put(DEFAULT_SETTINGS)
        if (db.queries().count() == 0) DEFAULT_QUERIES.forEach { db.queries().put(it) }
    }

    suspend fun token(): String {
        val stored: Tokens = tokens.read() ?: throw AuthException("not signed in")
        if (!stored.expired(Parse.now())) return stored.access
        // A rejected refresh means the grant is gone. Reporting that as a
        // generic error would back off silently instead of asking the user to
        // sign in again.
        val refreshed = try {
            PendingAuth.refresh(stored.refresh)
        } catch (c: CancellationException) {
            throw c
        } catch (t: Throwable) {
            throw AuthException("refresh failed: ${t.message ?: t::class.java.simpleName}")
        }
        tokens.write(refreshed.access, refreshed.refresh, refreshed.expiresAt)
        return refreshed.access
    }

    /**
     * A stored refresh token counts as connected even when the access token has
     * lapsed, because the next check refreshes silently. Reporting "signed out"
     * there would hide the Start button from a user who is actually fine.
     */
    fun connected(): Boolean = tokens.read()?.let { !it.expired(Parse.now()) || it.refresh.isNotEmpty() } ?: false

    suspend fun log(kind: String, message: String) {
        db.events().add(EventEntity(ts = System.currentTimeMillis(), kind = kind, title = message))
    }

    // ---- the ticker -------------------------------------------------------

    suspend fun loop() {
        ensureSeed()
        // Hydrate from disk before the first tick, otherwise every persisted
        // field reads as zero after a process restart and the ticker lies.
        hydrateTicker()
        var lastPrune = 0L
        var tick = 0
        while (currentCoroutineContext().isActive) {
            val settings = db.settings().get() ?: DEFAULT_SETTINGS
            TickerState.update {
                it.copy(watching = settings.watching, connected = connected())
            }
            // The engine now runs for as long as the app is alive, not just
            // while watching, so retention has to hold in this branch too.
            if (Parse.now() - lastPrune > 600) {
                lastPrune = Parse.now()
                prune(db, settings)
            }
            if (settings.watching) {
                val left = db.state().getLong(K.BUCKET_LEFT, -1)
                if (left <= 0) {
                    runCheck(settings)
                } else {
                    db.state().setLong(K.BUCKET_LEFT, left - 1)
                    TickerState.update { it.copy(bucketLeft = left - 1) }
                }
            }
            // Check figures are written on every check already; this keeps the
            // countdown and the last-known numbers recoverable if the process is
            // killed mid-bucket, which is the common case on a phone.
            if (++tick % 5 == 0) persistTicker()
            delay(1_000)
        }
    }

    private suspend fun persistTicker() {
        val t = TickerState.live.value
        val state = db.state()
        state.setLong(K.BUCKET_TOTAL, t.bucketTotal)
        state.setLong(K.BUCKET_LEFT, t.bucketLeft)
        state.setLong(K.BUCKET_DRAWN_AT, t.drawnAt)
        state.setLong(K.LAST_CHECK, t.lastCheck)
        state.setLong(K.SESSION_STARTED, t.sessionStarted)
        state.setLong(K.BACKOFF_UNTIL, t.backoffUntil)
        state.set(StateEntity(K.TICK_STATS, "${t.lastNew},${t.lastNotified},${t.lastPages},${t.lastRequests},${t.lastSeconds}"))
    }

    private suspend fun hydrateTicker() {
        val total = db.state().getLong(K.BUCKET_TOTAL)
        val left = db.state().getLong(K.BUCKET_LEFT)
        val drawn = db.state().getLong(K.BUCKET_DRAWN_AT)
        val last = db.state().getLong(K.LAST_CHECK)
        val session = db.state().getLong(K.SESSION_STARTED)
        val backoffUntil = db.state().getLong(K.BACKOFF_UNTIL)
        val stats = db.state().get(K.TICK_STATS)?.split(',')
        val nums = { i: Int -> stats?.getOrNull(i)?.toIntOrNull() ?: 0 }
        TickerState.update {
            it.copy(
                bucketTotal = total,
                bucketLeft = left,
                drawnAt = drawn,
                lastCheck = last,
                sessionStarted = session,
                backoffUntil = backoffUntil,
                lastNew = nums(0),
                lastNotified = nums(1),
                lastPages = nums(2),
                lastRequests = nums(3),
                lastSeconds = stats?.getOrNull(4)?.toFloatOrNull() ?: 0f,
            )
        }
    }

    private suspend fun drawBucket(settings: SettingsEntity, exactSeconds: Long? = null): Long {
        // The floor is not cosmetic. These are user-editable fields, and a base
        // of zero would fire a full check every second straight at Upwork.
        val total = exactSeconds?.coerceIn(MIN_BUCKET_SEC, MAX_BUCKET_SEC)
            ?: (settings.bucketBaseSec.toLong() +
                Random.nextLong(0, (settings.bucketJitterSec + 1L).coerceAtLeast(1)))
                .coerceIn(MIN_BUCKET_SEC, MAX_BUCKET_SEC)
        db.state().setLong(K.BUCKET_TOTAL, total)
        db.state().setLong(K.BUCKET_LEFT, total)
        db.state().setLong(K.BUCKET_DRAWN_AT, Parse.now())
        TickerState.update {
            it.copy(bucketTotal = total, bucketLeft = total, drawnAt = Parse.now())
        }
        log("bucket", "bucket drawn: $total s")
        return total
    }

    // ---- one check --------------------------------------------------------

    /** Latches so a tripped cap logs once rather than on every tick. */
    private var capLogged = false

    private suspend fun runCheck(settings: SettingsEntity) {
        if (sessionExpired(db, settings)) {
            // The service stays foreground and keeps ticking, so without a latch
            // this logs the identical line on every wake.
            if (!capLogged) {
                capLogged = true
                log("session", "session cap of ${settings.sessionCapHours} h reached; pausing")
            }
            db.state().setLong(K.SESSION_STARTED, 0)
            db.settings().put(settings.copy(watching = false))
            TickerState.update { it.copy(watching = false, inCheck = false) }
            return
        }
        if (db.state().getLong(K.SESSION_STARTED) == 0L) {
            capLogged = false
            db.state().setLong(K.SESSION_STARTED, Parse.now())
            TickerState.update { it.copy(sessionStarted = Parse.now()) }
        }

        TickerState.update { it.copy(inCheck = true) }
        log("check", "check started")
        val started = System.currentTimeMillis()
        val mcp = UpworkMcp({ token() })

        val outcome = isolate {
            mcp.withSession { client, tool ->
                val fresh = mutableListOf<Fresh>()
                var pages = 0
                for (query in db.queries().enabled()) {
                    val result = pageQuery(db, mcp, client, tool, query, settings, ::log)
                    pages += result.pages
                    fresh += result.fresh
                    log("query", "${Parse.describe(query)}: ${result.pages} page(s), ${result.fresh.size} new, stop=${result.stop}")
                }

                // The insert is the dedupe marker, so a process death between
                // storing a job and announcing it would lose it forever. Fold in
                // anything already stored but never announced.
                val pending = db.jobs().unnotifiedSince(Parse.now() - settings.lateNotifySec)
                    .map { Fresh(it, it.publishedAt, needsEnrich = false) }
                val merged = (fresh + pending).distinctBy { it.entity.id }
                    .sortedBy { it.publishedAt }

                val notified = dispatchNewJobs(db, mcp, client, tool, merged, settings, notifier, ::log)

                val seconds = (System.currentTimeMillis() - started) / 1000f
                TickerState.update {
                    it.copy(
                        lastNew = merged.size,
                        lastNotified = notified,
                        lastPages = pages,
                        lastRequests = mcp.requests,
                        lastSeconds = seconds,
                        lastCheck = Parse.now(),
                        inCheck = false,
                    )
                }
                log(
                    "check",
                    "check done: ${merged.size} new, $notified notified, $pages pages, " +
                        "${mcp.requests} requests, ${seconds}s",
                )
                Outcome("ok")
            }
        }

        when (outcome.status) {
            "ok" -> {
                db.state().setLong(K.LAST_CHECK, Parse.now())
                db.state().setLong(K.BACKOFF_LEVEL, 0L)
                db.state().setLong(K.BACKOFF_UNTIL, 0L)
                TickerState.update { it.copy(backoffUntil = 0) }
                drawBucket(settings)
            }

            "auth" -> {
                TickerState.update { it.copy(inCheck = false, connected = false) }
                log("auth", "auth error: ${outcome.detail}")
                nagReauth(db, notifier)
                // Still draw a bucket so the countdown keeps running and says
                // plainly that we are waiting to try again.
                drawBucket(settings, RETRY_SECONDS)
            }

            else -> {
                TickerState.update { it.copy(inCheck = false) }
                log("error", "check ${outcome.status}: ${outcome.detail}")
                val wait = backoff(db)
                drawBucket(settings, wait)
            }
        }
    }

    private suspend fun isolate(block: suspend () -> Outcome): Outcome {
        val child = ioScope.async {
            try {
                block()
            } catch (a: AuthException) {
                Outcome("auth", a.message ?: "")
            } catch (c: CancellationException) {
                Outcome("cancelled", c::class.java.simpleName)
            } catch (t: Throwable) {
                Outcome("error", t.message ?: t::class.java.simpleName)
            }
        }
        return child.await()
    }

    companion object {
        /** Guards the request rate against a mis-typed setting. */
        const val MIN_BUCKET_SEC = 30L
        const val MAX_BUCKET_SEC = 3_600L

        /** How long to wait before retrying after an auth failure. */
        private const val RETRY_SECONDS = 60L
    }
}