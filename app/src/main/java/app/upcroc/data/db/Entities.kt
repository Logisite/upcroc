package app.upcroc.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import app.upcroc.core.model.Job
import app.upcroc.core.util.CoreJson
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.JsonObject

@Entity(
    tableName = "jobs",
    indices = [
        Index("publishedAt"),
        Index("firstSeenAt"),
        Index("saved"),
        Index("hidden"),
    ],
)
data class JobEntity(
    @PrimaryKey val id: String,
    val title: String,
    val url: String,
    val publishedDate: String,
    val publishedAt: Long,
    val budget: String,
    val jobType: String,
    val proposalsTier: String,
    val experienceLevel: String,
    val country: String,
    val skillsJson: String,
    val description: String,
    val category: String,
    val firstSeenAt: Long,
    val notifiedAt: Long?,
    val notifiedFresh: Boolean,
    val saved: Boolean,
    val hidden: Boolean,
    val connectsCost: String?,
    val jobsPosted: Int?,
    val hireRate: Int?,
    val spendTotal: Double?,
    val feedbackScore: Double?,
    val totalHired: Int?,
    val invitesSent: Int?,
    val invitedToInterview: Int?,
    val extraJson: String?,
)

fun Job.toEntity(): JobEntity = JobEntity(
    id = id,
    title = title,
    url = url,
    publishedDate = publishedDate,
    publishedAt = publishedAt,
    budget = budget,
    jobType = jobType,
    proposalsTier = proposalsTier,
    experienceLevel = experienceLevel,
    country = country,
    skillsJson = CoreJson.encodeToString(ListSerializer(String.serializer()), skills),
    description = description,
    category = category,
    firstSeenAt = firstSeenAt,
    notifiedAt = notifiedAt,
    notifiedFresh = notifiedFresh,
    saved = saved,
    hidden = hidden,
    connectsCost = connectsCost,
    jobsPosted = jobsPosted,
    hireRate = hireRate,
    spendTotal = spendTotal,
    feedbackScore = feedbackScore,
    totalHired = totalHired,
    invitesSent = invitesSent,
    invitedToInterview = invitedToInterview,
    extraJson = extra?.let { CoreJson.encodeToString(JsonObject.serializer(), it) },
)

fun JobEntity.toModel(): Job = Job(
    id = id,
    title = title,
    url = url,
    publishedDate = publishedDate,
    publishedAt = publishedAt,
    budget = budget,
    jobType = jobType,
    proposalsTier = proposalsTier,
    experienceLevel = experienceLevel,
    country = country,
    skills = runCatching {
        CoreJson.decodeFromString(ListSerializer(String.serializer()), skillsJson)
    }.getOrElse { emptyList() },
    description = description,
    category = category,
    firstSeenAt = firstSeenAt,
    notifiedAt = notifiedAt,
    notifiedFresh = notifiedFresh,
    saved = saved,
    hidden = hidden,
    connectsCost = connectsCost,
    jobsPosted = jobsPosted,
    hireRate = hireRate,
    spendTotal = spendTotal,
    feedbackScore = feedbackScore,
    totalHired = totalHired,
    invitesSent = invitesSent,
    invitedToInterview = invitedToInterview,
    extra = extraJson?.let {
        runCatching { CoreJson.decodeFromString(JsonObject.serializer(), it) }.getOrNull()
    },
)

@Entity(tableName = "notification_outbox")
data class OutboxEntity(
    @PrimaryKey val jobId: String,
    val groupId: String,
    val createdAt: Long,
    val attempts: Int = 0,
    val state: String = "PENDING",
    val postedAt: Long? = null,
    val error: String? = null,
)

@Entity(tableName = "checks")
data class CheckEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val startedAt: Long,
    val finishedAt: Long,
    val groupId: String,
    val status: String,
    val pages: Int,
    val requests: Int,
    val newJobs: Int,
    val notified: Int,
    val durationMs: Long,
    val error: String?,
)

@Entity(tableName = "engine_state")
data class EngineStateEntity(
    @PrimaryKey val key: String,
    val value: String,
    val updatedAt: Long,
)
