package app.upcroc.core.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.time.Instant
import java.time.format.DateTimeParseException

@Serializable
data class SearchClient(
    val country: String = "",
    val city: String = "",
    val totalSpent: Double? = null,
    val hires: Int? = null,
    val hireRate: Int? = null,
    val feedbackScore: Double? = null,
    val jobsPosted: Int? = null,
)

@Serializable
data class Job(
    val id: String,
    val title: String = "",
    val url: String = "",
    val publishedDate: String = "",
    val publishedAt: Long = 0L,
    val budget: String = "",
    val jobType: String = "",
    val proposalsTier: String = "",
    val experienceLevel: String = "",
    val country: String = "",
    val skills: List<String> = emptyList(),
    val description: String = "",
    val category: String = "",
    val firstSeenAt: Long = 0L,
    val notifiedAt: Long? = null,
    val notifiedFresh: Boolean = false,
    val saved: Boolean = false,
    val hidden: Boolean = false,
    val connectsCost: String? = null,
    val jobsPosted: Int? = null,
    val hireRate: Int? = null,
    val spendTotal: Double? = null,
    val feedbackScore: Double? = null,
    val totalHired: Int? = null,
    val invitesSent: Int? = null,
    val invitedToInterview: Int? = null,
    val extra: JsonObject? = null,
) {
    val isHourly: Boolean get() = jobType.equals("hourly", ignoreCase = true)

    fun ageSeconds(now: Long): Long = ((now - publishedAt) / 1000L).coerceAtLeast(0)

    fun ageLabel(now: Long): String {
        val minutes = ageSeconds(now) / 60
        return when {
            minutes < 1 -> "just now"
            minutes < 60 -> "${minutes}m ago"
            minutes < 60 * 24 -> "${minutes / 60}h ${minutes % 60}m ago"
            else -> "${minutes / (60 * 24)}d ago"
        }
    }

    fun withEnrichment(stats: ClientStats, newUrl: String? = null): Job = copy(
        url = newUrl?.takeIf { it.isNotBlank() } ?: url,
        connectsCost = stats.connectsCost ?: connectsCost,
        jobsPosted = stats.jobsPosted ?: jobsPosted,
        hireRate = stats.hireRate ?: hireRate,
        spendTotal = stats.spendTotal ?: spendTotal,
        feedbackScore = stats.feedbackScore ?: feedbackScore,
        totalHired = stats.totalHired ?: totalHired,
        invitesSent = stats.invitesSent ?: invitesSent,
        invitedToInterview = stats.invitedToInterview ?: invitedToInterview,
        category = stats.category?.takeIf { it.isNotBlank() } ?: category,
    )
}

@Serializable
data class ClientStats(
    val connectsCost: String? = null,
    val jobsPosted: Int? = null,
    val hireRate: Int? = null,
    val spendTotal: Double? = null,
    val feedbackScore: Double? = null,
    val totalHired: Int? = null,
    val invitesSent: Int? = null,
    val invitedToInterview: Int? = null,
    val category: String? = null,
    val url: String? = null,
)

@Serializable
data class Account(
    val orgUid: String,
    val name: String = "",
    val type: String = "",
)

@Serializable
data class McpTool(
    val name: String,
    val description: String = "",
    val inputSchema: JsonObject? = null,
)

@Serializable
data class OutboxItem(
    val job: Job,
    val groupId: String,
    val createdAt: Long,
    val attempts: Int = 0,
)

@Serializable
data class CheckRecord(
    val startedAt: Long,
    val finishedAt: Long,
    val groupId: String,
    val status: String,
    val pages: Int = 0,
    val requests: Int = 0,
    val newJobs: Int = 0,
    val notified: Int = 0,
    val durationMs: Long = 0,
    val error: String? = null,
)

// ---- MCP wire DTOs -------------------------------------------------------

@Serializable
internal data class ClientWire(
    val country: String? = null,
    val city: String? = null,
)

@Serializable
internal data class JobWire(
    val id: String? = null,
    val title: String? = null,
    val url: String? = null,
    @SerialName("published_date") val publishedDate: String? = null,
    val budget: JsonElement? = null,
    @SerialName("job_type") val jobType: String? = null,
    @SerialName("proposals_tier") val proposalsTier: String? = null,
    @SerialName("experience_level") val experienceLevel: String? = null,
    val client: ClientWire? = null,
    val skills: JsonElement? = null,
    val description: String? = null,
    val category: String? = null,
    val subcategory: String? = null,
    val extra: JsonObject? = null,
)

@Serializable
internal data class JobsPayload(
    val jobs: List<JobWire> = emptyList(),
)

fun parseJobs(element: JsonElement, now: Long): List<Job> {
    val obj = runCatching { element.jsonObject }.getOrNull() ?: return emptyList()
    val payload = runCatching {
        app.upcroc.core.util.CoreJson.decodeFromJsonElement(JobsPayload.serializer(), obj)
    }.getOrNull()
    if (payload != null) {
        return payload.jobs.mapNotNull { it.toJob(now) }
    }
    val jobs = obj["jobs"] as? kotlinx.serialization.json.JsonArray ?: return emptyList()
    return jobs.mapNotNull { job ->
        runCatching {
            app.upcroc.core.util.CoreJson.decodeFromJsonElement(JobWire.serializer(), job)
        }.getOrNull()?.toJob(now)
    }
}

private fun JobWire.toJob(now: Long): Job? {
    val jobId = id?.takeIf { it.isNotBlank() } ?: return null
    val pub = publishedDate.orEmpty()
    val publishedAt = parsePublishedAt(pub) ?: now
    return Job(
        id = jobId,
        title = title.orEmpty().trim(),
        url = app.upcroc.core.util.cleanUrl(url.orEmpty()),
        publishedDate = pub,
        publishedAt = publishedAt,
        budget = budget?.asDisplayString().orEmpty(),
        jobType = jobType.orEmpty(),
        proposalsTier = proposalsTier.orEmpty(),
        experienceLevel = experienceLevel.orEmpty(),
        country = client?.country.orEmpty(),
        skills = skills?.asStringList().orEmpty(),
        description = description.orEmpty(),
        category = category.orEmpty(),
        firstSeenAt = now,
        extra = extra,
    )
}

private fun parsePublishedAt(value: String): Long? {
    if (value.isBlank()) return null
    return try {
        Instant.parse(value).toEpochMilli()
    } catch (_: DateTimeParseException) {
        runCatching { Instant.parse(value.replace("Z", "+00:00")).toEpochMilli() }.getOrNull()
    }
}

private fun JsonElement.asDisplayString(): String = when (this) {
    is JsonPrimitive -> contentOrNull.orEmpty()
    is JsonObject -> {
        val min = this["min"]?.jsonPrimitive?.doubleOrNull
        val max = this["max"]?.jsonPrimitive?.doubleOrNull
        val type = this["type"]?.jsonPrimitive?.contentOrNull.orEmpty()
        when {
            min != null && max != null -> "\$${min.toInt()}-\$${max.toInt()}${if (type.contains("hour", true)) "/hr" else ""}"
            min != null -> "\$${min.toInt()}+"
            else -> toString()
        }
    }
    else -> toString()
}

private fun JsonElement.asStringList(): List<String> = when (this) {
    is kotlinx.serialization.json.JsonArray -> mapNotNull {
        when (it) {
            is JsonPrimitive -> it.contentOrNull
            is JsonObject -> it["name"]?.jsonPrimitive?.contentOrNull
                ?: it["label"]?.jsonPrimitive?.contentOrNull
            else -> null
        }
    }.filter { it.isNotBlank() }
    is JsonPrimitive -> contentOrNull?.split(',')?.map { it.trim() }?.filter { it.isNotBlank() }.orEmpty()
    else -> emptyList()
}
