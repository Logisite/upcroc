package com.upcroc

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.ServiceCompat
import com.upcroc.core.Notifier
import com.upcroc.core.TickerState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Keeps the app alive, the way a terminal does: resident until the user hides
 * it or kills it, rather than living only while a screen is on top.
 *
 * Both loops are supervised. An engine that throws once would otherwise leave a
 * foreground notification claiming "waiting" while nothing was polling.
 */
class WatchService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var engine: Job? = null
    private var tickerNotification: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        val container = (application as UpcrocApp).container
        startForegroundCompat(container.notifier.ongoing(TickerState.live.value))
        startEngine()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_EXIT) {
            stopSelf()
            return START_NOT_STICKY
        }
        startEngine()
        // Restart if the system reclaims us: the app is meant to stay alive.
        return START_STICKY
    }

    private fun startEngine() {
        val container = (application as UpcrocApp).container

        if (engine?.isActive != true) {
            engine = scope.launch {
                while (isActive) {
                    try {
                        container.watcher.loop()
                        break
                    } catch (c: CancellationException) {
                        throw c
                    } catch (t: Throwable) {
                        container.watcher.log("error", "engine stopped: ${t.message}")
                        delay(10_000)
                    }
                }
            }
        }
        if (tickerNotification?.isActive != true) {
            tickerNotification = scope.launch {
                val manager = getSystemService(NotificationManager::class.java)
                var lastText: String? = null
                var lastPost = 0L
                while (isActive) {
                    // Only re-post when the text actually changed, and never
                    // more than once every few seconds. Posting every tick made
                    // some Android skins re-alert the notification while the app
                    // sat in the background.
                    val ticker = TickerState.live.value
                    val state = if (ticker.inCheck) "checking"
                    else if (ticker.watching) "watching" else "idle"
                    val text = "${ticker.bucketLeft / 60}m ${ticker.bucketLeft % 60}s " +
                        "$state ${ticker.lastNew} ${ticker.lastNotified}"
                    val now = System.currentTimeMillis()
                    if (text != lastText && now - lastPost > NOTIFY_MIN_GAP_MS) {
                        manager.notify(
                            Notifier.ONGOING_ID,
                            container.notifier.ongoing(ticker),
                        )
                        lastText = text
                        lastPost = now
                    }
                    delay(2_000)
                }
            }
        }
    }

    /**
     * Android 15+ caps dataSync foreground work and then calls this. The app is
     * meant to be resident, so log it and let the restart path pick it back up
     * rather than silently going quiet.
     */
    override fun onTimeout(startId: Int, fgsType: Int) {
        super.onTimeout(startId, fgsType)
        val watcher = (application as UpcrocApp).container.watcher
        scope.launch { watcher.log("service", "foreground budget reached, restarting") }
        stopSelf(startId)
    }

    override fun onDestroy() {
        (application as UpcrocApp).container.watcher.shutdown()
        scope.cancel()
        super.onDestroy()
    }

    private fun startForegroundCompat(notification: Notification) {
        // startForeground can throw (background-start restrictions, an invalid
        // type). Letting that escape onCreate would crash the process.
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                ServiceCompat.startForeground(
                    this,
                    Notifier.ONGOING_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC or
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
                )
            } else {
                startForeground(Notifier.ONGOING_ID, notification)
            }
        }.onFailure { stopSelf() }
    }

companion object {
        const val ACTION_EXIT = "com.upcroc.EXIT"
        private const val NOTIFY_MIN_GAP_MS = 15_000L
    }
}

/** Brings the watcher back after a reboot or an update, if it was left running. */
class BootReceiver : android.content.BroadcastReceiver() {
    override fun onReceive(context: android.content.Context, intent: Intent) {
        val action = intent.action ?: return
        if (action != Intent.ACTION_BOOT_COMPLETED && action != Intent.ACTION_MY_PACKAGE_REPLACED) {
            return
        }
        runCatching {
            context.startForegroundService(Intent(context, WatchService::class.java))
        }
    }
}