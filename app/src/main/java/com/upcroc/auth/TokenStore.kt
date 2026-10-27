package com.upcroc.auth

import android.content.Context

data class Tokens(
    val access: String,
    val refresh: String,
    val expiresAt: Long,
) {
    /** Sixty seconds of slack, so a token cannot expire mid-request. */
    fun expired(now: Long) = expiresAt <= now + 60
}

/**
 * Tokens live in app-private SharedPreferences, and auto-backup is switched off
 * in the manifest, so they do not leave the device through Google backup.
 */
class TokenStore(context: Context) {
    private val prefs = context.getSharedPreferences("upcroc.auth", Context.MODE_PRIVATE)

    fun read(): Tokens? {
        val access = prefs.getString("access", null) ?: return null
        return Tokens(
            access = access,
            refresh = prefs.getString("refresh", "") ?: "",
            expiresAt = prefs.getLong("expires", 0L),
        )
    }

    fun write(access: String, refresh: String, expiresAt: Long) {
        prefs.edit()
            .putString("access", access)
            .putString("refresh", refresh)
            .putLong("expires", expiresAt)
            .apply()
    }

    fun clear() = prefs.edit().clear().apply()
}