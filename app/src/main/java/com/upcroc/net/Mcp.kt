package com.upcroc.net

import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.ClientRequestException
import io.ktor.client.request.bearerAuth
import io.modelcontextprotocol.kotlin.sdk.client.Client
import io.modelcontextprotocol.kotlin.sdk.client.ClientOptions
import io.modelcontextprotocol.kotlin.sdk.client.StreamableHttpClientTransport
import io.modelcontextprotocol.kotlin.sdk.types.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.types.Implementation
import io.modelcontextprotocol.kotlin.sdk.types.TextContent
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject

const val MCP_URL = "https://mcp.upwork.com/mcp"
const val ORG_UID = "2028571967613634984"

/** The identity Upwork sees. "upcroc ai" is deliberate and load-bearing here:
 *  it is the agent name presented to the MCP, distinct from the app label. */
const val MCP_CLIENT_NAME = "upcroc ai"
const val MCP_CLIENT_VERSION = "0.1.0"

class AuthException(msg: String) : Exception(msg)

/**
 * The only actions this app is permitted to perform.
 *
 * The Upwork credential also exposes respond_to_offer, send_message,
 * manage_proposals, submit_milestones and boost_profile. None of them belong in
 * a watcher: the app reads and notifies, and the user acts. Keeping the list
 * here means "no bot movement" is enforced before a request is even built,
 * rather than being a promise about what the code happens to do today.
 */
private val READ_ONLY_ACTIONS = setOf("search", "smart_search", "get")

private val JSON = Json { ignoreUnknownKeys = true; isLenient = true }

/**
 * Request budget for one MCP session.
 *
 * Without this, a check that finds twenty new jobs fires twenty `get` calls
 * back to back. That burst shape is what abuse heuristics catch, so calls are
 * spaced apart and capped per minute. The ceiling is set to roughly the rate
 * the Python reference sustained in production, not above it.
 */
class RateLimiter(private val maxPerMinute: Int = 20, private val minGapMs: Long = 250) {
    private val lock = Any()
    private val stamps = ArrayDeque<Long>()
    private var lastAt = 0L

    suspend fun acquire() {
        // The wait is computed under the lock and taken outside it: suspending
        // while holding a monitor would serialise every caller onto one delay.
        while (true) {
            val waitMs = synchronized(lock) {
                val now = System.currentTimeMillis()
                while (stamps.isNotEmpty() && now - stamps.first() >= 60_000) {
                    stamps.removeFirst()
                }
                if (stamps.size >= maxPerMinute) {
                    60_000 - (now - stamps.first()) + 50
                } else {
                    (minGapMs - (now - lastAt)).coerceAtLeast(0L)
                }
            }
            if (waitMs <= 0) break
            delay(waitMs)
        }
        synchronized(lock) {
            lastAt = System.currentTimeMillis()
            stamps.addLast(lastAt)
        }
    }
}

/**
 * One MCP conversation against Upwork.
 *
 * A session is opened and closed around each check rather than held forever:
 * the Python reference proved a long-lived session leaks cancellation scopes,
 * and a fresh transport per check keeps one failure from poisoning every later
 * one. Every outbound call passes through [limiter] first.
 */
class UpworkMcp(
    private val token: suspend () -> String,
    private val limiter: RateLimiter = SharedLimiter,
) {

    companion object {
        /**
         * One limiter for the whole process, not one per check.
         *
         * Constructing a fresh limiter per check made the "20 per minute"
         * ceiling a "20 per check" one, which is not the guarantee it reads as.
         * A long session must be bounded across all of its checks, so the
         * budget has to outlive any single MCP session.
         */
        val SharedLimiter = RateLimiter()
    }

    var requests: Int = 0
        private set

    suspend fun <T> withSession(block: suspend (Client, String) -> T): T {
        val http = HttpClient(OkHttp) { expectSuccess = true }
        try {
            // The request updater is a plain lambda, not a coroutine, so the
            // token must be resolved before the transport is constructed.
            val bearer = token()
            val transport = StreamableHttpClientTransport(http, MCP_URL) {
                bearerAuth(bearer)
            }
            val client = Client(
                Implementation(name = MCP_CLIENT_NAME, version = MCP_CLIENT_VERSION),
                ClientOptions(),
            )
            try {
                client.connect(transport)
                val listed = client.listTools().tools
                val tool = listed.firstOrNull { it.name.endsWith("find_jobs") }
                    ?: error("find_jobs tool not present on server")
                requests += 3 // initialize + initialized + tools/list
                return block(client, tool.name)
            } finally {
                // close() is suspending; without NonCancellable a cancelled
                // parent abandons teardown and leaks the transport's socket.
                withContext(NonCancellable) {
                    runCatching { client.close() }
                }
            }
        } catch (e: ClientRequestException) {
            if (e.response.status.value == 401 || e.response.status.value == 403) {
                throw AuthException("upwork rejected the token (${e.response.status.value})")
            }
            throw e
        } finally {
            http.close()
        }
    }

    suspend fun call(
        client: Client,
        tool: String,
        action: String,
        params: JsonObject,
    ): JsonObject {
        require(action in READ_ONLY_ACTIONS) {
            "action '$action' is not read-only; upcroc never writes to Upwork"
        }
        limiter.acquire()
        requests++
        val result: CallToolResult = client.callTool(
            tool,
            mapOf("action" to action, "org_uid" to ORG_UID, "params" to params),
        )
        if (result.isError == true) {
            val detail = result.content.filterIsInstance<TextContent>()
                .joinToString(" ") { it.text }.take(300)
            throw IllegalStateException("tool error: $detail")
        }
        val text = result.content.filterIsInstance<TextContent>().firstOrNull()?.text
            ?: return JsonObject(emptyMap())
        return runCatching { JSON.parseToJsonElement(text).jsonObject }
            .getOrElse { JsonObject(mapOf("raw" to JsonPrimitive(text))) }
    }

    suspend fun jobs(client: Client, tool: String, params: JsonObject): List<JsonObject> =
        call(client, tool, "search", params)["jobs"]?.let { el ->
            (el as? JsonArray)?.mapNotNull { it.jsonObject } ?: emptyList()
        } ?: emptyList()
}