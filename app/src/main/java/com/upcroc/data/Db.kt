package com.upcroc.data

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface JobDao {
    /** -1 rowId means the id was already present, so that entry is not new. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIgnore(jobs: List<JobEntity>): List<Long>

    @Query("UPDATE job SET connectsCost=:connects, jobsPosted=:posted, hireRate=:hire, spendTotal=:spend, feedbackScore=:score, totalHired=:hired, invitesSent=:invites, category=:category, url=:url WHERE id=:id")
    suspend fun enrich(
        id: String, connects: String, posted: String, hire: String, spend: String,
        score: String, hired: String, invites: Int, category: String, url: String,
    )

    @Query("UPDATE job SET notifiedAt=:ts WHERE id=:id AND notifiedAt=0")
    suspend fun markNotified(id: String, ts: Long): Int

    @Query("SELECT COUNT(*) FROM job")
    suspend fun count(): Int

    @Query("SELECT COUNT(*) FROM job WHERE firstSeen >= :cutoff")
    fun countSince(cutoff: Long): Flow<Int>

    @Query("SELECT * FROM job WHERE firstSeen >= :cutoff ORDER BY publishedAt DESC LIMIT :limit")
    fun recent(cutoff: Long, limit: Int = 300): Flow<List<JobEntity>>

    /**
     * Jobs stored but never announced. A process death between the insert and
     * the notification would otherwise drop them permanently, since the insert
     * is also the dedupe marker.
     */
    @Query("SELECT * FROM job WHERE notifiedAt = 0 AND publishedAt >= :since ORDER BY publishedAt ASC")
    suspend fun unnotifiedSince(since: Long): List<JobEntity>

    @Query("DELETE FROM job WHERE firstSeen < :cutoff")
    suspend fun prune(cutoff: Long): Int
}

@Dao
interface EventDao {
    @Insert
    suspend fun add(event: EventEntity): Long

    @Query("SELECT * FROM event ORDER BY ts DESC, id DESC LIMIT :limit")
    fun latest(limit: Int = 500): Flow<List<EventEntity>>

    @Query("SELECT * FROM event ORDER BY ts DESC, id DESC LIMIT 1")
    fun newest(): Flow<EventEntity?>

    @Query("SELECT COUNT(*) FROM event WHERE ts >= :cutoff")
    fun countSince(cutoff: Long): Flow<Int>

    @Query("DELETE FROM event WHERE ts < :cutoff")
    suspend fun prune(cutoff: Long): Int
}

@Dao
interface QueryDao {
    @Query("SELECT * FROM query ORDER BY sortOrder, label")
    fun all(): Flow<List<QueryEntity>>

    @Query("SELECT * FROM query WHERE enabled = 1 ORDER BY sortOrder, label")
    suspend fun enabled(): List<QueryEntity>

    // Upsert, not Update: a source added from the UI is not in the table yet,
    // and Room's @Update would silently update zero rows.
    @Upsert
    suspend fun put(query: QueryEntity)

    @Query("DELETE FROM query WHERE id = :id")
    suspend fun delete(id: String)

    @Query("DELETE FROM query")
    suspend fun deleteAll()

    @Query("SELECT COUNT(*) FROM query")
    suspend fun count(): Int
}

@Dao
interface StateDao {
    @Query("SELECT value FROM state WHERE key = :key")
    suspend fun get(key: String): String?

    @Query("SELECT value FROM state WHERE key = :key")
    fun observe(key: String): Flow<String?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun set(state: StateEntity)

    @Query("SELECT value FROM state WHERE key = :key")
    suspend fun getLong(key: String, fallback: Long = 0): Long =
        get(key)?.toLongOrNull() ?: fallback

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun setLong(key: String, value: Long) = set(StateEntity(key, value.toString()))
}

@Dao
interface SettingsDao {
    @Query("SELECT * FROM settings WHERE id = 0")
    fun observe(): Flow<SettingsEntity?>

    @Query("SELECT * FROM settings WHERE id = 0")
    suspend fun get(): SettingsEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun put(settings: SettingsEntity)
}

@Database(
    entities = [
        JobEntity::class,
        EventEntity::class,
        QueryEntity::class,
        StateEntity::class,
        SettingsEntity::class,
    ],
    version = 1,
    exportSchema = false,
)
abstract class UpcrocDatabase : RoomDatabase() {
    abstract fun jobs(): JobDao
    abstract fun events(): EventDao
    abstract fun queries(): QueryDao
    abstract fun state(): StateDao
    abstract fun settings(): SettingsDao
}