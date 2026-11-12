package app.upcroc.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface JobDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(jobs: List<JobEntity>): List<Long>

    @Query("SELECT * FROM jobs WHERE id = :id")
    suspend fun byId(id: String): JobEntity?

    @Query("SELECT * FROM jobs WHERE id IN (:ids)")
    suspend fun byIds(ids: List<String>): List<JobEntity>

    @Query("SELECT * FROM jobs WHERE hidden = 0 ORDER BY publishedAt DESC LIMIT :limit")
    fun observeFeed(limit: Int): Flow<List<JobEntity>>

    @Query("SELECT * FROM jobs WHERE saved = 1 AND hidden = 0 ORDER BY publishedAt DESC LIMIT :limit")
    fun observeSaved(limit: Int): Flow<List<JobEntity>>

    @Query("SELECT * FROM jobs WHERE notifiedAt IS NOT NULL ORDER BY notifiedAt DESC LIMIT :limit")
    suspend fun recentNotified(limit: Int): List<JobEntity>

    @Query("SELECT * FROM jobs WHERE publishedAt >= :since ORDER BY publishedAt DESC LIMIT :limit")
    suspend fun since(since: Long, limit: Int): List<JobEntity>

    @Query("SELECT COUNT(*) FROM jobs")
    suspend fun count(): Int

    @Query("SELECT MAX(publishedAt) FROM jobs")
    suspend fun latestPublishedAt(): Long?

    @Query("UPDATE jobs SET notifiedAt = :at, notifiedFresh = :fresh WHERE id = :id")
    suspend fun markNotified(id: String, at: Long, fresh: Boolean)

    @Query("UPDATE jobs SET saved = :saved WHERE id = :id")
    suspend fun setSaved(id: String, saved: Boolean)

    @Query("UPDATE jobs SET hidden = :hidden WHERE id = :id")
    suspend fun setHidden(id: String, hidden: Boolean)

    @Update
    suspend fun update(job: JobEntity)

    @Query("DELETE FROM jobs WHERE firstSeenAt < :cutoff")
    suspend fun deleteOlderThan(cutoff: Long)

    @Query("SELECT * FROM jobs ORDER BY publishedAt DESC LIMIT :limit")
    suspend fun recent(limit: Int): List<JobEntity>
}

@Dao
interface OutboxDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(item: OutboxEntity)

    @Query("SELECT * FROM notification_outbox WHERE state = 'PENDING' ORDER BY createdAt LIMIT :limit")
    suspend fun pending(limit: Int): List<OutboxEntity>

    @Query("UPDATE notification_outbox SET state = 'POSTED', postedAt = :at WHERE jobId = :id")
    suspend fun markPosted(id: String, at: Long)

    @Query("UPDATE notification_outbox SET state = 'FAILED', error = :error, attempts = attempts + 1 WHERE jobId = :id")
    suspend fun markFailed(id: String, error: String)

    @Query("DELETE FROM notification_outbox WHERE state != 'PENDING' AND createdAt < :cutoff")
    suspend fun prune(cutoff: Long)
}

@Dao
interface CheckDao {
    @Insert
    suspend fun insert(check: CheckEntity)

    @Query("SELECT * FROM checks ORDER BY startedAt DESC LIMIT :limit")
    fun observe(limit: Int): Flow<List<CheckEntity>>

    @Query("SELECT * FROM checks WHERE startedAt >= :since ORDER BY startedAt DESC")
    suspend fun since(since: Long): List<CheckEntity>

    @Query("SELECT COUNT(*) FROM checks WHERE startedAt >= :since")
    suspend fun countSince(since: Long): Int

    @Query("DELETE FROM checks WHERE startedAt < :cutoff")
    suspend fun deleteOlderThan(cutoff: Long)
}

@Dao
interface EngineStateDao {
    @Query("SELECT value FROM engine_state WHERE `key` = :key")
    suspend fun get(key: String): String?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun put(entity: EngineStateEntity)

    @Query("DELETE FROM engine_state WHERE `key` = :key")
    suspend fun remove(key: String)

    @Query("SELECT * FROM engine_state")
    suspend fun all(): List<EngineStateEntity>
}
