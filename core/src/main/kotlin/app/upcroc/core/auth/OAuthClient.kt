package app.upcroc.core.auth

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.FormBody
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException

class OAuthClient(
    private val http: OkHttpClient,
    private val userAgent: String,
) {
    fun authorizationUrl(
        config: OAuthClientConfig,
        redirectUri: String,
        pkce: Pkce,
        state: String,
    ): String {
        val builder = config.authorizationEndpoint.toHttpUrl().newBuilder()
            .addQueryParameter("response_type", "code")
            .addQueryParameter("client_id", config.clientId)
            .addQueryParameter("redirect_uri", redirectUri)
            .addQueryParameter("code_challenge", pkce.challenge)
            .addQueryParameter("code_challenge_method", "S256")
            .addQueryParameter("state", state)
        config.resource?.takeIf { it.isNotBlank() }?.let { builder.addQueryParameter("resource", it) }
        if (config.scopes.isNotEmpty()) builder.addQueryParameter("scope", config.scopes.joinToString(" "))
        return builder.build().toString()
    }

    suspend fun exchangeCode(
        config: OAuthClientConfig,
        code: String,
        redirectUri: String,
        verifier: String,
        now: Long,
    ): TokenSet {
        val form = FormBody.Builder()
            .add("grant_type", "authorization_code")
            .add("code", code)
            .add("redirect_uri", redirectUri)
            .add("client_id", config.clientId)
            .add("code_verifier", verifier)
            .build()
        return tokenRequest(config.tokenEndpoint, form, now, fallbackRefresh = "")
    }

    suspend fun refresh(
        config: OAuthClientConfig,
        refreshToken: String,
        now: Long,
    ): TokenSet {
        val form = FormBody.Builder()
            .add("grant_type", "refresh_token")
            .add("refresh_token", refreshToken)
            .add("client_id", config.clientId)
            .build()
        return tokenRequest(config.tokenEndpoint, form, now, fallbackRefresh = refreshToken)
    }

    fun revoke(config: OAuthClientConfig, token: String) {
        val endpoint = config.revocationEndpoint ?: return
        runCatching {
            val form = FormBody.Builder()
                .add("token", token)
                .add("client_id", config.clientId)
                .build()
            val request = Request.Builder()
                .url(endpoint)
                .header("User-Agent", userAgent)
                .post(form)
                .build()
            http.newCall(request).execute().use { }
        }
    }

    private fun tokenRequest(
        endpoint: String,
        form: FormBody,
        now: Long,
        fallbackRefresh: String,
    ): TokenSet {
        val request = Request.Builder()
            .url(endpoint)
            .header("Accept", "application/json")
            .header("User-Agent", userAgent)
            .post(form)
            .build()
        val response = try {
            http.newCall(request).execute()
        } catch (e: IOException) {
            throw OAuthNetworkException("Could not reach Upwork sign-in: ${e.message}", e)
        }
        response.use { resp ->
            val body = resp.body?.string().orEmpty()
            if (resp.code == 400 || resp.code == 401 || resp.code == 403) {
                throw OAuthAuthException("Upwork rejected sign-in (${resp.code}): ${body.take(300)}")
            }
            if (resp.code !in 200..299) {
                throw OAuthNetworkException("Upwork sign-in returned HTTP ${resp.code}: ${body.take(300)}")
            }
            val obj = runCatching { app.upcroc.core.util.CoreJson.parseToJsonElement(body) as JsonObject }
                .getOrElse { throw OAuthNetworkException("Upwork sign-in returned non-JSON.") }
            val access = obj["access_token"]?.jsonPrimitive?.contentOrNull
                ?: throw OAuthAuthException("Upwork sign-in returned no access token.")
            val refresh = obj["refresh_token"]?.jsonPrimitive?.contentOrNull
                ?.takeIf { it.isNotBlank() }
                ?: fallbackRefresh
            val expiresIn = obj["expires_in"]?.jsonPrimitive?.contentOrNull?.toLongOrNull() ?: 3600L
            val expiresAt = now + (expiresIn - 30L).coerceAtLeast(30L) * 1000L
            return TokenSet(accessToken = access, refreshToken = refresh, expiresAt = expiresAt)
        }
    }
}
