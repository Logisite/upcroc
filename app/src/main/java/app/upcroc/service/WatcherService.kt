package app.upcroc.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.content.ContextCompat
import app.upcroc.UpcrocApp
import app.upcroc.core.engine.RunStatus
import app.upcroc.core.engine.StateKeys
import app.upcroc.core.platform.WatcherStatus
import app.upcroc.notify.NotificationChannels
import app.upcroc.runtime.CheckResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import java.util.concurrent.atomic.AtomicBoolean

class WatcherService : Service() {
    private val container by lazy { (application as UpcrocApp).container }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val checking = AtomicBoolean(false)
    private var heartbeatJob: Job? = null
    private var userRunning = false
    private var stopping = false

    override fun onCreate() {
        super.onCreate()
        NotificationChannels.ensureStatus(this)
        container.runtime.setRunning(true)
        container.logger.info("service: created")
        startForegroundCompat(
            container.notifier.statusNotification(
                WatcherStatus(running = true, lastTickAt = System.currentTimeMillis())
            )
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        container.logger.info("service: action=${action ?: "null"}")
        when (action) {
            ACTION_STOP -> stopEverything()
            ACTION_START, ACTION_CHECK, ACTION_CHECK_NOW, ACTION_ENSURE, ACTION_WATCHDOG, ACTION_RESTART -> {
                scope.launch {
                    val wasRunning = container.settings.runningFlow.first()
                    if (action == ACTION_START) {
                        userRunning = true
                        container.settings.setRunning(true)
                        container.runtime.setRunning(true)
                    } else if (!wasRunning) {
                        userRunning = false
                        stopSelfSafely()
                        return@launch
                    } else {
                        userRunning = true
                    }

                    if (action == ACTION_WATCHDOG) {
                        if (!container.settings.configOnce().advanced.watchdogEnabled) {
                            container.logger.info("service: watchdog disabled")
                            return@launch
                        }
                        val last = container.engineState.long(StateKeys.LAST_TICK)
                        if (System.currentTimeMillis() - last > STALE_MS) {
                            container.logger.warn("service: watchdog found stale engine; checking")
                            runCheck(force = false, source = "watchdog")
                        } else {
                            container.logger.info("service: watchdog healthy")
                            container.scheduler.scheduleWatchdog()
                            postStatus()
                        }
                        return@launch
                    }

                    startHeartbeat()
                    if (container.settings.configOnce().advanced.watchdogEnabled) {
                        container.scheduler.scheduleWatchdog()
                    }
                    BackstopScheduler.schedule(this@WatcherService)
                    val source = when (action) {
                        ACTION_CHECK_NOW -> {
                            if (intent?.getBooleanExtra(EXTRA_AUTO, false) == true) "auto" else "manual"
                        }
                        ACTION_START -> "manual"
                        ACTION_WATCHDOG -> "watchdog"
                        ACTION_ENSURE -> "ensure"
                        ACTION_RESTART -> "restart"
                        else -> "scheduled"
                    }
                    runCheck(force = action == ACTION_START || action == ACTION_CHECK_NOW, source = source)
                }
            }
        }
        return START_STICKY
    }

    private suspend fun runCheck(force: Boolean, source: String) {
        if (!checking.compareAndSet(false, true)) {
            container.logger.warn("service: check skipped; another check is running")
            return
        }
        container.logger.info("service: check starting source=$source force=$force")
        container.runtime.setChecking(true)
        val wake = runCatching {
            getSystemService(PowerManager::class.java)
                .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "upcroc:check")
                .apply {
                    setReferenceCounted(false)
                    acquire(CHECK_WAKE_MS)
                }
        }.getOrNull()
        try {
            val result = withTimeout(CHECK_TIMEOUT_MS) {
                container.engine.resumePendingNotifications()
                container.engine.runDue(force = force)
            }
            val snapshot = container.engine.snapshot()
            val now = System.currentTimeMillis()
            container.engineState.putLong(StateKeys.LAST_TICK, now)
            val status = WatcherStatus(
                running = true,
                checking = false,
                lastCheckAt = snapshot.lastCheckAt.takeIf { it > 0L },
                nextCheckAt = result.nextDueAt,
                lastTickAt = now,
                failures = snapshot.failures,
                lastError = snapshot.lastError,
                requestsToday = snapshot.budget.dayCount,
            )
            container.runtime.updateStatus { status }
            container.scheduler.scheduleNext(result.nextDueAt)
            val config = container.settings.configOnce()
            if (config.advanced.watchdogEnabled) {
                container.scheduler.scheduleWatchdog()
            }
            container.notifier.postStatus(status)
            container.jobRepository.prune(
                now = now,
                retentionDays = config.storage.retentionDays,
                statsRetentionDays = config.storage.statsRetentionDays,
            )
            if (result.status != RunStatus.NO_DUE) {
                container.runtime.publishResult(
                    CheckResult(
                        at = now,
                        source = source,
                        status = result.status.name,
                        requests = result.requests,
                        newJobs = result.newJobs,
                        notified = result.notified,
                        message = result.message,
                    ),
                )
            }
            container.logger.info(
                "service: check done source=$source status=${result.status} dueAt=${result.nextDueAt} " +
                    "requests=${result.requests} new=${result.newJobs} notified=${result.notified}" +
                    (result.message?.let { " msg=$it" } ?: "")
            )
        } catch (e: Exception) {
            val now = System.currentTimeMillis()
            val next = now + 60_000L
            container.logger.error("service: check failed source=$source", e)
            container.runtime.updateStatus {
                it.copy(
                    checking = false,
                    lastTickAt = now,
                    nextCheckAt = next,
                    failures = it.failures + 1,
                    lastError = e.message ?: e::class.java.simpleName,
                )
            }
            container.scheduler.scheduleNext(next)
            runCatching { container.notifier.postStatus(container.runtime.status.value) }
            container.runtime.publishResult(
                CheckResult(
                    at = now,
                    source = source,
                    status = "ERROR",
                    requests = 0,
                    newJobs = 0,
                    notified = 0,
                    message = e.message ?: e::class.java.simpleName,
                ),
            )
        } finally {
            runCatching { if (wake?.isHeld == true) wake.release() }
            container.runtime.setChecking(false)
            checking.set(false)
        }
    }

    private fun startHeartbeat() {
        if (heartbeatJob?.isActive == true) return
        heartbeatJob = scope.launch {
            while (isActive) {
                val now = System.currentTimeMillis()
                container.engineState.putLong(StateKeys.LAST_TICK, now)
                container.runtime.updateStatus { it.copy(lastTickAt = now, running = true) }
                runCatching {
                    container.notifier.postStatus(container.runtime.status.value)
                }
                delay(HEARTBEAT_MS)
            }
        }
    }

    private suspend fun postStatus() {
        container.notifier.postStatus(container.runtime.status.value)
    }

    private fun stopEverything() {
        stopping = true
        userRunning = false
        container.logger.info("service: stop requested")
        container.runtime.setRunning(false)
        container.scheduler.cancelAll()
        BackstopScheduler.cancel(this)
        container.scope.launch {
            container.settings.setRunning(false)
            container.notifier.cancelStatus()
        }
        runCatching { stopForeground(STOP_FOREGROUND_REMOVE) }
        stopSelf()
    }

    private fun stopSelfSafely() {
        runCatching { stopForeground(STOP_FOREGROUND_REMOVE) }
        stopSelf()
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        if (userRunning) {
            container.logger.info("service: task removed; scheduling restart")
            container.scheduler.scheduleRestartSoon()
        }
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        container.logger.info("service: destroyed stopping=$stopping userRunning=$userRunning")
        heartbeatJob?.cancel()
        scope.cancel()
        if (!stopping && userRunning) {
            container.scheduler.scheduleRestartSoon()
        }
        container.runtime.setRunning(false)
        super.onDestroy()
    }

    override fun onTimeout(startId: Int, fgsType: Int) {
        stopEverything()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun startForegroundCompat(notification: android.app.Notification) {
        when {
            Build.VERSION.SDK_INT >= 34 -> startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
            )
            Build.VERSION.SDK_INT >= 29 -> startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
            )
            else -> startForeground(NOTIFICATION_ID, notification)
        }
    }

    companion object {
        const val ACTION_START = "app.upcroc.action.START"
        const val ACTION_STOP = "app.upcroc.action.STOP"
        const val ACTION_CHECK = "app.upcroc.action.CHECK"
        const val ACTION_CHECK_NOW = "app.upcroc.action.CHECK_NOW"
        const val ACTION_WATCHDOG = "app.upcroc.action.WATCHDOG"
        const val ACTION_RESTART = "app.upcroc.action.RESTART"
        const val ACTION_ENSURE = "app.upcroc.action.ENSURE"
        const val EXTRA_AUTO = "app.upcroc.extra.AUTO"

        private const val NOTIFICATION_ID = 1000
        private const val HEARTBEAT_MS = 60_000L
        private const val STALE_MS = 5 * 60_000L
        private const val CHECK_WAKE_MS = 120_000L
        private const val CHECK_TIMEOUT_MS = 90_000L

        fun start(context: Context) {
            ContextCompat.startForegroundService(
                context,
                Intent(context, WatcherService::class.java).setAction(ACTION_START),
            )
        }

        /** Starts the service without forcing an extra check. */
        fun ensure(context: Context) {
            ContextCompat.startForegroundService(
                context,
                Intent(context, WatcherService::class.java).setAction(ACTION_ENSURE),
            )
        }

        fun stop(context: Context) {
            context.startService(Intent(context, WatcherService::class.java).setAction(ACTION_STOP))
        }

        fun checkNow(context: Context, auto: Boolean = false) {
            ContextCompat.startForegroundService(
                context,
                Intent(context, WatcherService::class.java)
                    .setAction(ACTION_CHECK_NOW)
                    .putExtra(EXTRA_AUTO, auto),
            )
        }
    }
}
