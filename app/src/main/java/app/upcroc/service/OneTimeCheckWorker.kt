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
import app.upcroc.core.engine.RunStatus
import app.upcroc.core.engine.StateKeys
import app.upcroc.core.platform.WatcherStatus
import app.upcroc.runtime.CheckResult
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
        if (!container.auth.hasToken()) {
            container.logger.info("worker: one-time check skipped; no account")
            return Result.success()
        }
        container.logger.info("worker: one-time check starting")
        container.runtime.setChecking(true)
        return runCatching {
            val result = container.engine.runDue(force = true)
            val snapshot = container.engine.snapshot()
            val now = System.currentTimeMillis()
            container.engineState.putLong(StateKeys.LAST_TICK, now)
            container.scheduler.scheduleNext(result.nextDueAt)
            container.notifier.postStatus(
                WatcherStatus(
                    running = true,
                    checking = false,
                    lastCheckAt = snapshot.lastCheckAt.takeIf { it > 0L },
                    nextCheckAt = result.nextDueAt,
                    lastTickAt = now,
                    failures = snapshot.failures,
                    lastError = snapshot.lastError,
                    requestsToday = snapshot.budget.dayCount,
                ),
            )
            if (result.status != RunStatus.NO_DUE) {
                container.runtime.publishResult(
                    CheckResult(
                        at = now,
                        source = "worker",
                        status = result.status.name,
                        requests = result.requests,
                        newJobs = result.newJobs,
                        notified = result.notified,
                        message = result.message,
                    ),
                )
            }
            container.logger.info(
                "worker: check done status=${result.status} new=${result.newJobs} notified=${result.notified}",
            )
            Result.success()
        }.getOrElse { error ->
            container.logger.error("worker: check failed", error)
            container.runtime.publishResult(
                CheckResult(
                    at = System.currentTimeMillis(),
                    source = "worker",
                    status = "ERROR",
                    requests = 0,
                    newJobs = 0,
                    notified = 0,
                    message = error.message ?: error::class.java.simpleName,
                ),
            )
            Result.retry()
        }.also {
            container.runtime.setChecking(false)
        }
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
