package app.upcroc.core.platform

import app.upcroc.core.model.CheckRecord
import app.upcroc.core.model.ClientStats
import app.upcroc.core.model.Job
import app.upcroc.core.model.OutboxItem
import app.upcroc.core.model.SearchGroup

interface Clock {
    fun now(): Long
}

object SystemClock : Clock {
    override fun now(): Long = System.currentTimeMillis()
}

enum class LogLevel { DEBUG, INFO, WARN, ERROR }

interface Logger {
    fun log(level: LogLevel, message: String, throwable: Throwable? = null)

    fun debug(message: String) = log(LogLevel.DEBUG, message)
    fun info(message: String) = log(LogLevel.INFO, message)
    fun warn(message: String, throwable: Throwable? = null) = log(LogLevel.WARN, message, throwable)
    fun error(message: String, throwable: Throwable? = null) = log(LogLevel.ERROR, message, throwable)
}

object NoopLogger : Logger {
    override fun log(level: LogLevel, message: String, throwable: Throwable?) = Unit
}

interface TokenProvider {
    suspend fun accessToken(forceRefresh: Boolean = false): String
    suspend fun invalidate()
}

interface JobStore {
    /** Insert jobs and enqueue notifications for the ones accepted by [notifyIf], in one transaction. */
    suspend fun insertAndEnqueue(
        jobs: List<Job>,
        groupId: String,
        now: Long,
        notifyIf: (Job) -> Boolean,
    ): InsertResult

    suspend fun pendingOutbox(limit: Int = 50): List<OutboxItem>
    suspend fun markOutboxPosted(jobId: String, at: Long)
    suspend fun markOutboxFailed(jobId: String, error: String)
    suspend fun markNotified(jobIds: List<String>, freshIds: Set<String>, at: Long)
    suspend fun updateEnrichment(jobId: String, stats: ClientStats)
    suspend fun recordCheck(record: CheckRecord)
    suspend fun prune(now: Long, retentionDays: Int, statsRetentionDays: Int)
    suspend fun latestPublishedAt(): Long
    suspend fun jobCount(): Int
}

data class InsertResult(
    val newJobs: List<Job>,
    val enqueued: List<Job>,
)

interface EngineStateStore {
    suspend fun get(key: String): String?
    suspend fun put(key: String, value: String)
    suspend fun remove(key: String)
}

interface Notifier {
    suspend fun postJob(job: Job, group: SearchGroup?)
    suspend fun updateJob(job: Job, group: SearchGroup?)
    suspend fun postSummary(jobs: List<Job>, group: SearchGroup?)
    suspend fun postAuthNeeded()
    suspend fun postStatus(status: WatcherStatus)
    suspend fun cancelStatus()
}

data class WatcherStatus(
    val running: Boolean = false,
    val checking: Boolean = false,
    val lastCheckAt: Long? = null,
    val nextCheckAt: Long? = null,
    val lastTickAt: Long? = null,
    val failures: Int = 0,
    val lastError: String? = null,
    val checksToday: Int = 0,
    val requestsToday: Int = 0,
    val doze: Boolean = false,
    val activeGroup: String? = null,
)

interface NetworkMonitor {
    fun isOnline(): Boolean
}
