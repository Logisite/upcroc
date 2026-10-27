package com.upcroc.core

import android.app.Application
import android.content.Intent
import com.upcroc.WatchService
import com.upcroc.auth.PendingAuth
import com.upcroc.auth.SessionStore
import com.upcroc.auth.TokenStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class AuthStep { IDLE, WAITING, EXCHANGING, CONNECTED, NEEDS_PASTE, FAILED }

/**
 * Owns the sign-in attempt.
 *
 * Two things make this survive a dead process. The attempt is persisted before
 * the browser opens, so a replacement process can still redeem the code. And a
 * foreground service is started alongside it, because the app is otherwise a
 * background candidate the moment the user switches to a browser — which is
 * exactly how the first attempt died and left the callback refused.
 */
class AuthCoordinator(
    private val app: Application,
    private val sessions: SessionStore,
    private val tokens: TokenStore,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var attempt: Job? = null

    private val _state = MutableStateFlow(AuthState())
    val state: StateFlow<AuthState> = _state.asStateFlow()

    data class AuthState(
        val step: AuthStep = AuthStep.IDLE,
        val url: String = "",
        val error: String = "",
        val pasteHint: String = "",
        val waitingSeconds: Long = 0,
    )

    /** True when a previous attempt is still redeemable after a restart. */
    fun resumable(): Boolean {
        val s = sessions.load() ?: return false
        return !s.expired(System.currentTimeMillis())
    }

    fun begin() {
        if (attempt?.isActive == true) return
        _state.value = AuthState()
        // Start the service first: it is what holds the process up while the
        // user is off in the browser.
        runCatching { app.startForegroundService(Intent(app, WatchService::class.java)) }

        attempt = scope.launch {
            val pending = try {
                PendingAuth.create(sessions)
            } catch (t: Throwable) {
                fail("could not open the sign-in listener: ${t.message}")
                return@launch
            }
            _state.value = AuthState(step = AuthStep.WAITING, url = pending.url)

            val code = withContext(Dispatchers.IO) {
                pending.awaitCallback(LISTEN_WINDOW_MS)
            }
            if (code == null) {
                // The session stays on disk and stays redeemable, so the user
                // can still paste the code well after the socket gives up.
                sessions.load()?.let {
                    _state.value = AuthState(
                        step = AuthStep.NEEDS_PASTE,
                        error = "the browser could not reach upcroc",
                        pasteHint = it.redirect,
                    )
                }
                return@launch
            }
            redeem(code)
        }
    }

    /** Manual recovery: a bare code, or the whole callback URL from the browser. */
    fun submitCode(input: String) {
        val session = sessions.load()
        if (session == null || session.expired(System.currentTimeMillis())) {
            fail("that sign-in attempt expired, start again")
            return
        }
        val code = PendingAuth.extractCode(input)
        if (code.isNullOrEmpty()) {
            fail("no code found in that text")
            return
        }
        attempt?.cancel()
        attempt = scope.launch { redeem(code) }
    }

    private suspend fun redeem(code: String) {
        val session = sessions.load() ?: run {
            fail("no sign-in attempt to finish")
            return
        }
        _state.value = _state.value.copy(step = AuthStep.EXCHANGING)
        try {
            val result = PendingAuth.exchange(code, session)
            tokens.write(result.access, result.refresh, result.expiresAt)
            sessions.clear()
            _state.value = AuthState(step = AuthStep.CONNECTED)
        } catch (c: CancellationException) {
            throw c
        } catch (t: Throwable) {
            fail(t.message ?: "Upwork rejected the sign-in")
        }
    }

    private fun fail(message: String) {
        _state.value = AuthState(step = AuthStep.FAILED, error = message)
    }

    fun disconnect() {
        tokens.clear()
        sessions.clear()
        _state.value = AuthState()
    }

    fun acknowledgeConnected() {
        if (_state.value.step == AuthStep.CONNECTED) _state.value = AuthState()
    }

    companion object {
        /**
         * How long the loopback socket listens. Five minutes covers a browser
         * round trip comfortably; the session itself stays redeemable for
         * longer via SessionStore, which is what the paste path relies on.
         */
        private const val LISTEN_WINDOW_MS = 5 * 60 * 1000L
    }
}

