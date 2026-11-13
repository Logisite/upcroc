package app.upcroc.ui.util

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Small text helpers shared by every screen. The prototype prints compact,
 * human strings ("2m", "1m 20s", "$84k"), so these keep that voice.
 */
object Fmt {
    fun countdown(millis: Long): String {
        val total = (millis / 1000L).coerceAtLeast(0L)
        val hours = total / 3600
        val minutes = (total % 3600) / 60
        val seconds = total % 60
        return when {
            hours > 0 -> "${hours}h ${minutes}m"
            minutes > 0 -> "${minutes}m ${seconds}s"
            else -> "${seconds}s"
        }
    }

    fun interval(seconds: Int): String {
        val sec = seconds.coerceAtLeast(0)
        return when {
            sec < 60 -> "${sec}s"
            sec % 3600 == 0 -> "${sec / 3600} hr"
            sec % 60 == 0 -> "${sec / 60} min"
            else -> "${sec / 60}m ${sec % 60}s"
        }
    }

    fun intervalRange(minSec: Int, maxSec: Int): String =
        if (maxSec > minSec) "${interval(minSec)} to ${interval(maxSec)}" else interval(minSec)

    fun age(publishedAt: Long, now: Long): String {
        val sec = ((now - publishedAt) / 1000L).coerceAtLeast(0L)
        return when {
            sec < 60 -> "just now"
            sec < 3600 -> "${sec / 60}m ago"
            sec < 86_400 -> "${sec / 3600}h ago"
            else -> "${sec / 86_400}d ago"
        }
    }

    fun ageShort(publishedAt: Long, now: Long): String {
        val sec = ((now - publishedAt) / 1000L).coerceAtLeast(0L)
        return when {
            sec < 60 -> "now"
            sec < 3600 -> "${sec / 60}m"
            sec < 86_400 -> "${sec / 3600}h"
            else -> "${sec / 86_400}d"
        }
    }

    fun lastSeen(at: Long?, now: Long): String {
        if (at == null || at <= 0L) return "not yet"
        val sec = ((now - at) / 1000L).coerceAtLeast(0L)
        return when {
            sec < 10 -> "just now"
            sec < 60 -> "${sec}s ago"
            sec < 3600 -> "${sec / 60}m ago"
            sec < 86_400 -> "${sec / 3600}h ago"
            else -> "${sec / 86_400}d ago"
        }
    }

    fun clock(at: Long): String = SimpleDateFormat("HH:mm:ss", Locale.US).format(Date(at))

    fun minuteOfDay(minutes: Int): String {
        val h = (minutes / 60).coerceIn(0, 23)
        val m = (minutes % 60).coerceIn(0, 59)
        return String.format(Locale.US, "%02d:%02d", h, m)
    }

    fun durationMs(ms: Long): String = if (ms < 1000) "${ms} ms" else "${ms / 1000}s"

    fun money(value: Double?): String {
        if (value == null) return "-"
        return when {
            value >= 1_000_000 -> "$" + trim(value / 1_000_000) + "m"
            value >= 1_000 -> "$" + trim(value / 1_000) + "k"
            else -> "$" + trim(value)
        }
    }

    fun moneyFull(value: Double?): String {
        if (value == null) return "-"
        val rounded = kotlin.math.round(value)
        return if (rounded == value) "$${rounded.toLong()}" else "$${String.format(Locale.US, "%.2f", value)}"
    }

    private fun trim(value: Double): String {
        val rounded = kotlin.math.round(value * 10) / 10
        return if (rounded == rounded.toLong().toDouble()) rounded.toLong().toString() else rounded.toString()
    }

    fun initials(text: String, max: Int = 2): String {
        val words = text.split(' ', '-', '/', '·')
            .map { it.trim() }
            .filter { it.isNotEmpty() && it.first().isLetterOrDigit() }
        if (words.isEmpty()) return "·"
        return words.take(max).map { it.first().uppercaseChar() }.joinToString("")
    }

    fun plural(count: Int, singular: String, plural: String = singular + "s"): String =
        if (count == 1) "1 $singular" else "$count $plural"

    fun sentence(text: String): String {
        val clean = text.trim()
        if (clean.isEmpty()) return clean
        return clean.first().uppercaseChar() + clean.drop(1)
    }

    /** Turns job-posting HTML into readable text, keeping paragraph breaks. */
    fun plain(text: String): String {
        if (text.isBlank()) return ""
        var out = text
        out = out.replace(Regex("(?i)<br\\s*/?>"), "\n")
        out = out.replace(Regex("(?i)</p\\s*>"), "\n\n")
        out = out.replace(Regex("(?i)<li[^>]*>"), "• ")
        out = out.replace(Regex("(?i)</li\\s*>"), "\n")
        out = out.replace(Regex("<[^>]+>"), "")
        out = out
            .replace("&nbsp;", " ")
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
        return out.lines()
            .joinToString("\n") { it.trim() }
            .replace(Regex("\n{3,}"), "\n\n")
            .trim()
    }

    fun soundLabel(uri: String?): String {
        if (uri.isNullOrBlank()) return "System default"
        val tail = uri.substringAfterLast('/').substringBefore('?')
        return tail.takeIf { it.isNotBlank() } ?: "Custom sound"
    }
}

/** Ticks once a second so countdowns and relative times stay alive. */
@Composable
fun rememberNow(periodMs: Long = 1000L): Long {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(periodMs) {
        while (true) {
            now = System.currentTimeMillis()
            delay(periodMs)
        }
    }
    return now
}
