package com.upcroc.auth

import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.request.forms.submitForm
import io.ktor.client.statement.bodyAsText
import io.ktor.http.Parameters
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.net.InetAddress
import java.net.ServerSocket
import java.net.URLDecoder
import java.net.URLEncoder
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import kotlin.concurrent.thread

object UpworkOAuth {
    const val CLIENT_ID = "https://opencode.ai/oauth/opencode/client.json"
    const val AUTHZ = "https://www.upwork.com/ab/account-security/oauth2/authorize"
    const val TOKEN = "https://www.upwork.com/api/v3/oauth2/token"
    const val RESOURCE = "https://mcp.upwork.com/mcp"
}

/**
 * One authorization-code + PKCE(S256) attempt, bound to a loopback socket.
 *
 * The registered client document for this public client only permits loopback
 * redirects, so there is no custom scheme to fall back on: if this process
 * dies while the user is in the browser, the socket dies with it and the
 * callback is refused. That is why [session] is persisted before the browser
 * opens, so [exchange] can still complete from a pasted code afterwards.
 */
class PendingAuth(val session: OAuthSession, private val socket: ServerSocket) {

    val port: Int get() = socket.localPort

    val redirect: String get() = session.redirect

    val url: String = UpworkOAuth.AUTHZ + "?" + Companion.query(
        mapOf(
            "response_type" to "code",
            "client_id" to UpworkOAuth.CLIENT_ID,
            "redirect_uri" to session.redirect,
            "code_challenge" to b64(
                MessageDigest.getInstance("SHA-256")
                    .digest(session.verifier.toByteArray())
            ),
            "code_challenge_method" to "S256",
            "state" to session.state,
            "resource" to UpworkOAuth.RESOURCE,
        )
    )

    /**
     * Blocks until the browser delivers a callback carrying our state, or
     * [timeoutMs] elapses. Returns the raw callback query, or null.
     *
     * Connections are looped, not accepted once: browsers open speculative and
     * preconnect sockets that send no request line at all, and treating the
     * first connection as the answer made sign-in fail intermittently.
     */
    fun awaitCallback(timeoutMs: Long): String? {
        val deadline = System.currentTimeMillis() + timeoutMs
        var result: String? = null
        val worker = thread(isDaemon = true) {
            while (System.currentTimeMillis() < deadline && result == null) {
                val conn = try {
                    socket.soTimeout = 1_000
                    socket.accept()
                } catch (_: Exception) {
                    continue
                }
                try {
                    conn.soTimeout = 3_000
                    val request = conn.getInputStream().bufferedReader().readLine()
                    if (request == null || !request.startsWith("GET ")) continue
                    val target = request.substringAfter("GET ", "").substringBefore(" ")
                    val raw = target.substringAfter('?', "")
                    val params = Companion.parseQuery(raw)
                    val ok = params["state"] == session.state && params["code"] != null
                    Companion.respond(conn, if (ok) AUTHORISED else REJECTED)
                    if (ok) result = params["code"]
                    // A mismatched state is not ours; keep listening rather than
                    // handing back a code we must not accept.
                } catch (_: Exception) {
                    // Malformed or dropped connection; keep waiting.
                } finally {
                    runCatching { conn.close() }
                }
            }
        }
        worker.join(timeoutMs + 2_000)
        close()
        return result
    }

    fun close() = runCatching { socket.close() }

    companion object {
        private const val AUTHORISED = "upcroc received the code. Return to the app."
        private const val REJECTED = "upcroc: that response was not for this app."

        private val JSON = Json { ignoreUnknownKeys = true }
        private val b64Encoder = Base64.getUrlEncoder().withoutPadding()
        fun b64(bytes: ByteArray) = b64Encoder.encodeToString(bytes)

        /**
         * Binds the listening socket first and derives the redirect from that
         * exact port, then persists.
         *
         * Binding twice would be a subtle disaster: the URL would carry one
         * port while the app listened on another, so the browser's callback
         * would be refused every single time.
         */
        fun create(
            sessions: SessionStore,
        ): PendingAuth {
            val random = SecureRandom()
            val verifier = b64(ByteArray(32).also { random.nextBytes(it) })
            val state = b64(ByteArray(16).also { random.nextBytes(it) })
            val socket = ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"))
            val redirect = "http://127.0.0.1:${socket.localPort}/callback"
            val session = OAuthSession(verifier, state, redirect, System.currentTimeMillis())
            sessions.save(session)
            return PendingAuth(session, socket)
        }

        /**
         * Accepts either a bare code or the whole callback URL the browser was
         * left showing, because users copy whichever is on screen.
         */
        fun extractCode(input: String): String? {
            val trimmed = input.trim()
            if (trimmed.isEmpty()) return null
            if (!trimmed.contains("?") && !trimmed.contains("=")) return trimmed
            val query = trimmed.substringAfter('?', "")
            return parseQuery(query)["code"]
        }

        /** OAuth query decoding does not treat `+` as a space. */
        private fun parseQuery(raw: String): Map<String, String> =
            raw.split("&").mapNotNull { pair ->
                val i = pair.indexOf('=')
                if (i <= 0) null
                else decode(pair.take(i)) to decode(pair.substring(i + 1))
            }.toMap()

        private fun decode(s: String) = URLDecoder.decode(s.replace("+", "%2B"), "UTF-8")

        private fun query(params: Map<String, String>): String =
            params.entries.joinToString("&") { (k, v) -> "$k=" + URLEncoder.encode(v, "UTF-8") }

        private fun respond(conn: java.net.Socket, body: String) {
            runCatching {
                conn.getOutputStream().apply {
                    write(
                        ("HTTP/1.1 200 OK\r\nContent-Type: text/plain\r\n" +
                            "Content-Length: ${body.toByteArray().size}\r\n" +
                            "Connection: close\r\n\r\n$body").toByteArray()
                    )
                    flush()
                }
            }
        }

        suspend fun exchange(code: String, session: OAuthSession): Tokens =
            post(
                Parameters.build {
                    append("grant_type", "authorization_code")
                    append("code", code)
                    append("redirect_uri", session.redirect)
                    append("client_id", UpworkOAuth.CLIENT_ID)
                    append("code_verifier", session.verifier)
                },
                fallbackRefresh = "",
            )

        /**
         * A refresh response may legally omit `refresh_token` when unchanged.
         * Writing an empty string over the stored one would kill the grant.
         */
        suspend fun refresh(refreshToken: String): Tokens =
            post(
                Parameters.build {
                    append("grant_type", "refresh_token")
                    append("refresh_token", refreshToken)
                    append("client_id", UpworkOAuth.CLIENT_ID)
                },
                fallbackRefresh = refreshToken,
            )

        private suspend fun post(form: Parameters, fallbackRefresh: String): Tokens {
            val http = HttpClient(OkHttp) { expectSuccess = false }
            return try {
                val response = http.submitForm(url = UpworkOAuth.TOKEN, formParameters = form)
                val text = response.bodyAsText()
                if (response.status.value !in 200..299) {
                    error("token endpoint ${response.status.value}: ${text.take(200)}")
                }
                val obj: JsonObject = JSON.parseToJsonElement(text).jsonObject
                fun str(key: String) = obj[key]?.jsonPrimitive?.content
                val access = str("access_token") ?: error("no access_token in response")
                val expiresIn = str("expires_in")?.toLongOrNull() ?: 3600L
                Tokens(
                    access = access,
                    refresh = str("refresh_token")?.takeIf { it.isNotEmpty() } ?: fallbackRefresh,
                    expiresAt = System.currentTimeMillis() / 1000 + expiresIn - 30,
                )
            } finally {
                http.close()
            }
        }
    }
}