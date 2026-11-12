package app.upcroc.core.auth

import app.upcroc.core.util.CoreJson
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonPrimitive
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64

@Serializable
data class OAuthClientConfig(
    @SerialName("client_id") val clientId: String,
    @SerialName("client_name") val clientName: String = "",
    @SerialName("authorization_endpoint") val authorizationEndpoint: String,
    @SerialName("token_endpoint") val tokenEndpoint: String,
    @SerialName("revocation_endpoint") val revocationEndpoint: String? = null,
    val resource: String? = null,
    @SerialName("redirect_uri_template") val redirectUriTemplate: String = "http://127.0.0.1:{port}/callback",
    val scopes: List<String> = emptyList(),
) {
    fun redirectUri(port: Int): String = redirectUriTemplate.replace("{port}", port.toString())

    companion object {
        fun decode(text: String): OAuthClientConfig =
            CoreJson.decodeFromString(serializer(), text)
    }
}

@Serializable
data class TokenSet(
    val accessToken: String,
    val refreshToken: String = "",
    val expiresAt: Long = 0L,
) {
    fun isExpiring(now: Long, skewMs: Long = 60_000L): Boolean = expiresAt <= now + skewMs
    fun encode(): String = CoreJson.encodeToString(serializer(), this)

    companion object {
        fun decode(text: String?): TokenSet? {
            if (text.isNullOrBlank()) return null
            return runCatching { CoreJson.decodeFromString(serializer(), text) }.getOrNull()
        }
    }
}

data class Pkce(val verifier: String, val challenge: String)

object PkceFactory {
    private val random = SecureRandom()

    fun create(): Pkce {
        val bytes = ByteArray(32).also(random::nextBytes)
        val verifier = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
        val digest = MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray(Charsets.US_ASCII))
        val challenge = Base64.getUrlEncoder().withoutPadding().encodeToString(digest)
        return Pkce(verifier, challenge)
    }

    fun randomState(): String {
        val bytes = ByteArray(16).also(random::nextBytes)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }
}

class OAuthAuthException(message: String, cause: Throwable? = null) : Exception(message, cause)
class OAuthNetworkException(message: String, cause: Throwable? = null) : Exception(message, cause)

interface TokenStore {
    suspend fun load(): TokenSet?
    suspend fun save(tokens: TokenSet)
    suspend fun clear()
}
