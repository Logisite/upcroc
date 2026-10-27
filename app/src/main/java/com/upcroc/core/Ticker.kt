package com.upcroc.core

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Live ticker values. These are the real numbers the engine is acting on, not a
 * UI-side countdown: the engine pushes into [live] and the screen only reads.
 */
data class Ticker(
    val bucketTotal: Long = 0,
    val bucketLeft: Long = 0,
    val drawnAt: Long = 0,
    val inCheck: Boolean = false,
    val lastCheck: Long = 0,
    val lastNew: Int = 0,
    val lastNotified: Int = 0,
    val lastPages: Int = 0,
    val lastRequests: Int = 0,
    val lastSeconds: Float = 0f,
    val watching: Boolean = false,
    val connected: Boolean = false,
    val sessionStarted: Long = 0,
    val backoffUntil: Long = 0,
) {
    val elapsedInBucket: Long get() = (bucketTotal - bucketLeft).coerceAtLeast(0)
    val progress: Float
        get() = if (bucketTotal <= 0) 0f else (elapsedInBucket.toFloat() / bucketTotal).coerceIn(0f, 1f)
}

object TickerState {
    private val _live = MutableStateFlow(Ticker())
    val live: StateFlow<Ticker> = _live.asStateFlow()

    fun update(block: (Ticker) -> Ticker) {
        _live.value = block(_live.value)
    }

    fun set(v: Ticker) {
        _live.value = v
    }
}