package com.upcroc.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A job as seen by any query. `insertIgnore` returning -1 for an existing id is
 * the dedupe signal, so we never notify a job twice.
 */
@Entity(tableName = "job", indices = [Index("publishedAt"), Index("firstSeen")])
data class JobEntity(
    @PrimaryKey val id: String,
    val title: String = "",
    val url: String = "",
    val publishedAt: Long = 0,
    val pay: String = "",
    val country: String = "",
    val proposalsTier: String = "",
    val experienceLevel: String = "",
    val jobType: String = "",
    val firstSeen: Long = 0,
    val notifiedAt: Long = 0,
    // Facts only the `get` action returns. Empty until a job is enriched.
    val connectsCost: String = "",
    val jobsPosted: String = "",
    val hireRate: String = "",
    val spendTotal: String = "",
    val feedbackScore: String = "",
    val totalHired: String = "",
    val invitesSent: Int = 0,
    val category: String = "",
)

/** One row per meaningful state change, newest first. This is the history feed. */
@Entity(tableName = "event", indices = [Index("ts")])
data class EventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val ts: Long,
    val kind: String,
    val title: String,
    val detail: String = "",
    val jobId: String? = null,
)

/**
 * A search source. This table is the plug-in surface: rows can be added, edited,
 * reordered, enabled or deleted from the app and the watcher picks them up on
 * the next check with no code change.
 */
@Entity(tableName = "query")
data class QueryEntity(
    @PrimaryKey val id: String,
    val label: String,
    val enabled: Boolean = true,
    val action: String,
    val params: String,
    val sortOrder: Int = 0,
)

/** Scalar runtime state: the bucket counter, backoff, token expiry. */
@Entity(tableName = "state")
data class StateEntity(
    @PrimaryKey val key: String,
    val value: String = "",
)

/** Single-row settings table. Everything the user can tune lives here. */
@Entity(tableName = "settings")
data class SettingsEntity(
    @PrimaryKey val id: Int = 0,
    val watching: Boolean = false,
    val notify: Boolean = true,
    val notifySound: Boolean = true,
    val notifyVibrate: Boolean = true,
    val bucketBaseSec: Int = 90,
    val bucketJitterSec: Int = 150,
    val pageCap: Int = 8,
    val paceMs: Long = 120,
    val retentionDays: Int = 30,
    val freshWindowSec: Int = 300,
    val lateNotifySec: Int = 900,
    val sessionCapHours: Int = 4,
)