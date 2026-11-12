package app.upcroc.core.metrics

import app.upcroc.core.model.Job

data class LatencyStats(
    val count: Int,
    val minSec: Long,
    val p50Sec: Long,
    val p90Sec: Long,
    val maxSec: Long,
    val within2Min: Int,
) {
    val within2MinPercent: Int get() = if (count == 0) 0 else within2Min * 100 / count
}

object Metrics {
    fun latency(jobs: List<Job>): LatencyStats? {
        val values = jobs.asSequence()
            .filter { it.notifiedAt != null && it.notifiedFresh }
            .map { (it.notifiedAt!! - it.publishedAt) / 1000L }
            .filter { it >= 0 }
            .sorted()
            .toList()
        if (values.isEmpty()) return null
        fun pct(p: Double): Long = values[((values.size - 1) * p).toInt().coerceIn(0, values.lastIndex)]
        return LatencyStats(
            count = values.size,
            minSec = values.first(),
            p50Sec = pct(0.50),
            p90Sec = pct(0.90),
            maxSec = values.last(),
            within2Min = values.count { it <= 120 },
        )
    }
}
