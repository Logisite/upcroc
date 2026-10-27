package com.upcroc.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Runtime keys in the `state` table. Anything the ticker needs to survive a
 *  process death lives here rather than in memory. */
object K {
    const val BUCKET_TOTAL = "bucket_total"
    const val BUCKET_LEFT = "bucket_left"
    const val BUCKET_DRAWN_AT = "bucket_drawn_at"
    const val BACKOFF_LEVEL = "backoff_level"
    const val BACKOFF_UNTIL = "backoff_until"
    const val LAST_CHECK = "last_check"
    const val SESSION_STARTED = "session_started"
    const val LAST_REAUTH_NAG = "last_reauth_nag"
    const val LAST_VACUUM = "last_vacuum"
    /** Last check figures as "new,notified,pages,requests,seconds". */
    const val TICK_STATS = "tick_stats"
}

/**
 * Seeds the plug-in query list on first run. Upwork's `recency` ordering is
 * unreliable, so every result set is sorted client-side on publishedDate
 * before anything is stored or notified.
 */
val DEFAULT_QUERIES = listOf(
    QueryEntity(
        id = "smart",
        label = "smart search",
        action = "smart_search",
        params = """{"limit":10}""",
        sortOrder = 0,
    ),
    QueryEntity(
        id = "ai_recency",
        label = "title AI, recency",
        action = "search",
        params = """{"title":"AI","sort":"recency","limit":10}""",
        sortOrder = 1,
    ),
)

val DEFAULT_SETTINGS = SettingsEntity()