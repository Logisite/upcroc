package com.upcroc.ui

import android.app.Application
import android.content.Intent
import android.net.Uri
import androidx.core.content.ContextCompat
import com.upcroc.UpcrocApp
import com.upcroc.WatchService
import com.upcroc.core.AuthCoordinator
import com.upcroc.core.Parse
import com.upcroc.core.Ticker
import com.upcroc.core.TickerState
import com.upcroc.core.Watcher
import com.upcroc.data.DEFAULT_QUERIES
import com.upcroc.data.DEFAULT_SETTINGS
import com.upcroc.data.QueryEntity
import com.upcroc.data.SettingsEntity
import com.upcroc.data.UpcrocDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Application-scoped state holder. Scoped to the process rather than an
 * Activity so a rotation or a return from the browser never restarts anything.
 */
class UiModel(
    private val app: Application,
    private val db: UpcrocDatabase,
    private val auth: AuthCoordinator,
    private val watcher: Watcher,
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    val authState: StateFlow<AuthCoordinator.AuthState> = auth.state

    val ticker: StateFlow<Ticker> = TickerState.live

    val resumable: Boolean get() = auth.resumable()

    /**
     * Fetch Upwork's filter schema on its own, without running a check.
     *
     * Publishing the schema from inside the watcher's session coupled the editor
     * to the watcher being running, so opening Sources on a fresh install showed
     * an empty form and told you to go and start a check first. The form is a
     * read of a published contract; it should not need a job search to render.
     */
    suspend fun refreshSchema() {
        runCatching {
            val mcp = com.upcroc.net.UpworkMcp({ watcher.token() })
            mcp.withSession { client, _ ->
                // find_jobs publishes an undocumented free-form `params`, so the
                // form is built from get_tool_help, which the same server
                // publishes and which documents every per-action filter.
                runCatching {
                    val help = client.callTool(
                        "upwork__get_tool_help",
                        mapOf("tool_name" to "find_jobs"),
                    )
                    val text = help.content.filterIsInstance<
                        io.modelcontextprotocol.kotlin.sdk.types.TextContent,
                        >().joinToString(" ") { it.text }
                    val description = runCatching {
                        kotlinx.serialization.json.Json.parseToJsonElement(text)
                            .let { it as kotlinx.serialization.json.JsonObject }["description"]
                            ?.let { d -> d as kotlinx.serialization.json.JsonPrimitive }
                            ?.content.orEmpty()
                    }.getOrDefault(text)
                    com.upcroc.net.SchemaRepo.publishHelp(description)
                }.onFailure { android.util.Log.w("upcroc", "tool help failed", it) }
            }
        }.onFailure { android.util.Log.w("upcroc", "schema fetch failed", it) }
    }

    val events = db.events().latest(500)
        .stateIn(scope, SharingStarted.Eagerly, emptyList())

    val queries = db.queries().all()
        .stateIn(scope, SharingStarted.Eagerly, emptyList())

    val settings = db.settings().observe()
        .map { it ?: DEFAULT_SETTINGS }
        .stateIn(scope, SharingStarted.Eagerly, DEFAULT_SETTINGS)

    val recentJobs = db.jobs().recent(Parse.now() - 7 * 86_400L, 200)
        .stateIn(scope, SharingStarted.Eagerly, emptyList())

    fun connected(): Boolean = watcher.connected()

    fun refresh() {
        if (auth.state.value.step == com.upcroc.core.AuthStep.CONNECTED) {
            auth.acknowledgeConnected()
        }
    }

    // ---- connection -------------------------------------------------------

    fun beginAuth() = auth.begin()

    fun submitCode(input: String) = auth.submitCode(input)

    fun openAuthLink(url: String) {
        runCatching {
            app.startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
    }

    fun disconnect() = auth.disconnect()

    // ---- settings ---------------------------------------------------------

    /**
     * Writes the settings and records what actually changed.
     *
     * Timing and pacing decide how hard this app hits someone's Upwork
     * account, so a silent edit is not acceptable: the log is the only record
     * of why the request rate moved.
     */
    fun save(next: SettingsEntity) = scope.launch {
        val prev = db.settings().get()
        db.settings().put(next)
        prev?.let { before ->
            describeChange(before, next)?.let { watcher.log("settings", it) }
        }
    }

    private fun describeChange(before: SettingsEntity, after: SettingsEntity): String? {
        val rows = buildList {
            fun cmp(name: String, a: Any?, b: Any?) {
                if (a != b) add("$name ${a ?: "-"} -> ${b ?: "-"}")
            }
            cmp("base", before.bucketBaseSec, after.bucketBaseSec)
            cmp("rotation", before.bucketJitterSec, after.bucketJitterSec)
            cmp("pageCap", before.pageCap, after.pageCap)
            cmp("paceMs", before.paceMs, after.paceMs)
            cmp("freshS", before.freshWindowSec, after.freshWindowSec)
            cmp("lateNotifyS", before.lateNotifySec, after.lateNotifySec)
            cmp("sessionCapH", before.sessionCapHours, after.sessionCapHours)
            cmp("retentionD", before.retentionDays, after.retentionDays)
            cmp("notify", before.notify, after.notify)
            cmp("sound", before.notifySound, after.notifySound)
            cmp("vibrate", before.notifyVibrate, after.notifyVibrate)
            cmp("watching", before.watching, after.watching)
        }
        return rows.takeIf { it.isNotEmpty() }?.joinToString("; ")
    }

    fun setWatching(on: Boolean) = scope.launch {
        val current = db.settings().get() ?: DEFAULT_SETTINGS
        db.settings().put(current.copy(watching = on))
        if (on) {
            // Starting is a new session. Without this the cap is measured from
            // whenever the last session began, so pressing Start hours later
            // trips the cap immediately and the app never checks again.
            db.state().setLong(com.upcroc.data.K.SESSION_STARTED, 0L)
            watcher.ensureSeed()
            app.startForegroundService(Intent(app, WatchService::class.java))
        } else {
            app.stopService(Intent(app, WatchService::class.java))
        }
    }

    // ---- plug-in queries --------------------------------------------------

    fun saveQuery(query: QueryEntity) = scope.launch { db.queries().put(query) }

    fun deleteQuery(id: String) = scope.launch { db.queries().delete(id) }

    fun resetQueries() = scope.launch {
        db.queries().deleteAll()
        DEFAULT_QUERIES.forEach { db.queries().put(it) }
    }

    /** True when the OS would actually let notifications through. */
    fun notificationsAllowed(): Boolean =
        ContextCompat.checkSelfPermission(app, android.Manifest.permission.POST_NOTIFICATIONS) ==
            android.content.pm.PackageManager.PERMISSION_GRANTED

    private val _resident = MutableStateFlow(false)
    val resident: StateFlow<Boolean> = _resident.asStateFlow()

    /** Resident from launch, like a terminal. */
    fun ensureResident() {
        runCatching { app.startForegroundService(Intent(app, WatchService::class.java)) }
        _resident.value = true
    }

    /** Explicit stop: ends the foreground service without killing the process. */
    fun stopResident() {
        app.stopService(Intent(app, WatchService::class.java))
        _resident.value = false
        TickerState.update { it.copy(watching = false, inCheck = false) }
    }

    /** The explicit Kill path: stop the service and end the process. */
    fun kill() {
        scope.launch {
            db.settings().put((db.settings().get() ?: DEFAULT_SETTINGS).copy(watching = false))
        }
        app.stopService(Intent(app, WatchService::class.java))
        Runtime.getRuntime().exit(0)
    }

    /** Cached jobs awaiting their retention window. */
    private val _jobCount = MutableStateFlow(0)
    val jobCount: Int get() = _jobCount.value

    init {
        scope.launch {
            watcher.log("app", "opened")
            // Kept live so the exit dialog cannot report a stale cache size.
            db.jobs().countSince(Parse.now() - 7 * 86_400L)
                .collect { _jobCount.value = it }
        }
    }
}