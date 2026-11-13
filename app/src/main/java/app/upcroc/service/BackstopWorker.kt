package app.upcroc.service

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.core.content.ContextCompat
import android.content.Intent
import app.upcroc.UpcrocApp
import kotlinx.coroutines.flow.first
import java.util.concurrent.TimeUnit

private const val STALE_MS = 4 * 60_000L

class BackstopWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val container = (applicationContext as UpcrocApp).container
        if (!container.settings.runningFlow.first()) return Result.success()
        if (!container.runtime.running.value) {
            return try {
                ContextCompat.startForegroundService(
                    applicationContext,
                    Intent(applicationContext, WatcherService::class.java).setAction(WatcherService.ACTION_START),
                )
                Result.success()
            } catch (_: Exception) {
                // If the OS refuses an FGS start, run one check inside the worker instead.
                runCatching { container.engine.runDue() }
                Result.success()
            }
        }
        // The service is alive but the alarm may have been deferred or dropped.
        val status = container.runtime.status.value
        val now = System.currentTimeMillis()
        val next = status.nextCheckAt ?: 0L
        val overdue = next > 0L && now >= next + STALE_MS
        val quietForLong = status.lastCheckAt?.let { now - it > STALE_MS } ?: true
        if (overdue && quietForLong) {
            container.logger.warn("worker: backstop found a late check")
            OneTimeCheckWorker.enqueue(applicationContext)
        }
        return Result.success()
    }
}

object BackstopScheduler {
    private const val NAME = "upcroc-backstop"

    fun schedule(context: Context) {
        val request = PeriodicWorkRequestBuilder<BackstopWorker>(15, TimeUnit.MINUTES)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            request,
        )
    }

    fun cancel(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(NAME)
    }
}
