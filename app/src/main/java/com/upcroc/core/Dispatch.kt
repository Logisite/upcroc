package com.upcroc.core

import com.upcroc.data.SettingsEntity
import com.upcroc.data.UpcrocDatabase
import com.upcroc.net.UpworkMcp
import io.modelcontextprotocol.kotlin.sdk.client.Client
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * Turns newly seen jobs into notifications.
 *
 * One extra `get` call per new job, and only for jobs whose facts are not
 * already stored, because search results omit connects cost, client history and
 * invite behaviour. A job outside the notify window is stored but never popped
 * up, so a long outage cannot dump a backlog of stale posts on the user.
 */
internal suspend fun dispatchNewJobs(
    db: UpcrocDatabase,
    mcp: UpworkMcp,
    client: Client,
    tool: String,
    fresh: List<Fresh>,
    settings: SettingsEntity,
    notifier: Notifier,
    log: suspend (String, String) -> Unit,
): Int {
    var notified = 0
    var skipped = 0
    for (item in fresh) {
        val age = Parse.now() - item.publishedAt
        // The old rule dropped anything older than lateNotifySec to avoid a
        // post-outage backlog, but in practice that meant almost nothing ever
        // notified: a 15 minute window against a 2-4 minute cadence loses most
        // jobs outright, and a job lost here is lost permanently because the
        // insert is the dedupe marker. The only sane hard bound is "older than
        // retention", so anything the user has not already seen alerts once.
        if (age < 0 || age > NOTIFY_HARD_BOUND_SEC) {
            skipped++
            continue
        }

        var extra = Enriched()
        if (item.needsEnrich) {
            extra = runCatching {
                Parse.enrich(
                    mcp.call(
                        client,
                        tool,
                        "get",
                        JsonObject(mapOf("job_id" to JsonPrimitive(item.entity.id))),
                    )
                )
            }.getOrElse {
                log("enrich", "enrich failed for ${item.entity.id}: ${it.message}")
                Enriched()
            }
        }

        // Write every column we learned, not only the ones that came with a
        // URL: a response missing `url` still carries hire rate and connects.
        val stored = item.entity.copy(
            url = extra.url.ifEmpty { item.entity.url },
            connectsCost = extra.connectsCost.ifEmpty { item.entity.connectsCost },
            jobsPosted = extra.jobsPosted.ifEmpty { item.entity.jobsPosted },
            hireRate = extra.hireRate.ifEmpty { item.entity.hireRate },
            spendTotal = extra.spendTotal.ifEmpty { item.entity.spendTotal },
            feedbackScore = extra.feedbackScore.ifEmpty { item.entity.feedbackScore },
            totalHired = extra.totalHired.ifEmpty { item.entity.totalHired },
            invitesSent = if (item.needsEnrich) extra.invitesSent else item.entity.invitesSent,
            category = extra.category.ifEmpty { item.entity.category },
        )
        db.jobs().enrich(
            id = stored.id,
            connects = stored.connectsCost,
            posted = stored.jobsPosted,
            hire = stored.hireRate,
            spend = stored.spendTotal,
            score = stored.feedbackScore,
            hired = stored.totalHired,
            invites = stored.invitesSent,
            category = stored.category,
            url = stored.url,
        )

        // Count only what was actually surfaced. Counting while notifications are
        // off reports work the user never saw.
        if (settings.notify) {
            notifier.notify(stored, age, true, settings.notifySound, settings.notifyVibrate)
            notified++
        }

        // Mark it either way. The carry-over sweep selects on notifiedAt = 0,
        // so leaving a late job unmarked would re-announce it every check.
        db.jobs().markNotified(item.entity.id, Parse.now())
        log("job", "notified ${item.entity.id} +${age}s ${item.entity.title.take(50)}")
    }
    if (skipped > 0) {
        log("job", "skipped $skipped job(s) older than the retention window")
    }
    return notified
}

/** A week. Anything older is not news, and cannot have been newly seen. */
private const val NOTIFY_HARD_BOUND_SEC = 7 * 86_400L