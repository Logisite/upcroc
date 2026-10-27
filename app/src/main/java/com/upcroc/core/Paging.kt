package com.upcroc.core

import com.upcroc.data.JobEntity
import com.upcroc.data.QueryEntity
import com.upcroc.data.SettingsEntity
import com.upcroc.data.UpcrocDatabase
import com.upcroc.net.UpworkMcp
import io.modelcontextprotocol.kotlin.sdk.client.Client
import kotlinx.coroutines.delay
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

internal data class Fresh(
    val entity: JobEntity,
    val publishedAt: Long,
    /** False for jobs carried over from a previous run: their facts are stored
     *  already, so re-fetching them would spend a request for nothing. */
    val needsEnrich: Boolean = true,
)

internal data class PageResult(val fresh: List<Fresh>, val pages: Int, val stop: String)

/**
 * Pull the continuation token out of a find_jobs response.
 *
 * find_jobs pages with an opaque cursor, documented as: omit for the first
 * page, echo the previous response's endCursor for the next. The envelope key
 * is read defensively because it has moved between pageInfo and a top-level
 * cursor; guessing one key and getting null would silently collapse paging to a
 * single page, which looks exactly like "there was nothing more".
 */
internal fun endCursorOf(body: JsonObject): String? {
    val pageInfo = body["pageInfo"] as? JsonObject
    val raw = pageInfo?.get("endCursor")
        ?: body["nextCursor"]
        ?: body["endCursor"]
        ?: body["cursor"]
    val s = (raw as? JsonPrimitive)?.contentOrNullSafe() ?: return null
    return s.takeIf { it.isNotBlank() && it != "null" }
}

private fun JsonPrimitive.contentOrNullSafe(): String? =
    content.takeIf { it.isNotEmpty() }

/**
 * Upwork reports two separate things about a filter, and conflating them hides
 * a real problem: filters_rejected means a filter the user set was refused and
 * the results are NOT what they asked for; filters_ignored means a value within
 * a filter was dropped (an unmatched skill name). The docs are explicit that
 * nothing is guessed, so both belong in the log.
 */
internal suspend fun reportFilterFeedback(body: JsonObject, label: String, log: suspend (String, String) -> Unit) {
    fun names(key: String): String {
        val el = body[key] ?: return ""
        return runCatching {
            when (el) {
                is kotlinx.serialization.json.JsonArray ->
                    el.mapNotNull { (it as? JsonPrimitive)?.content }.joinToString(", ")
                is JsonPrimitive -> el.content
                is JsonObject -> el.keys.joinToString(", ")
                else -> ""
            }
        }.getOrDefault("")
    }
    val rejected = names("filters_rejected")
    if (rejected.isNotBlank()) log("filter", "$label: rejected $rejected — results differ from what was asked")
    val ignored = names("filters_ignored")
    if (ignored.isNotBlank()) log("filter", "$label: ignored $ignored — value not applied")
}

/**
 * Pages one source by cursor until it stops yielding new jobs.
 *
 * Three guards matter for safety rather than speed. The insert row count decides
 * when to stop, so a repeated window costs exactly one call. A missing cursor
 * ends the loop, because continuing without one just re-reads page one. And
 * because Upwork's ordering is not dependable, a page whose oldest job is not
 * older than the previous page's oldest also ends it.
 */
internal suspend fun pageQuery(
    db: UpcrocDatabase,
    mcp: UpworkMcp,
    client: Client,
    tool: String,
    query: QueryEntity,
    settings: SettingsEntity,
    log: suspend (String, String) -> Unit,
): PageResult {
    val base = runCatching { Json.parseToJsonElement(query.params).jsonObject }
        .getOrElse { JsonObject(emptyMap()) }
    val out = mutableListOf<Fresh>()
    var prevOldest: Long? = null
    var pages = 0
    var stop = "cap"
    var cursor: String? = null
    var reported = false

    for (page in 0 until settings.pageCap) {
        // The cursor is the caller's filter set plus this page's token. It must
        // not be folded back into query.params, or the next check would resume
        // mid-listing instead of from the top.
        val params = if (cursor == null) base else JsonObject(base + ("cursor" to JsonPrimitive(cursor)))
        val body = mcp.call(client, tool, query.action, params)
        if (!reported) {
            reportFilterFeedback(body, query.label, log)
            reported = true
        }
        val jobs = Parse.jobs(body)
        pages++
        if (jobs.isEmpty()) {
            stop = "empty"
            break
        }
        val seen = Parse.now()
        val entities = jobs.mapNotNull { Parse.toEntity(it, seen) }
        // insertIgnore returns -1 for ids already stored, so the surviving row
        // ids are exactly the new jobs.
        val rowIds = db.jobs().insertIgnore(entities)
        if (rowIds.none { it >= 0 }) {
            stop = "no-new"
            break
        }
        entities.forEachIndexed { i, e ->
            if ((rowIds.getOrNull(i) ?: -1L) >= 0L) out += Fresh(e, e.publishedAt)
        }

        val next = endCursorOf(body)
        if (next == null || next == cursor) {
            stop = "no-cursor"
            break
        }
        cursor = next

        val oldest = entities.minOfOrNull { it.publishedAt }
        if (oldest != null && prevOldest != null && oldest >= prevOldest) {
            stop = "ordering-guard"
            log("query", "ordering guard tripped on ${query.label}")
            break
        }
        if (oldest != null) prevOldest = oldest
        if (page < settings.pageCap - 1) delay(settings.paceMs)
    }
    return PageResult(out, pages, stop)
}