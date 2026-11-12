package app.upcroc.service

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import app.upcroc.UpcrocApp
import app.upcroc.core.engine.StateKeys
import app.upcroc.core.platform.WatcherStatus
import kotlinx.coroutines.flow.first

/**
 * Last-resort check when Android refuses to start the foreground service from
 * the alarm receiver. WorkManager can still run this expeditiously.
 */
class OneTimeCheckWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val container = (applicationContext as UpcrocApp).container
        if (!container.settings.runningFlow.first()) return Result.success()
        return runCatching {
            val result = container.engine.runDue(force = true)
            val snapshot = container.engine.snapshot()
            val now = System.currentTimeMillis()
            container.engineState.putLong(StateKeys.LAST_TICK, now)
            container.scheduler.scheduleNext(result.nextDueAt)
            container.notifier.postStatus(
                WatcherStatus(
                    running = true,
                    lastCheckAt = snapshot.lastCheckAt,
                    nextCheckAt = result.nextDueAt,
                    lastTickAt = now,
                    failures = snapshot.failures,
                    lastError = snapshot.lastError,
                    requestsToday = snapshot.budget.dayCount,
                ),
            )
            Result.success()
        }.getOrElse { Result.retry() }
    }

    companion object {
        private const val NAME = "upcroc-one-check"

        fun enqueue(context: Context) {
            val request = OneTimeWorkRequestBuilder<OneTimeCheckWorker>()
                .setConstraints(
                    Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build(),
                )
                .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(
                NAME,
                ExistingWorkPolicy.REPLACE,
                request,
            )
        }
    }
}
