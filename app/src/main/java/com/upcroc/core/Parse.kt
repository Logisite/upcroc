package com.upcroc.core

import com.upcroc.data.JobEntity
import com.upcroc.data.QueryEntity
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.time.Instant

object Parse {

    fun now(): Long = System.currentTimeMillis() / 1000

    /** Tracking params carry nothing we need and make the link ugly in a notification. */
    fun cleanUrl(url: String): String = url.substringBefore('?').trimEnd('/')

    fun text(o: JsonObject, key: String): String {
        val el = o[key] ?: return ""
        if (el is JsonNull) return ""
        return (el as? JsonPrimitive)?.contentOrNull ?: ""
    }

    fun time(iso: String, fallback: Long): Long =
        runCatching { Instant.parse(iso).epochSecond }.getOrDefault(fallback)

    fun obj(o: JsonObject, key: String): JsonObject = o[key] as? JsonObject ?: JsonObject(emptyMap())

    /** Upwork's own recency sort is unreliable, so ordering is always done here. */
    fun toEntity(o: JsonObject, seen: Long): JobEntity? {
        val id = text(o, "id")
        if (id.isEmpty()) return null
        val client = obj(o, "client")
        return JobEntity(
            id = id,
            title = text(o, "title").trim(),
            url = cleanUrl(text(o, "url")),
            publishedAt = time(text(o, "published_date"), seen),
            pay = text(o, "budget").ifEmpty { text(o, "job_type") },
            country = text(client, "country"),
            proposalsTier = text(o, "proposals_tier"),
            experienceLevel = text(o, "experience_level"),
            jobType = text(o, "job_type"),
            firstSeen = seen,
        )
    }

    /**
     * Facts the search action omits: connects cost, client history and whether
     * the client is invite-first. `client_record` sits at the top level of the
     * response, not inside the posting, which is easy to get wrong.
     */
    fun enrich(payload: JsonObject): Enriched {
        val post = obj(obj(payload, "data"), "marketplaceJobPosting")
        val client = payload["client_record"] as? JsonObject ?: JsonObject(emptyMap())
        val activity = obj(obj(post, "activityStat"), "jobActivity")
        return Enriched(
            url = cleanUrl(text(post, "url")),
            connectsCost = payload["connects_cost"]?.let { scalar(it) } ?: "",
            jobsPosted = scalar(client["jobs_posted"]),
            hireRate = scalar(client["hire_rate_percent"]),
            spendTotal = scalar(client["spend_total"]),
            feedbackScore = scalar(client["feedback_score"]),
            totalHired = scalar(activity["totalHired"]),
            invitesSent = (activity["invitesSent"] as? JsonPrimitive)?.contentOrNull?.toIntOrNull() ?: 0,
            category = text(obj(obj(post, "classification"), "category"), "preferredLabel"),
        )
    }

    private fun scalar(el: Any?): String =
        ((el as? JsonPrimitive)?.contentOrNull) ?: ""

    fun jobs(payload: JsonObject): List<JsonObject> =
        (payload["jobs"] as? JsonArray)?.mapNotNull { it as? JsonObject } ?: emptyList()

    /**
     * What this source actually searches, read back from its own params.
     *
     * A stored label is free text and drifts: a source can be edited to search
     * "python" while still being called "title AI", and then the log reports
     * something that never happened. Describing the request instead of the
     * label keeps the log honest.
     */
    fun describe(query: QueryEntity): String {
        val params = runCatching {
            Json.parseToJsonElement(query.params).jsonObject
        }.getOrElse { JsonObject(emptyMap()) }
        val bits = params.entries.map { (k, v) ->
            val value = (v as? JsonPrimitive)?.contentOrNull ?: v.toString()
            "$k=$value"
        }
        val body = bits.joinToString(", ")
        return if (body.isEmpty()) query.action else "${query.action} $body"
    }
}

data class Enriched(
    val url: String = "",
    val connectsCost: String = "",
    val jobsPosted: String = "",
    val hireRate: String = "",
    val spendTotal: String = "",
    val feedbackScore: String = "",
    val totalHired: String = "",
    val invitesSent: Int = 0,
    val category: String = "",
)