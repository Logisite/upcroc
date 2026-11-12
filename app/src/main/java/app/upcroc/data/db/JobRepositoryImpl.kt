package app.upcroc.data.db

import androidx.room.withTransaction
import app.upcroc.core.model.CheckRecord
import app.upcroc.core.model.ClientStats
import app.upcroc.core.model.Job
import app.upcroc.core.model.OutboxItem
import app.upcroc.core.platform.EngineStateStore
import app.upcroc.core.platform.InsertResult
import app.upcroc.core.platform.JobStore

class JobRepositoryImpl(private val db: UpcrocDatabase) : JobStore {
    override suspend fun insertAndEnqueue(
        jobs: List<Job>,
        groupId: String,
        now: Long,
        notifyIf: (Job) -> Boolean,
    ): InsertResult = db.withTransaction {
        if (jobs.isEmpty()) return@withTransaction InsertResult(emptyList(), emptyList())
        val entities = jobs.map { it.copy(firstSeenAt = now).toEntity() }
        val rowIds = db.jobDao().insertAll(entities)
        val newJobs = entities.zip(rowIds)
            .filter { (_, rowId) -> rowId != -1L }
            .map { (entity, _) -> entity.toModel() }
        val enqueued = newJobs.filter(notifyIf)
        enqueued.forEach { job ->
            db.outboxDao().insert(
                OutboxEntity(
                    jobId = job.id,
                    groupId = groupId,
                    createdAt = now,
                )
            )
        }
        InsertResult(newJobs = newJobs, enqueued = enqueued)
    }

    override suspend fun pendingOutbox(limit: Int): List<OutboxItem> {
        val pending = db.outboxDao().pending(limit)
        if (pending.isEmpty()) return emptyList()
        val jobs = db.jobDao().byIds(pending.map { it.jobId }).associateBy { it.id }
        return pending.mapNotNull { item ->
            val job = jobs[item.jobId] ?: return@mapNotNull null
            OutboxItem(
                job = job.toModel(),
                groupId = item.groupId,
                createdAt = item.createdAt,
                attempts = item.attempts,
            )
        }
    }

    override suspend fun markOutboxPosted(jobId: String, at: Long) {
        db.outboxDao().markPosted(jobId, at)
    }

    override suspend fun markOutboxFailed(jobId: String, error: String) {
        db.outboxDao().markFailed(jobId, error.take(300))
    }

    override suspend fun markNotified(jobIds: List<String>, freshIds: Set<String>, at: Long) {
        if (jobIds.isEmpty()) return
        db.withTransaction {
            jobIds.forEach { id ->
                db.jobDao().markNotified(id, at, id in freshIds)
            }
        }
    }

    override suspend fun updateEnrichment(jobId: String, stats: ClientStats) {
        val entity = db.jobDao().byId(jobId) ?: return
        db.jobDao().update(
            entity.copy(
                url = stats.url?.takeIf { it.isNotBlank() } ?: entity.url,
                connectsCost = stats.connectsCost ?: entity.connectsCost,
                jobsPosted = stats.jobsPosted ?: entity.jobsPosted,
                hireRate = stats.hireRate ?: entity.hireRate,
                spendTotal = stats.spendTotal ?: entity.spendTotal,
                feedbackScore = stats.feedbackScore ?: entity.feedbackScore,
                totalHired = stats.totalHired ?: entity.totalHired,
                invitesSent = stats.invitesSent ?: entity.invitesSent,
                invitedToInterview = stats.invitedToInterview ?: entity.invitedToInterview,
                category = stats.category?.takeIf { it.isNotBlank() } ?: entity.category,
            )
        )
    }

    override suspend fun recordCheck(record: CheckRecord) {
        db.checkDao().insert(
            CheckEntity(
                startedAt = record.startedAt,
                finishedAt = record.finishedAt,
                groupId = record.groupId,
                status = record.status,
                pages = record.pages,
                requests = record.requests,
                newJobs = record.newJobs,
                notified = record.notified,
                durationMs = record.durationMs,
                error = record.error,
            )
        )
    }

    override suspend fun prune(now: Long, retentionDays: Int, statsRetentionDays: Int) {
        db.withTransaction {
            db.jobDao().deleteOlderThan(now - retentionDays * 86_400_000L)
            db.checkDao().deleteOlderThan(now - statsRetentionDays * 86_400_000L)
            db.outboxDao().prune(now - 7 * 86_400_000L)
        }
    }

    override suspend fun latestPublishedAt(): Long = db.jobDao().latestPublishedAt() ?: 0L

    override suspend fun jobCount(): Int = db.jobDao().count()
}

class EngineStateStoreImpl(private val db: UpcrocDatabase) : EngineStateStore {
    override suspend fun get(key: String): String? = db.engineStateDao().get(key)

    override suspend fun put(key: String, value: String) {
        db.engineStateDao().put(
            EngineStateEntity(
                key = key,
                value = value,
                updatedAt = System.currentTimeMillis(),
            )
        )
    }

    override suspend fun remove(key: String) {
        db.engineStateDao().remove(key)
    }
}
