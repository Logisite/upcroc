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

class BackstopWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val container = (applicationContext as UpcrocApp).container
        if (!container.settings.runningFlow.first()) return Result.success()
        if (container.runtime.running.value) return Result.success()
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
