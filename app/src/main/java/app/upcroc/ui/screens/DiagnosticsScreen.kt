package app.upcroc.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.upcroc.core.config.WatcherConfig
import app.upcroc.core.model.Job
import app.upcroc.core.platform.LogLevel
import app.upcroc.data.db.CheckEntity
import app.upcroc.logging.LogLine
import app.upcroc.permissions.Permissions
import app.upcroc.ui.MainViewModel
import app.upcroc.ui.components.AppText
import app.upcroc.ui.components.Banner
import app.upcroc.ui.components.BannerTone
import app.upcroc.ui.components.ConfirmDialog
import app.upcroc.ui.components.DividerSoft
import app.upcroc.ui.components.IconButton40
import app.upcroc.ui.icons.IconGlyph
import app.upcroc.ui.components.LiveDot
import app.upcroc.ui.components.PageHeader
import app.upcroc.ui.components.Segmented
import app.upcroc.ui.components.StatTile
import app.upcroc.ui.components.TextLink
import app.upcroc.ui.components.ToastHost
import app.upcroc.ui.components.UpCard
import app.upcroc.ui.components.pressable
import app.upcroc.ui.components.rememberToastState
import app.upcroc.ui.icons.UiIcon
import app.upcroc.ui.theme.UpType
import app.upcroc.ui.theme.Upcroc
import app.upcroc.ui.util.Fmt
import app.upcroc.ui.util.rememberNow
import java.util.Locale

private enum class EventKind { Check, Alert, Warn, Error, Info }

private data class DiagEvent(
    val key: String,
    val at: Long,
    val kind: EventKind,
    val title: String,
    val sub: String,
    val dur: String,
    val details: List<Pair<String, String>>,
    val full: String? = null,
)

/**
 * Diagnostics, ported from prototype/logs.html. The live card, three stat
 * tiles, permission banners and expandable event log use real watcher data,
 * real check history, real notifications and the persisted app log.
 */
@Composable
fun DiagnosticsScreen(
    vm: MainViewModel,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val config by vm.config.collectAsStateWithLifecycle()
    val status by vm.status.collectAsStateWithLifecycle()
    val snapshot by vm.snapshot.collectAsStateWithLifecycle()
    val checks by vm.recentChecks.collectAsStateWithLifecycle()
    val alerts by vm.recentAlerts.collectAsStateWithLifecycle()
    val logs by vm.logs.collectAsStateWithLifecycle()
    val stats by vm.diagStats.collectAsStateWithLifecycle()
    val toast = rememberToastState()
    val now = rememberNow()
    var filter by rememberSaveable { mutableIntStateOf(0) }
    var clearOpen by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { vm.refreshDiagnostics() }
    var permissionTick by remember { mutableIntStateOf(0) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        permissionTick++
        vm.refreshDiagnostics()
    }
    val notificationsOn = remember(permissionTick) { Permissions.notificationsEnabled(context) }
    val exactOn = remember(permissionTick) { Permissions.exactAlarmsGranted(context) }
    val batteryOk = remember(permissionTick) { Permissions.batteryExempt(context) }

    val groupNames = remember(config.searches) {
        config.searches.associate { it.id to it.name }
    }
    val events = remember(checks, alerts, logs, groupNames, status.requestsToday) {
        buildEvents(checks, alerts, logs, groupNames, status.requestsToday, config)
    }
    val visible = when (filter) {
        1 -> events.filter { it.kind == EventKind.Check }
        2 -> events.filter { it.kind == EventKind.Alert }
        3 -> events.filter { it.kind == EventKind.Error || it.kind == EventKind.Warn }
        else -> events
    }

    val nextAt = status.nextCheckAt ?: snapshot?.nextDueAt
    val nextLine = when {
        !status.running -> "Paused"
        nextAt == null || nextAt <= 0L -> "Watching · next check pending"
        nextAt <= now -> "Watching · checking now"
        else -> "Watching · next check in ${Fmt.countdown(nextAt - now)}"
    }
    val enabled = config.searches.count { it.enabled }
    val limit = config.budget.dailyLimit
    val used = status.requestsToday
    val quotaFraction = (used.toFloat() / limit.coerceAtLeast(1)).coerceIn(0f, 1f)

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize().statusBarsPadding(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 2.dp, bottom = 112.dp),
        ) {
            item {
                PageHeader(
                    kicker = "System",
                    title = "Diagnostics",
                    onBack = onBack,
                    actions = {
                        IconButton40(
                            icon = UiIcon.Copy,
                            contentDescription = "Copy log",
                            onClick = {
                                val clipboard = context.getSystemService(ClipboardManager::class.java)
                                clipboard?.setPrimaryClip(ClipData.newPlainText("Upcroc log", vm.logText()))
                                toast.show("Log copied to clipboard")
                            },
                        )
                        IconButton40(
                            icon = UiIcon.Share,
                            contentDescription = "Share log",
                            onClick = {
                                vm.shareLogs(context)
                                toast.show("Share sheet opened")
                            },
                        )
                    },
                )
            }

            item {
                UpCard(color = Upcroc.Card, radius = 18.dp, padding = PaddingValues(15.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        LiveDot(
                            color = if (status.running) Upcroc.Green else Upcroc.Dim,
                            pulse = status.running,
                        )
                        Spacer(Modifier.width(7.dp))
                        AppText(nextLine, style = UpType.bodyStrong, color = Upcroc.Text, maxLines = 1)
                    }
                    Spacer(Modifier.height(4.dp))
                    AppText(
                        if (status.running) {
                            "${Fmt.plural(enabled, "search", "searches")} on · last check ${Fmt.lastSeen(status.lastCheckAt, now)}"
                        } else {
                            "${Fmt.plural(enabled, "search", "searches")} ready · watcher is off"
                        },
                        style = UpType.small,
                        color = Upcroc.Muted,
                        maxLines = 1,
                    )
                    Spacer(Modifier.height(13.dp))
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(Color(0xFF232323)),
                    ) {
                        if (quotaFraction > 0f) {
                            Box(
                                Modifier
                                    .fillMaxWidth(quotaFraction)
                                    .fillMaxHeight()
                                    .clip(RoundedCornerShape(2.dp))
                                    .background(Upcroc.Blue)
                            )
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Row {
                            AppText("$used", style = UpType.tiny.copy(fontWeight = FontWeight.W700), color = Upcroc.Text)
                            AppText(" requests today", style = UpType.tiny, color = Upcroc.Muted)
                        }
                        Row {
                            AppText(
                                "${(limit - used).coerceAtLeast(0)}",
                                style = UpType.tiny.copy(fontWeight = FontWeight.W700),
                                color = Upcroc.Text,
                            )
                            AppText(" left of $limit", style = UpType.tiny, color = Upcroc.Muted)
                        }
                    }
                }
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(7.dp),
                ) {
                    StatTile(stats.checks.toString(), "checks", Modifier.weight(1f))
                    StatTile(stats.alerts.toString(), "alerts", Modifier.weight(1f))
                    StatTile(stats.errors.toString(), "errors", Modifier.weight(1f))
                }
            }

            if (!batteryOk) {
                item {
                    Spacer(Modifier.height(10.dp))
                    Banner(
                        icon = UiIcon.Warn,
                        title = "Battery optimisation is on",
                        body = "Some checks may be delayed while the screen is off.",
                        action = "Fix",
                        onAction = { Permissions.openBatterySettings(context) },
                    )
                }
            }
            if (!notificationsOn) {
                item {
                    Spacer(Modifier.height(10.dp))
                    Banner(
                        icon = UiIcon.Bell,
                        title = "Notifications are blocked",
                        body = "Job alerts will not reach this phone.",
                        action = "Fix",
                        onAction = { Permissions.openNotificationSettings(context) },
                    )
                }
            }
            if (!exactOn) {
                item {
                    Spacer(Modifier.height(10.dp))
                    Banner(
                        icon = UiIcon.Clock,
                        title = "Exact alarms are off",
                        body = "Android may delay checks until the next window.",
                        action = "Allow",
                        onAction = { Permissions.openExactAlarmSettings(context) },
                    )
                }
            }
            if (status.failures > 0) {
                item {
                    Spacer(Modifier.height(10.dp))
                    Banner(
                        icon = UiIcon.Error,
                        title = "Checks are failing",
                        body = status.lastError ?: "${status.failures} failures in a row.",
                        tone = BannerTone.Danger,
                    )
                }
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 22.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    AppText("Event log", style = UpType.section, color = Upcroc.Text, modifier = Modifier.weight(1f))
                    IconButton40(
                        icon = UiIcon.Download,
                        contentDescription = "Export log",
                        onClick = {
                            vm.shareLogs(context)
                            toast.show("Log exported.")
                        },
                        size = 34.dp,
                    )
                }
            }

            item {
                Segmented(
                    options = listOf("All", "Checks", "Alerts", "Errors"),
                    selected = filter,
                    onSelect = { filter = it },
                    modifier = Modifier.padding(top = 0.dp),
                )
                Spacer(Modifier.height(8.dp))
            }

            if (visible.isEmpty()) {
                item {
                    Box(Modifier.fillMaxWidth().padding(vertical = 28.dp), contentAlignment = Alignment.Center) {
                        AppText("No events in this filter.", style = UpType.caption, color = Upcroc.Dim)
                    }
                }
            } else {
                itemsIndexed(visible, key = { index, event -> "${event.key}-$index" }) { _, event ->
                    EventRow(event)
                }
            }

            item {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(top = 18.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    AppText("Keeps 7 days of events.", style = UpType.tiny, color = Upcroc.Dim)
                    Spacer(Modifier.height(8.dp))
                    TextLink(
                        "Clear log",
                        onClick = { clearOpen = true },
                        color = Upcroc.Error,
                        style = UpType.small.copy(fontWeight = FontWeight.W700),
                    )
                }
            }
        }

        ToastHost(
            state = toast,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 94.dp),
        )
    }

    if (clearOpen) {
        ConfirmDialog(
            title = "Clear log?",
            body = "This removes all saved events from this device.",
            confirmLabel = "Clear",
            onConfirm = {
                clearOpen = false
                vm.clearLogs()
                toast.show("Log cleared")
            },
            onDismiss = { clearOpen = false },
            danger = true,
        )
    }
}

@Composable
private fun EventRow(event: DiagEvent) {
    var open by rememberSaveable(event.key) { mutableStateOf(false) }
    val color = when (event.kind) {
        EventKind.Check -> Upcroc.LogCheck
        EventKind.Alert -> Upcroc.LogAlert
        EventKind.Warn -> Upcroc.LogWarn
        EventKind.Error -> Upcroc.LogError
        EventKind.Info -> Upcroc.LogInfo
    }
    val icon = when (event.kind) {
        EventKind.Check -> UiIcon.Radar
        EventKind.Alert -> UiIcon.Bell
        EventKind.Warn -> UiIcon.Warn
        EventKind.Error -> UiIcon.Offline
        EventKind.Info -> UiIcon.Clock
    }
    Column(Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .pressable(onClick = { open = !open })
                .padding(vertical = 13.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            AppText(
                Fmt.clock(event.at),
                style = UpType.tiny.copy(fontWeight = FontWeight.W600).copy(fontSize = 9.sp),
                color = Upcroc.Dim,
                modifier = Modifier.width(48.dp),
                maxLines = 1,
            )
            Box(Modifier.width(22.dp), contentAlignment = Alignment.Center) {
                IconGlyph(icon, Modifier.width(16.dp).height(16.dp), color)
            }
            Column(Modifier.weight(1f)) {
                AppText(
                    event.title,
                    style = UpType.caption.copy(fontWeight = FontWeight.W700),
                    color = Upcroc.Text,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                AppText(
                    event.sub,
                    style = UpType.tiny.copy(fontSize = 9.5.sp),
                    color = Upcroc.Muted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            AppText(
                event.dur,
                style = UpType.tiny.copy(fontSize = 9.sp, fontWeight = FontWeight.W600),
                color = Upcroc.Dim,
                maxLines = 1,
            )
        }
        AnimatedVisibility(visible = open) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(start = 56.dp, bottom = 14.dp),
                verticalArrangement = Arrangement.spacedBy(7.dp),
            ) {
                event.full?.takeIf { it.isNotBlank() }?.let {
                    AppText(
                        it,
                        style = UpType.small.copy(lineHeight = 15.sp),
                        color = Upcroc.Muted,
                    )
                }
                event.details.chunked(2).forEach { pair ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        pair.forEach { (label, value) ->
                            Row(
                                modifier = Modifier.weight(1f),
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                AppText(
                                    label,
                                    style = UpType.tiny.copy(fontSize = 9.sp),
                                    color = Upcroc.Dim,
                                    maxLines = 1,
                                )
                                AppText(
                                    value,
                                    style = UpType.tiny.copy(fontSize = 9.sp, fontWeight = FontWeight.W600),
                                    color = Upcroc.Muted,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                        if (pair.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
            }
        }
        DividerSoft()
    }
}

private fun buildEvents(
    checks: List<CheckEntity>,
    alerts: List<Job>,
    logs: List<LogLine>,
    groups: Map<String, String>,
    requestsToday: Int,
    config: WatcherConfig,
): List<DiagEvent> {
    val out = mutableListOf<DiagEvent>()
    checks.forEach { check ->
        val group = groups[check.groupId] ?: check.groupId.ifBlank { "Search" }
        val ok = check.status.equals("OK", ignoreCase = true) && check.error == null
        out += DiagEvent(
            key = "check-${check.id}",
            at = check.startedAt,
            kind = if (ok) EventKind.Check else EventKind.Error,
            title = if (ok) "$group checked" else "$group failed",
            sub = if (ok) {
                "${Fmt.plural(check.pages, "page")} · ${Fmt.durationMs(check.durationMs)} · ${check.newJobs} new"
            } else {
                check.error ?: check.status
            },
            dur = if (ok) "OK" else "ERR",
            details = buildList {
                add("group" to group)
                add("pages" to check.pages.toString())
                add("requests" to check.requests.toString())
                add("new" to check.newJobs.toString())
                add("notified" to check.notified.toString())
                add("duration" to Fmt.durationMs(check.durationMs))
                add("quota" to "$requestsToday / ${config.budget.dailyLimit}")
                if (!ok) add("status" to check.status)
            },
        )
    }
    alerts.forEach { job ->
        val at = job.notifiedAt ?: job.firstSeenAt
        if (at <= 0L) return@forEach
        val lag = job.notifiedAt?.let { ((it - job.publishedAt) / 1000L).coerceAtLeast(0L) }
        out += DiagEvent(
            key = "alert-${job.id}",
            at = at,
            kind = EventKind.Alert,
            title = "1 match · notified",
            sub = job.title.ifBlank { "Job alert" },
            dur = lag?.let { "${it}s" } ?: "—",
            details = listOf(
                "job" to job.id.take(14),
                "budget" to job.budget.ifBlank { "not listed" },
                "type" to job.jobType.ifBlank { "not listed" },
                "proposals" to job.proposalsTier.ifBlank { "not listed" },
            ),
        )
    }
    logs.forEach { line ->
        val text = line.text.trim()
        if (text.startsWith("check ") || text.startsWith("engine: ")) return@forEach
        val kind = when (line.level) {
            LogLevel.ERROR -> EventKind.Error
            LogLevel.WARN -> EventKind.Warn
            else -> EventKind.Info
        }
        val split = text.split(" | ", limit = 2)
        out += DiagEvent(
            key = "log-${line.at}-${text.hashCode()}",
            at = line.at,
            kind = kind,
            title = split.first(),
            sub = split.getOrNull(1)?.takeIf { it.isNotBlank() }
                ?: "System event · ${line.level.name.lowercase(Locale.US)}",
            dur = when (line.level) {
                LogLevel.WARN -> "warn"
                LogLevel.ERROR -> "error"
                else -> "—"
            },
            details = listOf(
                "level" to line.level.name,
                "time" to Fmt.clock(line.at),
                "kept" to "7 days",
            ),
            full = text,
        )
    }
    return out.sortedByDescending { it.at }.take(300)
}
