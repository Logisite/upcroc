package app.upcroc.auth

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.browser.customtabs.CustomTabsIntent
import app.upcroc.BuildConfig
import app.upcroc.core.auth.LoopbackServer
import app.upcroc.core.auth.OAuthAuthException
import app.upcroc.core.auth.OAuthClient
import app.upcroc.core.auth.OAuthClientConfig
import app.upcroc.core.auth.OAuthNetworkException
import app.upcroc.core.auth.Pkce
import app.upcroc.core.auth.PkceFactory
import app.upcroc.core.auth.TokenStore
import app.upcroc.core.engine.PollingEngine
import app.upcroc.core.platform.Logger
import app.upcroc.core.platform.TokenProvider
import app.upcroc.data.prefs.SettingsRepository
import app.upcroc.service.AuthKeepAliveService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient

data class AuthState(
    val signedIn: Boolean = false,
    val busy: Boolean = false,
    val accountName: String = "",
    val error: String? = null,
    val status: String? = null,
    val authUrl: String? = null,
    val manualPrompt: Boolean = false,
)

private data class PendingAuth(
    val pkce: Pkce,
    val redirectUri: String,
    val state: String,
)

class AuthRepository(
    private val context: Context,
    private val settings: SettingsRepository,
    private val tokenStore: TokenStore,
    private val http: OkHttpClient,
    private val logger: Logger,
    private val engineProvider: () -> PollingEngine,
) : TokenProvider {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val oauth = OAuthClient(http, "Upcroc/0.1.0 (Android)")
    private val config: OAuthClientConfig = loadConfig()
    private val _state = MutableStateFlow(AuthState())
    val state: StateFlow<AuthState> = _state

    @Volatile
    private var pending: PendingAuth? = null

    init {
        logger.info("auth: client=${config.clientName.ifBlank { "unknown" }} redirect=${config.redirectUriTemplate}")
        scope.launch {
            val tokens = tokenStore.load()
            logger.info("auth: stored token present=${tokens != null}")
            _state.value = AuthState(signedIn = tokens != null)
        }
    }

    fun signIn() {
        if (_state.value.busy) return
        _state.value = AuthState(busy = true, status = "Opening Upwork…")
        scope.launch {
            logger.info("auth: sign-in start")
            val server = runCatching { LoopbackServer() }.getOrElse {
                logger.error("auth: could not open loopback listener", it)
                _state.value = _state.value.copy(
                    busy = false,
                    error = "Could not open the local sign-in listener. Try again.",
                )
                return@launch
            }
            val pkce = PkceFactory.create()
            val state = PkceFactory.randomState()
            val current = PendingAuth(pkce, server.redirectUri, state)
            pending = current
            val url = runCatching { oauth.authorizationUrl(config, server.redirectUri, pkce, state) }
                .getOrElse {
                    logger.error("auth: could not build authorization URL", it)
                    server.close()
                    _state.value = _state.value.copy(busy = false, error = it.message)
                    return@launch
                }
            logger.info("auth: authorize url=$url")
            _state.value = _state.value.copy(
                authUrl = url,
                status = "Waiting for Upwork approval…",
            )
            try {
                AuthKeepAliveService.start(context)
                logger.info("auth: keepalive service started")
                launchBrowser(url)
                logger.info("auth: waiting for loopback on ${server.redirectUri}")
                val callback = server.awaitCallback(timeoutMs = 180_000, expectedState = state)
                logger.info(
                    "auth: callback received code=${callback.code != null} " +
                        "error=${callback.error ?: "none"} stateMatch=${callback.state == state}"
                )
                when {
                    callback.error == "timeout" -> {
                        logger.warn("auth: loopback timed out; manual fallback enabled")
                        _state.value = _state.value.copy(
                            busy = false,
                            manualPrompt = true,
                            status = "Sign-in timed out. Paste the callback link or code below.",
                        )
                    }
                    callback.error != null -> {
                        _state.value = _state.value.copy(
                            busy = false,
                            error = "Sign-in was cancelled (${callback.error}).",
                            status = null,
                        )
                    }
                    callback.code.isNullOrBlank() -> {
                        _state.value = _state.value.copy(
                            busy = false,
                            error = "No authorization code was returned.",
                            status = null,
                        )
                    }
                    callback.state != null && callback.state != state -> {
                        _state.value = _state.value.copy(
                            busy = false,
                            error = "The sign-in state did not match. Please try again.",
                            status = null,
                        )
                    }
                    else -> completeSignIn(callback.code!!)
                }
            } catch (e: Exception) {
                logger.error("auth: sign-in failed", e)
                _state.value = _state.value.copy(
                    busy = false,
                    status = null,
                    error = e.message ?: "Sign-in failed.",
                    manualPrompt = true,
                )
            } finally {
                AuthKeepAliveService.stop(context)
                logger.info("auth: keepalive service stopped")
                server.close()
            }
        }
    }

    /** Accepts either the full http://127.0.0.1 callback URL or a bare authorization code. */
    fun submitManual(input: String) {
        val current = pending
        if (current == null) {
            _state.value = _state.value.copy(
                error = "That sign-in attempt expired. Start sign-in again.",
                manualPrompt = false,
            )
            return
        }
        val parsed = runCatching { parseManual(input) }.getOrElse {
            _state.value = _state.value.copy(error = it.message ?: "That link could not be read.")
            return
        }
        if (parsed.second != null && parsed.second != current.state) {
            _state.value = _state.value.copy(error = "That link belongs to a different sign-in attempt. Start again.")
            return
        }
        logger.info("auth: manual fallback submitted")
        _state.value = _state.value.copy(busy = true, manualPrompt = false, status = "Finishing sign-in…", error = null)
        scope.launch { completeSignIn(parsed.first) }
    }

    private suspend fun completeSignIn(code: String) {
        val current = pending
        if (current == null) {
            _state.value = _state.value.copy(
                busy = false,
                error = "That sign-in attempt expired. Start sign-in again.",
            )
            return
        }
        try {
            logger.info("auth: exchanging authorization code")
            val tokens = oauth.exchangeCode(
                config = config,
                code = code,
                redirectUri = current.redirectUri,
                verifier = current.pkce.verifier,
                now = System.currentTimeMillis(),
            )
            tokenStore.save(tokens)
            logger.info("auth: tokens stored; expiresAt=${tokens.expiresAt}")
            val orgUid = runCatching { engineProvider().resolveOrgUid() }.getOrNull()
            if (!orgUid.isNullOrBlank()) {
                settings.updateConfig { cfg -> cfg.copy(auth = cfg.auth.copy(orgUid = orgUid)) }
                logger.info("auth: org uid discovered")
            } else {
                logger.warn("auth: org uid not discovered; checks will use a blank org_uid")
            }
            pending = null
            _state.value = AuthState(signedIn = true, status = "Signed in to Upwork.")
            logger.info("auth: sign-in complete")
        } catch (e: OAuthAuthException) {
            logger.warn("auth: token exchange rejected: ${e.message}")
            _state.value = _state.value.copy(busy = false, error = e.message, status = null)
        } catch (e: OAuthNetworkException) {
            logger.warn("auth: token exchange network: ${e.message}")
            _state.value = _state.value.copy(busy = false, error = e.message, status = null)
        } catch (e: Exception) {
            logger.error("auth: token exchange failed", e)
            _state.value = _state.value.copy(
                busy = false,
                error = e.message ?: "Sign-in failed.",
                status = null,
            )
        }
    }

    fun signOut() {
        scope.launch {
            val tokens = tokenStore.load()
            if (tokens != null) runCatching { oauth.revoke(config, tokens.accessToken) }
            tokenStore.clear()
            pending = null
            _state.value = AuthState(signedIn = false)
            logger.info("auth: signed out")
        }
    }

    override suspend fun accessToken(forceRefresh: Boolean): String {
        val tokens = tokenStore.load() ?: throw OAuthAuthException("Sign in to Upwork first.")
        if (!forceRefresh && !tokens.isExpiring(System.currentTimeMillis())) {
            _state.value = _state.value.copy(signedIn = true)
            return tokens.accessToken
        }
        if (tokens.refreshToken.isBlank()) throw OAuthAuthException("Upwork sign-in expired. Sign in again.")
        return try {
            logger.info("auth: refreshing token")
            val fresh = oauth.refresh(config, tokens.refreshToken, System.currentTimeMillis())
            tokenStore.save(fresh)
            _state.value = _state.value.copy(signedIn = true, error = null)
            fresh.accessToken
        } catch (e: OAuthAuthException) {
            logger.warn("auth: refresh rejected: ${e.message}")
            tokenStore.clear()
            _state.value = _state.value.copy(signedIn = false, error = e.message)
            throw e
        }
    }

    override suspend fun invalidate() {
        val tokens = tokenStore.load() ?: return
        tokenStore.save(tokens.copy(expiresAt = 0L))
    }

    suspend fun hasToken(): Boolean = tokenStore.load() != null

    private fun launchBrowser(url: String) {
        try {
            val customTabs = CustomTabsIntent.Builder()
                .setShowTitle(true)
                .build()
            customTabs.intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            customTabs.launchUrl(context, Uri.parse(url))
        } catch (_: ActivityNotFoundException) {
            context.startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
    }

    private fun parseManual(input: String): Pair<String, String?> {
        val trimmed = input.trim()
        if (trimmed.isBlank()) throw IllegalArgumentException("Paste the callback link or authorization code.")
        if (!trimmed.contains("code=") && !trimmed.startsWith("http", ignoreCase = true)) {
            return trimmed to null
        }
        val uri = Uri.parse(if (trimmed.startsWith("http", ignoreCase = true)) trimmed else "http://localhost/callback?$trimmed")
        val error = uri.getQueryParameter("error")
        if (!error.isNullOrBlank()) throw IllegalArgumentException("Upwork returned: $error")
        val code = uri.getQueryParameter("code")
            ?: throw IllegalArgumentException("That link has no authorization code.")
        return code to uri.getQueryParameter("state")
    }

    private fun loadConfig(): OAuthClientConfig {
        val text = context.assets.open(BuildConfig.OAUTH_ASSET).bufferedReader().use { it.readText() }
        return OAuthClientConfig.decode(text)
    }
}
