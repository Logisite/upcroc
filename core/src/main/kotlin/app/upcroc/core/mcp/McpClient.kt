package app.upcroc.core.mcp

import app.upcroc.core.model.McpTool
import app.upcroc.core.platform.Logger
import app.upcroc.core.platform.NoopLogger
import app.upcroc.core.util.CoreJson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.IOException
import java.util.concurrent.atomic.AtomicLong

open class McpException(message: String, cause: Throwable? = null) : Exception(message, cause)
class McpAuthException(message: String = "Upwork sign-in is no longer valid.", cause: Throwable? = null) :
    McpException(message, cause)
class McpNetworkException(message: String, cause: Throwable? = null) : McpException(message, cause)
class McpRateLimitException(val retryAfterSec: Long, message: String) : McpException(message)
class McpProtocolException(message: String, cause: Throwable? = null) : McpException(message, cause)
class McpToolException(val code: Int?, message: String, val data: JsonElement? = null) : McpException(message)

/**
 * A deliberately thin MCP client for one known server.
 *
 * It performs initialize -> notifications/initialized -> tools/list -> tools/call and does not
 * open the long-lived GET SSE stream. Responses may be JSON or inline SSE; both are parsed.
 */
class McpClient(
    private val http: OkHttpClient,
    private val endpoint: String,
    private val userAgent: String,
    internal val protocolVersion: String = "2026-07-28",
    private val json: Json = CoreJson,
    internal val logger: Logger = NoopLogger,
) {
    suspend fun open(token: String): McpSession = McpSession(this, token)

    internal fun newCall(request: Request): Response = http.newCall(request).execute()

    internal fun requestBuilder(token: String): Request.Builder = Request.Builder()
        .url(endpoint)
        .header("Authorization", "Bearer $token")
        .header("Accept", "application/json, text/event-stream")
        .header("User-Agent", userAgent)

    internal fun parseJson(text: String): JsonElement = json.parseToJsonElement(text)

    internal fun encode(element: JsonElement): String = json.encodeToString(JsonElement.serializer(), element)

    internal fun mediaJson() = "application/json; charset=utf-8".toMediaType()
}

class McpSession internal constructor(
    private val client: McpClient,
    private val token: String,
) {
    private val ids = AtomicLong(0)
    private val json = CoreJson

    var sessionId: String? = null
        private set
    var negotiatedProtocol: String = client.protocolVersion
        private set
    var serverName: String = ""
        private set
    var serverVersion: String = ""
        private set

    suspend fun initialize() {
        val id = ids.incrementAndGet()
        client.logger.info("mcp: initialize protocol=${client.protocolVersion}")
        val payload = buildJsonObject {
            put("jsonrpc", "2.0")
            put("id", id)
            put("method", "initialize")
            put("params", buildJsonObject {
                put("protocolVersion", client.protocolVersion)
                put("capabilities", buildJsonObject { })
                put("clientInfo", buildJsonObject {
                    put("name", "upcroc")
                    put("version", "0.1.0")
                })
            })
        }
        val result = send(payload, id, allowEmpty = false)
        if (result is JsonObject) {
            result["protocolVersion"]?.jsonPrimitive?.contentOrNull?.let { negotiatedProtocol = it }
            (result["serverInfo"] as? JsonObject)?.let { info ->
                info["name"]?.jsonPrimitive?.contentOrNull?.let { serverName = it }
                info["version"]?.jsonPrimitive?.contentOrNull?.let { serverVersion = it }
            }
        }
        initialized()
    }

    private suspend fun initialized() {
        val payload = buildJsonObject {
            put("jsonrpc", "2.0")
            put("method", "notifications/initialized")
            put("params", buildJsonObject { })
        }
        send(payload, null, allowEmpty = true)
    }

    suspend fun listTools(): List<McpTool> {
        val id = ids.incrementAndGet()
        val payload = buildJsonObject {
            put("jsonrpc", "2.0")
            put("id", id)
            put("method", "tools/list")
            put("params", buildJsonObject { })
        }
        val result = send(payload, id, allowEmpty = false)
        val tools = (result as? JsonObject)?.get("tools") as? JsonArray ?: return emptyList()
        client.logger.info("mcp: tools/list returned ${tools.size} tool(s)")
        return tools.mapNotNull { element ->
            val obj = element as? JsonObject ?: return@mapNotNull null
            val name = obj["name"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
            McpTool(
                name = name,
                description = obj["description"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                inputSchema = obj["inputSchema"] as? JsonObject,
            )
        }
    }

    suspend fun callTool(name: String, arguments: JsonObject): JsonElement {
        val id = ids.incrementAndGet()
        client.logger.info("mcp: call $name")
        val payload = buildJsonObject {
            put("jsonrpc", "2.0")
            put("id", id)
            put("method", "tools/call")
            put("params", buildJsonObject {
                put("name", name)
                put("arguments", arguments)
            })
        }
        return extractToolResult(send(payload, id, allowEmpty = false))
    }

    suspend fun close() {
        if (sessionId == null) return
        withContext(Dispatchers.IO) {
            runCatching {
                val request = client.requestBuilder(token)
                    .header("Mcp-Session-Id", sessionId!!)
                    .header("Mcp-Protocol-Version", negotiatedProtocol)
                    .delete()
                    .build()
                client.newCall(request).use { }
            }
        }
    }

    private fun extractToolResult(result: JsonElement): JsonElement {
        val obj = result as? JsonObject ?: return result
        val content = obj["content"] as? JsonArray
        if (content != null) {
            for (block in content) {
                val blockObj = block as? JsonObject ?: continue
                if (blockObj["type"]?.jsonPrimitive?.contentOrNull == "text") {
                    val text = blockObj["text"]?.jsonPrimitive?.contentOrNull.orEmpty()
                    if (text.isBlank()) return JsonNull
                    return runCatching { client.parseJson(text) }.getOrElse { JsonPrimitive(text) }
                }
            }
        }
        return obj
    }

    private suspend fun send(payload: JsonObject, id: Long?, allowEmpty: Boolean): JsonElement =
        withContext(Dispatchers.IO) {
            val body = client.encode(payload).toRequestBody(client.mediaJson())
            val builder = client.requestBuilder(token)
                .post(body)
            sessionId?.let { builder.header("Mcp-Session-Id", it) }
            if (sessionId != null) builder.header("Mcp-Protocol-Version", negotiatedProtocol)

            val response: Response = try {
                client.newCall(builder.build())
            } catch (e: IOException) {
                throw McpNetworkException("Could not reach Upwork: ${e.message}", e)
            }
            response.use { resp ->
                resp.header("Mcp-Session-Id")?.let { sessionId = it }
                resp.header("MCP-Protocol-Version")?.let { negotiatedProtocol = it }
                if (resp.code !in 200..299) {
                    client.logger.warn("mcp: HTTP ${resp.code}")
                }
                if (resp.code == 401 || resp.code == 403) {
                    throw McpAuthException("Upwork rejected the session (${resp.code}).")
                }
                if (resp.code == 429) {
                    val retry = parseRetryAfter(resp)
                    throw McpRateLimitException(retry, "Upwork rate limit: retry in ${retry}s.")
                }
                if (resp.code !in 200..299) {
                    val text = runCatching { resp.body?.string().orEmpty() }.getOrElse { "" }
                    throw McpNetworkException("Upwork returned HTTP ${resp.code}: ${text.take(300)}")
                }
                val contentType = resp.header("Content-Type").orEmpty()
                if (contentType.contains("text/event-stream", ignoreCase = true)) {
                    return@withContext readInlineSse(resp, id)
                }
                val text = resp.body?.string().orEmpty()
                if (text.isBlank()) {
                    if (allowEmpty || resp.code == 202 || resp.code == 204) return@withContext JsonNull
                    throw McpProtocolException("Empty response from Upwork (${resp.code}).")
                }
                val element = runCatching { client.parseJson(text) }.getOrElse {
                    throw McpProtocolException("Upwork returned non-JSON: ${text.take(200)}", it)
                }
                handleEnvelope(element, id)
            }
        }

    /**
     * Inline SSE must be read incrementally. A server may keep the stream open for
     * server-initiated notifications after the response, so `body.string()` would hang.
     */
    private fun readInlineSse(resp: Response, expectedId: Long?): JsonElement {
        val source = resp.body?.source() ?: throw McpProtocolException("SSE response had no body.")
        val data = StringBuilder()
        var result: JsonElement? = null
        var done = false

        fun flush() {
            if (data.isEmpty() || done) return
            val payload = data.toString()
            data.clear()
            val element = runCatching { client.parseJson(payload) }.getOrNull() ?: return
            val obj = element as? JsonObject ?: return
            val responseId = obj["id"]?.jsonPrimitive?.contentOrNull?.toLongOrNull()
            if (expectedId != null && responseId != null && responseId != expectedId) return
            if (obj.containsKey("error") || obj.containsKey("result")) {
                result = handleEnvelope(element, expectedId)
                done = true
            }
        }

        while (!done) {
            val line = source.readUtf8Line() ?: break
            when {
                line.isEmpty() -> flush()
                line.startsWith("data:") -> data.append(line.removePrefix("data:").trim())
            }
        }
        if (!done) flush()
        return result ?: throw McpProtocolException("SSE stream ended without a JSON-RPC response.")
    }

    private fun handleEnvelope(element: JsonElement, expectedId: Long?): JsonElement {
        val obj = element as? JsonObject ?: return element
        val error = obj["error"] as? JsonObject
        if (error != null) {
            val message = error["message"]?.jsonPrimitive?.contentOrNull.orEmpty()
            val code = error["code"]?.jsonPrimitive?.intOrNull
            val data = error["data"]
            val text = if (data != null) "${message} ${data}" else message
            if (text.contains("RATE_LIMIT", ignoreCase = true) || text.contains("rate limit", ignoreCase = true)) {
                val retry = Regex("retry_after_seconds\\D+(\\d+)").find(text)?.groupValues?.getOrNull(1)?.toLongOrNull()
                    ?: 30L
                throw McpRateLimitException(retry, text.take(300))
            }
            throw McpToolException(code, message.ifBlank { "Upwork tool error" }, data)
        }
        val responseId = obj["id"]?.jsonPrimitive?.contentOrNull?.toLongOrNull()
        if (expectedId != null && responseId != null && responseId != expectedId) {
            throw McpProtocolException("Response id $responseId did not match request $expectedId.")
        }
        return obj["result"] ?: JsonNull
    }

    private fun parseSse(text: String, expectedId: Long?): JsonElement {
        val data = StringBuilder()
        var lastResult: JsonElement? = null
        fun flush() {
            if (data.isEmpty()) return
            val payload = data.toString()
            data.clear()
            val element = runCatching { client.parseJson(payload) }.getOrNull() ?: return
            lastResult = runCatching { handleEnvelope(element, expectedId) }.getOrNull() ?: lastResult
        }
        text.lineSequence().forEach { line ->
            when {
                line.isEmpty() -> flush()
                line.startsWith("data:") -> data.append(line.removePrefix("data:").trim())
            }
        }
        flush()
        return lastResult ?: throw McpProtocolException("SSE stream ended without a JSON-RPC response.")
    }

    private fun parseRetryAfter(resp: Response): Long {
        val header = resp.header("Retry-After")?.toLongOrNull()
        if (header != null) return header.coerceIn(1, 3600)
        val body = runCatching { resp.body?.string().orEmpty() }.getOrElse { "" }
        val regex = Regex("retry_after_seconds\\D+(\\d+)", RegexOption.IGNORE_CASE)
        return regex.find(body)?.groupValues?.getOrNull(1)?.toLongOrNull()?.coerceIn(1, 3600) ?: 30L
    }
}
