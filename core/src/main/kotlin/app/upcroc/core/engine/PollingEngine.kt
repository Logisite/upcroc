package app.upcroc.core.engine

import app.upcroc.core.auth.OAuthAuthException
import app.upcroc.core.auth.OAuthNetworkException
import app.upcroc.core.config.WatcherConfig
import app.upcroc.core.filter.JobFilter
import app.upcroc.core.filter.QuietHoursCalc
import app.upcroc.core.filter.SearchParams
import app.upcroc.core.mcp.McpAuthException
import app.upcroc.core.mcp.McpClient
import app.upcroc.core.mcp.McpException
import app.upcroc.core.mcp.McpNetworkException
import app.upcroc.core.mcp.McpProtocolException
import app.upcroc.core.mcp.McpRateLimitException
import app.upcroc.core.mcp.McpSession
import app.upcroc.core.mcp.McpToolException
import app.upcroc.core.model.Account
import app.upcroc.core.model.CheckRecord
import app.upcroc.core.model.ClientStats
import app.upcroc.core.model.Job
import app.upcroc.core.model.McpTool
import app.upcroc.core.model.SearchGroup
import app.upcroc.core.model.SearchMode
import app.upcroc.core.model.parseJobs
import app.upcroc.core.platform.Clock
import app.upcroc.core.platform.EngineStateStore
import app.upcroc.core.platform.JobStore
import app.upcroc.core.platform.Logger
import app.upcroc.core.platform.NetworkMonitor
import app.upcroc.core.platform.Notifier
import app.upcroc.core.platform.TokenProvider
import app.upcroc.core.util.CoreJson
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.OkHttpClient
import kotlin.math.min
import kotlin.random.Random

enum class RunStatus { OK, NO_DUE, OFFLINE, BACKOFF, AUTH, RATE_LIMIT, NETWORK, ERROR }

data class EngineRunResult(
    val status: RunStatus,
    val nextDueAt: Long,
    val requests: Int = 0,
    val newJobs: Int = 0,
    val notified: Int = 0,
    val message: String? = null,
)

data class EngineSnapshot(
    val lastCheckAt: Long = 0L,
    val nextDueAt: Long = 0L,
    val failures: Int = 0,
    val lastError: String? = null,
    val budget: BudgetState = BudgetState(),
)

/**
 * The polling brain. It has no Android dependencies, no timer, and no long-lived
 * connections. One call does one due batch and returns when the next alarm should fire.
 */
class PollingEngine(
    private val configProvider: () -> WatcherConfig,
    private val tokens: TokenProvider,
    private val http: OkHttpClient,
    private val store: JobStore,
    private val state: EngineStateStore,
    private val notifier: Notifier,
    private val logger: Logger,
    private val clock: Clock,
    private val network: NetworkMonitor? = null,
    private val endpoint: String = "https://mcp.upwork.com/mcp",
) {
    private val engineState = EngineState(state, clock)
    private val budgetCalc = RateBudget(clock::now)

    suspend fun runDue(force: Boolean = false): EngineRunResult {
        val config = configProvider().sanitized()
        val now = clock.now()
        if (config.validate().isNotEmpty()) {
            return EngineRunResult(RunStatus.ERROR, now + 60_000, message = config.validate().joinToString("; "))
        }
        if (network?.isOnline() == false) {
            return EngineRunResult(RunStatus.OFFLINE, now + 60_000, message = "No network.")
        }
        val backoffUntil = engineState.long(StateKeys.BACKOFF_UNTIL)
        if (!force && backoffUntil > now) {
            return EngineRunResult(RunStatus.BACKOFF, backoffUntil, message = "Backing off.")
        }
        val enabled = config.enabledGroups()
        if (enabled.isEmpty()) {
            return EngineRunResult(RunStatus.NO_DUE, now + 60_000, message = "No enabled search group.")
        }
        val due = enabled.filter { group ->
            force || engineState.group(group.id).nextDueAt.let { it == 0L || it <= now }
        }
        if (due.isEmpty()) {
            return EngineRunResult(RunStatus.NO_DUE, computeNext(enabled, now))
        }

        var requests = 0
        var newJobs = 0
        var notified = 0
        var lastStatus = RunStatus.OK
        var lastMessage: String? = null
        var session: McpSession? = null

        try {
            val token = try {
                tokens.accessToken()
            } catch (e: OAuthAuthException) {
                tokens.invalidate()
                return failAuth(now, e.message ?: "Sign-in expired.")
            } catch (e: OAuthNetworkException) {
                return fail(RunStatus.NETWORK, now, e.message ?: "Sign-in endpoint unreachable.")
            }

            session = McpClient(http, endpoint, config.network.userAgent, logger = logger).open(token)
            session.initialize()
            val toolName = ensureTool(session)
            val orgUid = ensureOrgUid(session, config)

            for (group in due) {
                val started = clock.now()
                val decision = budgetCalc.consume(engineState.budget(), config.budget, 1)
                if (!decision.allowed) {
                    lastStatus = RunStatus.RATE_LIMIT
                    lastMessage = decision.reason
                    logger.warn("budget skipped ${group.name}: ${decision.reason}")
                    engineState.putGroup(
                        group.id,
                        engineState.group(group.id).copy(
                            nextDueAt = started + 60_000,
                            lastError = decision.reason,
                        ),
                    )
                    engineState.putBudget(decision.state)
                    continue
                }
                engineState.putBudget(decision.state)
                if (decision.warning) logger.warn("search budget warning: ${decision.reason ?: "approaching limit"}")

                val params = SearchParams.build(group, config.filters, config.advanced)
                val arguments = buildJsonObject {
                    put("action", if (group.mode == SearchMode.SMART_SEARCH) "smart_search" else "search")
                    put("org_uid", orgUid)
                    put("params", params)
                }

                try {
                    engineState.putString(StateKeys.CHECK_IN_PROGRESS, group.id)
                    engineState.putLong(StateKeys.CHECK_STARTED_AT, started)
                    engineState.putString(StateKeys.LAST_CHECK_GROUP, group.id)
                    val result = session.callTool(toolName, arguments)
                    requests++
                    toolError(result)?.let { throw McpToolException(null, it) }
                    val rawJobs = parseJobs(result, clock.now())
                    val parsed = rawJobs
                        .filter { JobFilter.matches(it, config.filters) }
                        .distinctBy { it.id }
                        .sortedBy { it.publishedAt }
                    logger.info(
                        "engine: ${group.name} raw=${rawJobs.size} filtered=${parsed.size} " +
                            "payloadKeys=${(result as? JsonObject)?.keys?.joinToString(",")}" +
                            (parsed.firstOrNull()?.title?.let { " first=$it" } ?: "")
                    )
                    val insertNow = clock.now()
                    val insert = store.insertAndEnqueue(parsed, group.id, insertNow) { job ->
                        config.notifications.enabled && notifyable(job, config, insertNow)
                    }
                    newJobs += insert.newJobs.size

                    var toPost = insert.enqueued
                    if (config.notifications.enrich && config.notifications.enrichBeforeNotify) {
                        toPost = toPost.map { job ->
                            enrich(session, toolName, orgUid, job)?.also { store.updateEnrichment(it.id, it.stats()) }
                                ?: job
                        }
                    }
                    val posted = postNotifications(toPost, group, config)
                    notified += posted

                    if (config.notifications.enrich && !config.notifications.enrichBeforeNotify) {
                        for (job in toPost) {
                            val enriched = enrich(session, toolName, orgUid, job) ?: continue
                            store.updateEnrichment(enriched.id, enriched.stats())
                            runCatching { notifier.updateJob(enriched, group) }
                        }
                    }

                    val finish = clock.now()
                    val interval = intervalFor(group, config, finish)
                    engineState.putGroup(
                        group.id,
                        GroupRuntime(
                            nextDueAt = finish + interval * 1000L,
                            lastCheckAt = finish,
                            lastPages = 1,
                            lastNew = insert.newJobs.size,
                            lastNotified = posted,
                        ),
                    )
                    store.recordCheck(
                        CheckRecord(
                            startedAt = started,
                            finishedAt = finish,
                            groupId = group.id,
                            status = "OK",
                            pages = 1,
                            requests = 1,
                            newJobs = insert.newJobs.size,
                            notified = posted,
                            durationMs = finish - started,
                        ),
                    )
                    logger.info(
                        "check ${group.name}: ${insert.newJobs.size} new, $posted notified, " +
                            "${finish - started}ms"
                    )
                } catch (e: McpRateLimitException) {
                    lastStatus = RunStatus.RATE_LIMIT
                    lastMessage = e.message
                    engineState.putBudget(
                        budgetCalc.on429(engineState.budget(), e.retryAfterSec, config.budget.pauseOn429)
                    )
                    val finish = clock.now()
                    engineState.putGroup(
                        group.id,
                        engineState.group(group.id).copy(
                            nextDueAt = finish + e.retryAfterSec * 1000L,
                            lastError = e.message,
                        ),
                    )
                    store.recordCheck(
                        CheckRecord(
                            startedAt = started,
                            finishedAt = finish,
                            groupId = group.id,
                            status = "RATE_LIMIT",
                            requests = 1,
                            error = e.message,
                            durationMs = finish - started,
                        ),
                    )
                    logger.warn("rate limited on ${group.name}: ${e.message}")
                } catch (e: McpAuthException) {
                    throw e
                } catch (e: Exception) {
                    lastStatus = RunStatus.ERROR
                    lastMessage = e.message
                    val finish = clock.now()
                    engineState.putGroup(
                        group.id,
                        engineState.group(group.id).copy(
                            nextDueAt = finish + 60_000,
                            lastError = e.message,
                        ),
                    )
                    store.recordCheck(
                        CheckRecord(
                            startedAt = started,
                            finishedAt = finish,
                            groupId = group.id,
                            status = "ERROR",
                            requests = 1,
                            error = e.message,
                            durationMs = finish - started,
                        ),
                    )
                    logger.error("check failed ${group.name}", e)
                }
            }

            if (lastStatus == RunStatus.OK || lastStatus == RunStatus.RATE_LIMIT) {
                engineState.putLong(StateKeys.LAST_CHECK, clock.now())
                engineState.remove(StateKeys.CHECK_IN_PROGRESS)
                if (lastStatus == RunStatus.OK) clearFailures()
            }
        } catch (e: McpAuthException) {
            tokens.invalidate()
            return failAuth(now, e.message ?: "Sign-in expired.")
        } catch (e: McpRateLimitException) {
            return fail(RunStatus.RATE_LIMIT, now, e.message ?: "Rate limited.", e.retryAfterSec)
        } catch (e: McpNetworkException) {
            return fail(RunStatus.NETWORK, now, e.message ?: "Network error.")
        } catch (e: OAuthAuthException) {
            tokens.invalidate()
            return failAuth(now, e.message ?: "Sign-in expired.")
        } catch (e: OAuthNetworkException) {
            return fail(RunStatus.NETWORK, now, e.message ?: "Sign-in unreachable.")
        } catch (e: McpProtocolException) {
            return fail(RunStatus.ERROR, now, e.message ?: "Protocol error.")
        } catch (e: McpToolException) {
            return fail(RunStatus.ERROR, now, e.message ?: "Tool error.")
        } catch (e: McpException) {
            return fail(RunStatus.ERROR, now, e.message ?: "MCP error.")
        } catch (e: Exception) {
            return fail(RunStatus.ERROR, now, e.message ?: e::class.java.simpleName)
        } finally {
            runCatching { session?.close() }
        }

        val next = computeNext(enabled, clock.now())
        engineState.putLong(StateKeys.NEXT_CHECK, next)
        return EngineRunResult(
            status = lastStatus,
            nextDueAt = next,
            requests = requests,
            newJobs = newJobs,
            notified = notified,
            message = lastMessage,
        )
    }

    suspend fun resumePendingNotifications(): Int {
        val config = configProvider()
        var posted = 0
        val pending = store.pendingOutbox(100)
        for (item in pending) {
            val group = config.searches.firstOrNull { it.id == item.groupId }
            val ok = runCatching { notifier.postJob(item.job, group) }.isSuccess
            if (ok) {
                val now = clock.now()
                store.markOutboxPosted(item.job.id, now)
                val fresh = (now - item.job.publishedAt) <= config.notifications.freshWindowSec * 1000L
                store.markNotified(listOf(item.job.id), if (fresh) setOf(item.job.id) else emptySet(), now)
                posted++
            } else {
                store.markOutboxFailed(item.job.id, "notification post failed")
            }
        }
        if (posted > 0) logger.info("replayed $posted pending notification(s)")
        return posted
    }

    suspend fun preview(groupId: String, limit: Int = 10): List<Job> {
        val config = configProvider().sanitized()
        val group = config.searches.firstOrNull { it.id == groupId }
            ?: config.searches.firstOrNull()
            ?: throw McpProtocolException("No search group configured.")
        val token = tokens.accessToken()
        val session = McpClient(http, endpoint, config.network.userAgent, logger = logger).open(token)
        try {
            session.initialize()
            val toolName = ensureTool(session)
            val orgUid = ensureOrgUid(session, config)
            val params = SearchParams.build(group, config.filters, config.advanced)
            val arguments = buildJsonObject {
                put("action", if (group.mode == SearchMode.SMART_SEARCH) "smart_search" else "search")
                put("org_uid", orgUid)
                put("params", params)
            }
            val result = session.callTool(toolName, arguments)
            return parseJobs(result, clock.now())
                .filter { JobFilter.matches(it, config.filters) }
                .distinctBy { it.id }
                .sortedByDescending { it.publishedAt }
                .take(limit)
        } finally {
            runCatching { session.close() }
        }
    }

    suspend fun resolveOrgUid(): String? {
        val config = configProvider().sanitized()
        val token = tokens.accessToken()
        val session = McpClient(http, endpoint, config.network.userAgent, logger = logger).open(token)
        try {
            session.initialize()
            val accounts = listAccounts(session)
            val account = accounts.firstOrNull() ?: return null
            engineState.putOrgUid(account.orgUid)
            return account.orgUid
        } finally {
            runCatching { session.close() }
        }
    }

    suspend fun snapshot(): EngineSnapshot {
        val enabled = configProvider().enabledGroups()
        return EngineSnapshot(
            lastCheckAt = engineState.long(StateKeys.LAST_CHECK),
            nextDueAt = computeNext(enabled, clock.now()),
            failures = engineState.long(StateKeys.BACKOFF_LEVEL).toInt(),
            lastError = engineState.string(StateKeys.LAST_ERROR),
            budget = engineState.budget(),
        )
    }

    suspend fun toolSchema(): String? = engineState.toolSchema()

    private suspend fun ensureTool(session: McpSession): String {
        val cached = engineState.toolName()
        if (cached != null) return cached
        val tools = session.listTools()
        val tool = tools.firstOrNull {
            it.name.endsWith("find_jobs") || it.name.contains("find_jobs", ignoreCase = true)
        } ?: throw McpProtocolException("Upwork did not expose find_jobs.")
        engineState.putToolName(tool.name)
        tool.inputSchema?.let {
            engineState.putToolSchema(CoreJson.encodeToString(JsonObject.serializer(), it))
            val properties = it["properties"] as? JsonObject
            val paramProperties = ((properties?.get("params") as? JsonObject)?.get("properties")) as? JsonObject
            logger.info("mcp: find_jobs top properties=${properties?.keys?.joinToString(",")}")
            logger.info("mcp: find_jobs param properties=${paramProperties?.keys?.joinToString(",")}")
        }
        logger.info("MCP tool: ${tool.name} (${tools.size} tools)")
        return tool.name
    }

    private suspend fun ensureOrgUid(session: McpSession, config: WatcherConfig): String {
        config.auth.orgUid.takeIf { it.isNotBlank() }?.let { return it }
        engineState.orgUid()?.let { return it }
        val account = listAccounts(session).firstOrNull()
        if (account != null) {
            engineState.putOrgUid(account.orgUid)
            return account.orgUid
        }
        // Upwork's examples accept an empty org_uid and resolve the signed-in account.
        logger.warn("No org UID discovered; sending a blank org_uid. Set one in Layer 0 if checks fail.")
        return ""
    }

    private suspend fun listAccounts(session: McpSession): List<Account> {
        return try {
            val tools = session.listTools()
            val tool = tools.firstOrNull {
                it.name.endsWith("list_accounts") || it.name.contains("list_accounts", ignoreCase = true)
            } ?: return emptyList()
            val result = runCatching {
                session.callTool(tool.name, buildJsonObject { })
            }.getOrElse {
                session.callTool(tool.name, buildJsonObject { put("action", "list") })
            }
            findAccounts(result)
        } catch (e: Exception) {
            logger.warn("could not list Upwork accounts: ${e.message}")
            emptyList()
        }
    }

    private fun findAccounts(element: JsonElement): List<Account> {
        val found = mutableListOf<Account>()
        fun walk(value: JsonElement) {
            when (value) {
                is JsonArray -> value.forEach(::walk)
                is JsonObject -> {
                    val uid = listOf("org_uid", "orgUid", "organizationUid", "organization_uid")
                        .firstNotNullOfOrNull { key -> value[key]?.jsonPrimitive?.contentOrNull }
                    if (!uid.isNullOrBlank()) {
                        found += Account(
                            orgUid = uid,
                            name = listOf("name", "company_name", "title")
                                .firstNotNullOfOrNull { key -> value[key]?.jsonPrimitive?.contentOrNull }
                                .orEmpty(),
                            type = listOf("type", "account_type", "kind")
                                .firstNotNullOfOrNull { key -> value[key]?.jsonPrimitive?.contentOrNull }
                                .orEmpty(),
                        )
                    }
                    value.values.forEach(::walk)
                }
                else -> Unit
            }
        }
        walk(element)
        return found.distinctBy { it.orgUid }
    }

    private suspend fun enrich(
        session: McpSession,
        toolName: String,
        orgUid: String,
        job: Job,
    ): Job? {
        val result = runCatching {
            session.callTool(
                toolName,
                buildJsonObject {
                    put("action", "get")
                    put("org_uid", orgUid)
                    put("params", buildJsonObject { put("id", job.id) })
                },
            )
        }.getOrElse {
            runCatching {
                session.callTool(
                    toolName,
                    buildJsonObject {
                        put("action", "get")
                        put("org_uid", orgUid)
                        put("params", buildJsonObject { put("job_id", job.id) })
                    },
                )
            }.getOrNull()
        } ?: return null
        return job.withEnrichment(parseStats(result))
    }

    private fun parseStats(result: JsonElement): ClientStats {
        val obj = result as? JsonObject ?: return ClientStats()
        val client = obj["client_record"] as? JsonObject
        val data = obj["data"] as? JsonObject
        val post = data?.get("marketplaceJobPosting") as? JsonObject
        val activity = ((post?.get("activityStat") as? JsonObject)?.get("jobActivity")) as? JsonObject
        val classification = (post?.get("classification") as? JsonObject)?.get("category") as? JsonObject
        return ClientStats(
            connectsCost = obj["connects_cost"]?.primitiveText(),
            jobsPosted = client?.get("jobs_posted")?.intValue(),
            hireRate = client?.get("hire_rate_percent")?.intValue(),
            spendTotal = client?.get("spend_total")?.doubleValue(),
            feedbackScore = client?.get("feedback_score")?.doubleValue(),
            totalHired = activity?.get("totalHired")?.intValue(),
            invitesSent = activity?.get("invitesSent")?.intValue(),
            invitedToInterview = activity?.get("totalInvitedToInterview")?.intValue(),
            category = (classification?.get("preferredLabel") as? JsonPrimitive)?.contentOrNull,
            url = (post?.get("url") as? JsonPrimitive)?.contentOrNull,
        )
    }

    private fun JsonElement.intValue(): Int? = runCatching { jsonPrimitive.intOrNull }.getOrNull()
    private fun JsonElement.doubleValue(): Double? = runCatching { jsonPrimitive.doubleOrNull }.getOrNull()
    private fun JsonElement.primitiveText(): String? = runCatching {
        when (val p = jsonPrimitive) {
            is JsonPrimitive -> p.contentOrNull
        }
    }.getOrNull()

    private fun Job.stats(): ClientStats = ClientStats(
        connectsCost = connectsCost,
        jobsPosted = jobsPosted,
        hireRate = hireRate,
        spendTotal = spendTotal,
        feedbackScore = feedbackScore,
        totalHired = totalHired,
        invitesSent = invitesSent,
        invitedToInterview = invitedToInterview,
        category = category,
        url = url,
    )

    private suspend fun postNotifications(
        jobs: List<Job>,
        group: SearchGroup,
        config: WatcherConfig,
    ): Int {
        if (jobs.isEmpty() || !config.notifications.enabled) return 0
        val now = clock.now()
        val freshIds = jobs.filter { (now - it.publishedAt) <= config.notifications.freshWindowSec * 1000L }
            .map { it.id }
            .toSet()
        if (jobs.size > config.notifications.summaryThreshold) {
            val ok = runCatching { notifier.postSummary(jobs, group) }.isSuccess
            if (ok) {
                jobs.forEach { store.markOutboxPosted(it.id, clock.now()) }
                store.markNotified(jobs.map { it.id }, freshIds, clock.now())
                return jobs.size
            }
            return 0
        }
        var posted = 0
        val successful = mutableListOf<Job>()
        for (job in jobs) {
            val ok = runCatching { notifier.postJob(job, group) }.isSuccess
            if (ok) {
                posted++
                successful += job
                store.markOutboxPosted(job.id, clock.now())
            } else {
                store.markOutboxFailed(job.id, "notification post failed")
            }
        }
        if (successful.isNotEmpty()) {
            store.markNotified(
                successful.map { it.id },
                freshIds.intersect(successful.map { it.id }.toSet()),
                clock.now(),
            )
        }
        return posted
    }

    private fun toolError(element: JsonElement): String? {
        val obj = element as? JsonObject ?: return null
        val code = obj["error_code"]?.jsonPrimitive?.contentOrNull
        val reason = obj["reason"]?.jsonPrimitive?.contentOrNull
        val status = obj["status"]?.jsonPrimitive?.contentOrNull
        if (code.isNullOrBlank() && reason.isNullOrBlank()) return null
        return listOfNotNull(
            code?.takeIf { it.isNotBlank() },
            reason?.takeIf { it.isNotBlank() },
            status?.takeIf { it.isNotBlank() && !it.equals("ok", ignoreCase = true) },
        ).joinToString(": ").take(300)
    }

    private fun notifyable(job: Job, config: WatcherConfig, now: Long): Boolean {
        val age = now - job.publishedAt
        return age >= 0 && age <= config.notifications.lateNotifySec * 1000L
    }

    private fun intervalFor(group: SearchGroup, config: WatcherConfig, now: Long): Long {
        val quiet = QuietHoursCalc.isQuiet(now, config.cadence.quietHours)
        if (quiet) {
            return if (group.priority == 0) config.cadence.quietIntervalSec.toLong()
            else config.cadence.quietSecondaryIntervalSec.toLong()
        }
        val min = group.intervalSec.coerceIn(30, 300)
        val max = group.intervalMaxSec.takeIf { it > min }?.coerceAtMost(300) ?: min
        return if (max > min) Random.nextLong(min.toLong(), max.toLong() + 1) else min.toLong()
    }

    private suspend fun computeNext(groups: List<SearchGroup>, now: Long): Long {
        if (groups.isEmpty()) return now + 60_000L
        val values = groups.map { engineState.group(it.id).nextDueAt }
        if (values.any { it == 0L }) return now
        return values.filter { it > 0L }.minOrNull() ?: (now + 60_000L)
    }

    private suspend fun failAuth(now: Long, message: String): EngineRunResult {
        logger.warn("auth: $message")
        runCatching { notifier.postAuthNeeded() }
        return fail(RunStatus.AUTH, now, message)
    }

    private suspend fun fail(status: RunStatus, now: Long, message: String, retryAfterSec: Long? = null): EngineRunResult {
        val level = min(6L, engineState.long(StateKeys.BACKOFF_LEVEL) + 1L)
        val wait = retryAfterSec ?: min(30L shl level.toInt(), 1800L)
        val until = now + wait * 1000L
        engineState.putLong(StateKeys.BACKOFF_LEVEL, level)
        engineState.putLong(StateKeys.BACKOFF_UNTIL, until)
        engineState.putLong(StateKeys.LAST_ERROR_AT, now)
        engineState.putString(StateKeys.LAST_ERROR, message.take(400))
        engineState.putLong(StateKeys.NEXT_CHECK, until)
        engineState.remove(StateKeys.CHECK_IN_PROGRESS)
        logger.warn("check $status: $message (retry in ${wait}s)")
        return EngineRunResult(status, until, message = message)
    }

    private suspend fun clearFailures() {
        engineState.putLong(StateKeys.BACKOFF_LEVEL, 0)
        engineState.putLong(StateKeys.BACKOFF_UNTIL, 0)
        engineState.remove(StateKeys.LAST_ERROR)
    }
}
