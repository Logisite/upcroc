package app.upcroc.logging

import android.content.Context
import android.util.Log
import app.upcroc.core.platform.LogLevel
import app.upcroc.core.platform.Logger
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class LogLine(
    val at: Long,
    val level: LogLevel,
    val text: String,
)

/**
 * In-memory log plus a small on-disk mirror. Both are trimmed to seven days so
 * the Diagnostics screen can truthfully say that events are kept for a week.
 */
class AppLogger(context: Context) : Logger {
    private val dir = File(context.filesDir, "logs").apply { mkdirs() }
    private val file = File(dir, "upcroc.log")
    private val rotated = File(dir, "upcroc.log.1")
    private val stamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
    private val _lines = MutableStateFlow<List<LogLine>>(emptyList())
    val lines: StateFlow<List<LogLine>> = _lines

    init {
        runCatching {
            val loaded = (readFile(rotated) + readFile(file))
                .sortedBy { it.at }
                .takeLast(MAX_LINES)
            _lines.value = purgeExpired(loaded)
            if (file.exists()) rewrite()
        }
    }

    @Synchronized
    override fun log(level: LogLevel, message: String, throwable: Throwable?) {
        val now = System.currentTimeMillis()
        val safe = redact(message) + (throwable?.let { " | ${it::class.java.simpleName}: ${redact(it.message.orEmpty())}" } ?: "")
        val line = LogLine(now, level, safe)
        _lines.value = purgeExpired(_lines.value + line).takeLast(MAX_LINES)
        Log.println(
            when (level) {
                LogLevel.DEBUG -> Log.DEBUG
                LogLevel.INFO -> Log.INFO
                LogLevel.WARN -> Log.WARN
                LogLevel.ERROR -> Log.ERROR
            },
            "Upcroc",
            "${level.name} $safe",
        )
        runCatching {
            if (file.exists() && file.length() > MAX_FILE_BYTES) {
                rotated.delete()
                file.renameTo(rotated)
            }
            file.appendText(format(line) + "\n")
        }
    }

    fun exportText(): String {
        val lines = purgeExpired(_lines.value)
        if (lines.isEmpty()) return "No log entries yet."
        return lines.joinToString("\n", transform = ::format)
    }

    fun clear() {
        _lines.value = emptyList()
        runCatching {
            file.delete()
            rotated.delete()
        }
    }

    private fun readFile(source: File): List<LogLine> {
        if (!source.exists()) return emptyList()
        return source.useLines { rows ->
            rows.mapNotNull { row ->
                val time = row.take(19)
                val rest = row.drop(20)
                val level = LogLevel.entries.firstOrNull { rest.startsWith("${it.name} ") }
                    ?: return@mapNotNull null
                val at = runCatching { stamp.parse(time)?.time }.getOrNull() ?: return@mapNotNull null
                LogLine(at, level, rest.removePrefix("${level.name} "))
            }.toList()
        }
    }

    private fun rewrite() {
        if (_lines.value.isEmpty()) {
            file.delete()
            rotated.delete()
            return
        }
        file.writeText(_lines.value.joinToString("\n", transform = ::format) + "\n")
        rotated.delete()
    }

    private fun purgeExpired(source: List<LogLine>): List<LogLine> {
        val cutoff = System.currentTimeMillis() - RETENTION_MS
        return source.filter { it.at >= cutoff }
    }

    private fun format(line: LogLine): String =
        "${stamp.format(Date(line.at))} ${line.level.name} ${line.text}"

    private fun redact(value: String): String {
        var out = value
        out = out.replace(Regex("(?i)bearer\\s+[A-Za-z0-9._~+/=-]+"), "Bearer ***")
        out = out.replace(Regex("(?i)(access_token|refresh_token|code_verifier)\\s*[=:]\\s*[^\\s,}]+"), "$1=***")
        return out.take(2000)
    }

    private companion object {
        const val MAX_LINES = 1200
        const val MAX_FILE_BYTES = 1_000_000L
        const val RETENTION_MS = 7L * 24 * 60 * 60 * 1000
    }
}
