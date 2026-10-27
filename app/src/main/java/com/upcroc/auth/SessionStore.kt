package com.upcroc.auth

import android.content.Context

/**
 * A PKCE attempt that has outlived the process that created it.
 *
 * The verifier is the only thing standing between a pasted authorization code
 * and a token exchange, so it cannot live in memory: Android is free to kill
 * the app while the user is in the browser, which is exactly what happened.
 * Persisting this before the browser opens is what makes that recoverable.
 */
data class OAuthSession(
    val verifier: String,
    val state: String,
    val redirect: String,
    val createdAt: Long,
) {
    fun expired(now: Long, windowMs: Long = DEFAULT_WINDOW_MS) =
        now - createdAt > windowMs

    fun remainingSeconds(now: Long, windowMs: Long = DEFAULT_WINDOW_MS) =
        ((windowMs - (now - createdAt)) / 1000).coerceAtLeast(0)

    companion object {
        const val DEFAULT_WINDOW_MS = 10 * 60 * 1000L
    }
}

/**
 * Separate prefs file from the tokens on purpose: clearing a stale auth attempt
 * must never be able to disturb a valid refresh token.
 */
class SessionStore(context: Context) {
    private val prefs = context.getSharedPreferences("upcroc.session", Context.MODE_PRIVATE)

    fun save(session: OAuthSession) {
        prefs.edit()
            .putString("verifier", session.verifier)
            .putString("state", session.state)
            .putString("redirect", session.redirect)
            .putLong("createdAt", session.createdAt)
            .apply()
    }

    fun load(): OAuthSession? {
        val verifier = prefs.getString("verifier", null) ?: return null
        val state = prefs.getString("state", null) ?: return null
        val redirect = prefs.getString("redirect", null) ?: return null
        return OAuthSession(verifier, state, redirect, prefs.getLong("createdAt", 0L))
    }

    fun clear() = prefs.edit().clear().apply()
}