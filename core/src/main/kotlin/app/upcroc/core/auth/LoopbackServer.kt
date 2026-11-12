package app.upcroc.core.auth

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.Closeable
import java.net.InetAddress
import java.net.ServerSocket
import java.net.URI
import java.net.URLDecoder

data class OAuthCallback(
    val code: String? = null,
    val state: String? = null,
    val error: String? = null,
)

/**
 * A single-use loopback listener for the OAuth redirect. Binds only to 127.0.0.1 and
 * accepts the first request to /callback. Other requests (favicon, preconnect) get 404
 * and are ignored.
 */
class LoopbackServer(
    private val host: String = "127.0.0.1",
    private val path: String = "/callback",
) : Closeable {
    private val server: ServerSocket = ServerSocket(0, 1, InetAddress.getByName(host))
    val port: Int = server.localPort
    val redirectUri: String = "http://$host:$port$path"

    suspend fun awaitCallback(timeoutMs: Long = 300_000L, expectedState: String? = null): OAuthCallback =
        withContext(Dispatchers.IO) {
            server.soTimeout = timeoutMs.toInt()
            val deadline = System.currentTimeMillis() + timeoutMs
            while (System.currentTimeMillis() < deadline) {
                val socket = try {
                    server.accept()
                } catch (_: java.net.SocketTimeoutException) {
                    break
                }
                socket.use { client ->
                    client.soTimeout = 10_000
                    val reader = client.getInputStream().bufferedReader(Charsets.UTF_8)
                    val requestLine = reader.readLine().orEmpty()
                    val target = requestLine.split(' ').getOrNull(1).orEmpty()
                    val uri = runCatching { URI("http://localhost$target") }.getOrNull()
                    if (uri?.path != path) {
                        respond(client, 404, "Not found")
                        return@use
                    }
                    val query = parseQuery(uri.rawQuery)
                    val callback = OAuthCallback(
                        code = query["code"],
                        state = query["state"],
                        error = query["error"],
                    )
                    val ok = callback.error == null &&
                        callback.code != null &&
                        (expectedState == null || callback.state == expectedState)
                    respond(
                        client,
                        if (ok) 200 else 400,
                        if (ok) "Upwork approved. You can close this tab." else "Sign-in failed. Return to Upcroc.",
                    )
                    return@withContext callback
                }
            }
            OAuthCallback(error = "timeout")
        }

    private fun respond(client: java.net.Socket, status: Int, message: String) {
        val body = "<!doctype html><meta charset=\"utf-8\"><title>Upcroc</title>" +
            "<body style=\"font-family:system-ui;padding:3rem;text-align:center\">" +
            "<h2>$message</h2></body>"
        val bytes = body.toByteArray(Charsets.UTF_8)
        val reason = when (status) {
            200 -> "OK"
            400 -> "Bad Request"
            else -> "Not Found"
        }
        val head = "HTTP/1.1 $status $reason\r\n" +
            "Content-Type: text/html; charset=utf-8\r\n" +
            "Content-Length: ${bytes.size}\r\n" +
            "Connection: close\r\n\r\n"
        runCatching {
            client.getOutputStream().write(head.toByteArray(Charsets.US_ASCII))
            client.getOutputStream().write(bytes)
            client.getOutputStream().flush()
        }
    }

    private fun parseQuery(raw: String?): Map<String, String> {
        if (raw.isNullOrBlank()) return emptyMap()
        return raw.split('&').mapNotNull { pair ->
            val idx = pair.indexOf('=')
            if (idx <= 0) null else {
                val key = URLDecoder.decode(pair.substring(0, idx), "UTF-8")
                val value = URLDecoder.decode(pair.substring(idx + 1), "UTF-8")
                key to value
            }
        }.toMap()
    }

    override fun close() {
        runCatching { server.close() }
    }
}
