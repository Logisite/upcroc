package app.upcroc.core.engine

import app.upcroc.core.config.RateBudgetConfig
import app.upcroc.core.util.CoreJson
import kotlinx.serialization.Serializable

@Serializable
data class BudgetState(
    val minuteEpoch: Long = 0L,
    val minuteCount: Int = 0,
    val hourEpoch: Long = 0L,
    val hourCount: Int = 0,
    val dayEpoch: Long = 0L,
    val dayCount: Int = 0,
    val pausedUntil: Long = 0L,
    val last429At: Long = 0L,
    val total429: Int = 0,
)

data class BudgetDecision(
    val allowed: Boolean,
    val warning: Boolean,
    val state: BudgetState,
    val reason: String? = null,
)

/**
 * Fixed-window counter: minute, hour, and UTC day. This mirrors Upwork's published limits.
 * It is advisory by default; [RateBudgetConfig.hardCap] decides whether to block.
 */
class RateBudget(
    private val now: () -> Long,
) {
    fun minuteEpoch(t: Long): Long = t / 60_000L
    fun hourEpoch(t: Long): Long = t / 3_600_000L
    fun dayEpoch(t: Long): Long = t / 86_400_000L

    fun normalize(state: BudgetState): BudgetState {
        val t = now()
        var out = state
        if (out.minuteEpoch != minuteEpoch(t)) out = out.copy(minuteEpoch = minuteEpoch(t), minuteCount = 0)
        if (out.hourEpoch != hourEpoch(t)) out = out.copy(hourEpoch = hourEpoch(t), hourCount = 0)
        if (out.dayEpoch != dayEpoch(t)) out = out.copy(dayEpoch = dayEpoch(t), dayCount = 0)
        return out
    }

    fun consume(
        state: BudgetState,
        config: RateBudgetConfig,
        count: Int = 1,
    ): BudgetDecision {
        val t = now()
        var s = normalize(state)
        if (s.pausedUntil > t) {
            return BudgetDecision(false, false, s, "Rate limited for ${(s.pausedUntil - t) / 1000}s")
        }
        val nextMinute = s.minuteCount + count
        val nextHour = s.hourCount + count
        val nextDay = s.dayCount + count
        val minuteBlocked = nextMinute > config.minuteLimit
        val hourBlocked = nextHour > config.hourlyLimit
        val dayBlocked = nextDay > config.dailyLimit
        val over = minuteBlocked || hourBlocked || dayBlocked
        if (over && config.hardCap) {
            val reason = when {
                dayBlocked -> "Daily search budget reached (${config.dailyLimit})."
                hourBlocked -> "Hourly search budget reached (${config.hourlyLimit})."
                else -> "Per-minute search budget reached (${config.minuteLimit})."
            }
            return BudgetDecision(false, true, s, reason)
        }
        s = s.copy(
            minuteCount = nextMinute,
            hourCount = nextHour,
            dayCount = nextDay,
        )
        val warning = over || nextDay * 100 >= config.dailyLimit * config.warningPercent
        return BudgetDecision(true, warning, s, if (over) "Over the configured soft budget." else null)
    }

    fun on429(state: BudgetState, retryAfterSec: Long, pause: Boolean): BudgetState {
        val t = now()
        return normalize(state).copy(
            pausedUntil = if (pause) t + retryAfterSec.coerceIn(1, 3600) * 1000L else 0L,
            last429At = t,
            total429 = state.total429 + 1,
        )
    }

    fun encode(state: BudgetState): String = CoreJson.encodeToString(BudgetState.serializer(), state)

    fun decode(text: String?): BudgetState {
        if (text.isNullOrBlank()) return BudgetState(
            minuteEpoch = minuteEpoch(now()),
            hourEpoch = hourEpoch(now()),
            dayEpoch = dayEpoch(now()),
        )
        return runCatching { CoreJson.decodeFromString(BudgetState.serializer(), text) }
            .getOrElse {
                BudgetState(
                    minuteEpoch = minuteEpoch(now()),
                    hourEpoch = hourEpoch(now()),
                    dayEpoch = dayEpoch(now()),
                )
            }
    }
}
