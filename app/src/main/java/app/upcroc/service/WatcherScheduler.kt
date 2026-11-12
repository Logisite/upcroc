package app.upcroc.service

import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import app.upcroc.core.platform.Logger
import app.upcroc.runtime.WatcherRuntime

class WatcherScheduler(
    private val context: Context,
    private val runtime: WatcherRuntime,
    private val logger: Logger,
) {
    private val alarmManager = context.getSystemService(AlarmManager::class.java)

    fun scheduleNext(atMillis: Long) {
        val whenAt = atMillis.coerceAtLeast(System.currentTimeMillis() + 1000L)
        setAlarm(whenAt, WatcherService.ACTION_CHECK, REQ_CHECK)
        runtime.setNextAlarm(whenAt)
    }

    fun scheduleWatchdog() {
        setAlarm(System.currentTimeMillis() + WATCHDOG_MS, WatcherService.ACTION_WATCHDOG, REQ_WATCHDOG)
    }

    fun scheduleRestartSoon() {
        setAlarm(System.currentTimeMillis() + 5_000L, WatcherService.ACTION_START, REQ_RESTART)
    }

    fun cancelAll() {
        listOf(REQ_CHECK, REQ_WATCHDOG, REQ_RESTART).forEach { req ->
            alarmManager.cancel(pendingIntent(req))
        }
        runtime.setNextAlarm(0L)
    }

    fun canScheduleExact(): Boolean =
        if (android.os.Build.VERSION.SDK_INT >= 31) alarmManager.canScheduleExactAlarms() else true

    @SuppressLint("ScheduleExactAlarm")
    private fun setAlarm(atMillis: Long, action: String, requestCode: Int) {
        val pi = pendingIntent(requestCode, action)
        runCatching {
            if (canScheduleExact()) {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMillis, pi)
            } else {
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMillis, pi)
                logger.warn("exact alarms not permitted; using inexact alarm")
            }
        }.onFailure { logger.error("failed to schedule alarm", it) }
    }

    private fun pendingIntent(requestCode: Int, action: String? = null): PendingIntent {
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            if (action != null) this.action = action
        }
        return PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private companion object {
        const val REQ_CHECK = 2001
        const val REQ_WATCHDOG = 2002
        const val REQ_RESTART = 2003
        const val WATCHDOG_MS = 15 * 60_000L
    }
}
