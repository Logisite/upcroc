package app.upcroc.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.upcroc.core.model.Job
import app.upcroc.core.model.SearchMode
import app.upcroc.permissions.Permissions
import app.upcroc.runtime.CheckResult
import app.upcroc.ui.MainViewModel
import app.upcroc.ui.components.AppDialog
import app.upcroc.ui.components.AppText
import app.upcroc.ui.components.Avatar
import app.upcroc.ui.components.Banner
import app.upcroc.ui.components.BannerTone
import app.upcroc.ui.components.DialogAction
import app.upcroc.ui.components.DividerSoft
import app.upcroc.ui.components.EmptyState
import app.upcroc.ui.components.GhostButton
import app.upcroc.ui.components.IconButton40
import app.upcroc.ui.icons.IconGlyph
import app.upcroc.ui.components.LiveDot
import app.upcroc.ui.components.ModalSheet
import app.upcroc.ui.components.PrimaryButton
import app.upcroc.ui.components.SectionHeader
import app.upcroc.ui.components.TopBar
import app.upcroc.ui.components.pressable
import app.upcroc.ui.icons.UiIcon
import app.upcroc.ui.theme.UpType
import app.upcroc.ui.theme.Upcroc
import app.upcroc.ui.util.Fmt
import app.upcroc.ui.util.rememberNow
import kotlinx.coroutines.delay

/**
 * Alerts screen, ported from prototype/alerts.html + feed.css. Layout and copy
 * stay the same; the hero, cards, checks, and list run on live state.
 */
@Composable
fun FeedScreen(
    vm: MainViewModel,
    onOpenJob: (String) -> Unit,
    onSearch: () -> Unit,
    onDiagnostics: () -> Unit,
    onToast: (String) -> Unit,
) {
    val context = LocalContext.current
    val config by vm.config.collectAsStateWithLifecycle()
    val status by vm.status.collectAsStateWithLifecycle()
    val lastResult by vm.lastResult.collectAsStateWithLifecycle()
    val snapshot by vm.snapshot.collectAsStateWithLifecycle()
    val jobs by vm.jobs.collectAsStateWithLifecycle()
    val auth by vm.authState.collectAsStateWithLifecycle()
    val now = rememberNow()
    var showAll by rememberSaveable { mutableStateOf(false) }
    var notifyHelp by remember { mutableStateOf(false) }
    var backgroundHelp by remember { mutableStateOf(false) }
    var permissionTick by remember { mutableIntStateOf(0) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { permissionTick++ }

    val notificationsOn = remember(permissionTick) { Permissions.notificationsEnabled(context) }
    val enabled = config.enabledGroups()
    val next = status.nextCheckAt ?: snapshot?.nextDueAt
    val countdown = when {
        !status.running -> "Paused"
        status.checking -> "Checking"
        next == null || next <= 0L -> "Checking"
        next <= now -> "Overdue"
        else -> Fmt.countdown(next - now)
    }
    val heroHint = when {
        !status.running -> "tap play to start · ${Fmt.plural(enabled.size, "search", "searches")}"
        status.checking -> "talking to Upwork…"
        lastResult != null -> "last check ${Fmt.lastSeen(lastResult?.at, now)} · ${resultShort(lastResult!!)}"
        else -> "next check · ${Fmt.plural(enabled.size, "search", "searches")}"
    }
    val isOverdue = status.running && !status.checking && next != null && next > 0L && next <= now
    var autoTriedAt by remember { mutableLongStateOf(0L) }
    var seenResultAt by remember { mutableLongStateOf(lastResult?.at ?: 0L) }

    LaunchedEffect(isOverdue, status.lastCheckAt) {
        if (isOverdue && System.currentTimeMillis() - autoTriedAt > 30_000L) {
            delay(1200)
            autoTriedAt = System.currentTimeMillis()
            vm.checkNow(context, announce = false, auto = true)
        }
    }
    var wasSignedIn by remember { mutableStateOf(auth.signedIn) }
    LaunchedEffect(auth.signedIn) {
        if (auth.signedIn && !wasSignedIn) {
            onToast("Connected to Upwork.")
            vm.checkNow(context, announce = false, auto = true)
        }
        wasSignedIn = auth.signedIn
    }

    var notificationsWereOff by remember { mutableStateOf(!notificationsOn) }
    LaunchedEffect(notificationsOn) {
        if (notificationsWereOff && notificationsOn) onToast("Notifications are on.")
        notificationsWereOff = !notificationsOn
    }
    LaunchedEffect(lastResult?.at) {
        val result = lastResult ?: return@LaunchedEffect
        if (result.at > seenResultAt) {
            seenResultAt = result.at
            if (result.source == "manual") onToast(resultText(result))
        }
    }

    val used = status.requestsToday.takeIf { it > 0 } ?: snapshot?.budget?.dayCount ?: 0
    val limit = config.budget.dailyLimit.coerceAtLeast(1)
    val maxGapSec = enabled.maxOfOrNull { it.intervalUpperSec } ?: 300
    val staleAfterMs = maxOf(maxGapSec * 2000L, 10 * 60_000L)
    val recentJobs = jobs
    val visibleJobs = if (showAll) recentJobs.take(50) else recentJobs.take(4)

    LazyColumn(
        modifier = Modifier.fillMaxWidth().statusBarsPadding(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 112.dp),
    ) {
        item {
            TopBar(
                kicker = "Welcome back",
                title = "Alerts",
                avatar = if (auth.signedIn) "AU" else "·",
                avatarColor = if (auth.signedIn) Upcroc.Blue else Upcroc.Dim,
                actions = {
                    IconButton40(UiIcon.Search, "Search", onSearch)
                    IconButton40(
                        UiIcon.Bell,
                        "Notifications",
                        { Permissions.openNotificationSettings(context) },
                        badge = !notificationsOn,
                    )
                },
            )
        }

        item {
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(Upcroc.Hero)
                    .padding(start = 17.dp, end = 17.dp, top = 16.dp, bottom = 15.dp),
            ) {
                Row(verticalAlignment = Alignment.Top) {
                    Column(Modifier.weight(1f)) {
                        AppText(
                            (if (status.running) "Watching" else "Paused").uppercase(),
                            style = UpType.tiny.copy(
                                fontWeight = FontWeight.W700,
                                letterSpacing = 0.9.sp,
                            ),
                            color = Upcroc.Dim,
                        )
                        Spacer(Modifier.height(3.dp))
                        AppText(
                            countdown,
                            style = UpType.heroBig.copy(fontSize = 36.sp, lineHeight = 37.sp),
                            color = Upcroc.Text,
                            maxLines = 1,
                        )
                        Spacer(Modifier.height(3.dp))
                        AppText(
                            heroHint,
                            style = UpType.tiny.copy(fontSize = 11.sp),
                            color = Upcroc.Muted,
                            maxLines = 2,
                        )
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(
                            Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(Color.White)
                                .pressable(onClick = {
                                    if (!auth.signedIn) {
                                        onToast("Sign in to Upwork first.")
                                    } else {
                                        vm.checkNow(context)
                                    }
                                }),
                            contentAlignment = Alignment.Center,
                        ) {
                            IconGlyph(UiIcon.Plus, Modifier.size(18.dp), Upcroc.Ink, strokeWidth = 1.8.dp)
                        }
                        Spacer(Modifier.height(4.dp))
                        AppText(
                            "Check now",
                            style = UpType.tiny.copy(fontSize = 10.sp, fontWeight = FontWeight.W600),
                            color = Upcroc.Muted,
                        )
                    }
                }
                Spacer(Modifier.height(14.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(
                        Modifier
                            .clip(RoundedCornerShape(7.dp))
                            .background(Upcroc.SurfaceHighest)
                            .padding(horizontal = 7.dp, vertical = 5.dp),
                    ) {
                        AppText(
                            "Quota $used / $limit",
                            style = UpType.tiny.copy(fontSize = 10.sp, fontWeight = FontWeight.W600),
                            color = Upcroc.Muted,
                        )
                    }
                    Box(
                        Modifier
                            .weight(1f)
                            .height(3.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(Upcroc.SurfaceHighest),
                    ) {
                        Box(
                            Modifier
                                .fillMaxWidth((used.toFloat() / limit.toFloat()).coerceIn(0f, 1f))
                                .height(3.dp)
                                .background(Upcroc.Blue)
                        )
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                        Box(
                            Modifier
                                .size(5.dp)
                                .clip(CircleShape)
                                .background(if (status.running) Upcroc.Blue else Upcroc.Dim)
                        )
                        AppText(
                            if (status.running) "Live" else "Off",
                            style = UpType.tiny.copy(fontSize = 10.sp, fontWeight = FontWeight.W600),
                            color = Upcroc.Muted,
                        )
                    }
                }
            }
        }

        if (!auth.signedIn) {
            item {
                Spacer(Modifier.height(10.dp))
                Banner(
                    icon = UiIcon.Key,
                    title = "Sign in to Upwork",
                    body = "Upcroc needs a session before it can check for jobs.",
                    action = "Sign in",
                    onAction = vm::signIn,
                    tone = BannerTone.Warning,
                )
            }
        }
        if (!notificationsOn) {
            item {
                Spacer(Modifier.height(10.dp))
                Banner(
                    icon = UiIcon.Bell,
                    title = "Notifications are off",
                    body = "Checks still run, but Android will not show job alerts.",
                    action = "Fix",
                    onAction = { notifyHelp = true },
                    tone = BannerTone.Danger,
                )
            }
        }
        val lastCheck = status.lastCheckAt
        if (status.running && lastCheck != null && lastCheck > 0L && now - lastCheck > staleAfterMs) {
            item {
                Spacer(Modifier.height(10.dp))
                Banner(
                    icon = UiIcon.Clock,
                    title = "Checks are delayed",
                    body = "Last check was ${Fmt.lastSeen(lastCheck, now)}. Android may be delaying background work.",
                    action = "Fix",
                    onAction = { backgroundHelp = true },
                    tone = BannerTone.Warning,
                )
            }
        }

        item {
            FeedSection("Your searches", "Edit", onSearch)
            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(bottom = 2.dp),
            ) {
                items(config.searches.size) { index ->
                    val group = config.searches[index]
                    SearchCard(
                        index = index,
                        label = if (group.mode == SearchMode.SMART_SEARCH) "Smart" else "Keyword",
                        name = group.name,
                        meta = "every ${Fmt.intervalRange(group.intervalSec, group.intervalUpperSec)} · " +
                            (if (group.enabled) "on" else "paused"),
                        enabled = group.enabled,
                        onClick = onSearch,
                    )
                }
            }
        }

        item {
            FeedSection("Quick actions", null, null)
            Row(Modifier.fillMaxWidth().padding(top = 11.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                QuickAction(
                    icon = if (status.running) UiIcon.Pause else UiIcon.Play,
                    contentDescription = if (status.running) "Pause watcher" else "Start watcher",
                    modifier = Modifier.weight(1f),
                ) {
                    if (!auth.signedIn) {
                        onToast("Sign in to Upwork first.")
                    } else if (status.running) {
                        vm.stopWatcher(context)
                    } else {
                        vm.startWatcher(context)
                    }
                }
                QuickAction(UiIcon.Check, "Check now", Modifier.weight(1f)) {
                    if (!auth.signedIn) onToast("Sign in to Upwork first.") else vm.checkNow(context)
                }
                QuickAction(UiIcon.Plus, "Add search", Modifier.weight(1f), onSearch)
                QuickAction(UiIcon.Health, "Diagnostics", Modifier.weight(1f), onDiagnostics)
            }
        }

        item {
            FeedSection(
                "Recent alerts",
                if (showAll && recentJobs.size > 4) "Show less" else "View all",
                {
                    if (recentJobs.size > 4) {
                        showAll = !showAll
                    } else {
                        onToast("Showing all alerts")
                    }
                },
            )
            if (recentJobs.isEmpty()) {
                val result = lastResult
                val title = when {
                    status.checking -> "Checking Upwork…"
                    result == null -> "No alerts yet"
                    result.status == "OK" && result.newJobs == 0 -> "No new matches"
                    else -> "Nothing to show"
                }
                val body = when {
                    status.checking -> "The check is still running."
                    result == null -> "Start the watcher or tap Check now. New matches land here."
                    result.status == "OK" -> {
                        "Last check ${Fmt.lastSeen(result.at, now)}. ${resultText(result)}"
                    }
                    else -> resultText(result)
                }
                Box(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(18.dp))
                        .background(Upcroc.Surface),
                ) {
                    EmptyState(
                        icon = UiIcon.Radar,
                        title = title,
                        body = body,
                    )
                }
            } else {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(18.dp))
                        .background(Upcroc.Surface)
                        .padding(horizontal = 15.dp, vertical = 3.dp),
                ) {
                    visibleJobs.forEachIndexed { index, job ->
                        JobRow(
                            job = job,
                            now = now,
                            onOpen = { onOpenJob(job.id) },
                            onSave = {
                                vm.setSaved(job.id, !job.saved)
                                onToast(if (job.saved) "Removed" else "Saved")
                            },
                        )
                        if (index < visibleJobs.lastIndex) DividerSoft()
                    }
                }
                Spacer(Modifier.height(12.dp))
                AppText(
                    if (showAll && recentJobs.size > 50) "Showing the 50 newest alerts." else "You're all caught up.",
                    style = UpType.tiny,
                    color = Upcroc.Dim,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun FeedSection(label: String, action: String?, onAction: (() -> Unit)?) {
    Spacer(Modifier.height(21.dp))
    SectionHeader(label, action, onAction)
    Spacer(Modifier.height(9.dp))
}

@Composable
private fun SearchCard(
    index: Int,
    label: String,
    name: String,
    meta: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val color = when (index) {
        0 -> Upcroc.RailBlue
        1 -> Upcroc.RailPurple
        else -> Upcroc.RailOrange
    }
    Box(
        Modifier
            .width(210.dp)
            .height(112.dp)
            .clip(RoundedCornerShape(15.dp))
            .background(color)
            .pressable(onClick),
    ) {
        Box(
            Modifier
                .align(Alignment.TopEnd)
                .offset(x = 26.dp, y = (-26).dp)
                .size(84.dp)
                .clip(CircleShape)
                .border(1.dp, Color.White.copy(alpha = 0.16f), CircleShape)
        )
        Column(Modifier.padding(start = 14.dp, end = 14.dp, top = 13.dp)) {
            AppText(
                label.uppercase(),
                style = UpType.tiny.copy(fontWeight = FontWeight.W600, letterSpacing = 0.7.sp, fontSize = 10.5.sp),
                color = Color.White.copy(alpha = 0.7f),
            )
            Spacer(Modifier.height(5.dp))
            AppText(
                name,
                style = UpType.groupTitle.copy(fontWeight = FontWeight.W700, fontSize = 14.sp),
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(4.dp))
            AppText(
                meta,
                style = UpType.tiny.copy(fontSize = 11.sp),
                color = Color.White.copy(alpha = 0.7f),
                maxLines = 1,
            )
        }
        Box(
            Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 13.dp, bottom = 13.dp)
                .size(5.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = if (enabled) 0.85f else 0.35f))
        )
    }
}

@Composable
private fun QuickAction(
    icon: UiIcon,
    contentDescription: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Box(
        modifier = modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(Upcroc.Field)
            .semantics { this.contentDescription = contentDescription }
            .pressable(onClick),
        contentAlignment = Alignment.Center,
    ) {
        IconGlyph(icon, Modifier.size(18.dp), Upcroc.Blue)
    }
}

private fun resultText(result: CheckResult): String = when (result.status) {
    "AUTH" -> "Sign in to Upwork again."
    "OFFLINE" -> "No network."
    "NETWORK" -> result.message ?: "Network error."
    "RATE_LIMIT" -> result.message ?: "Rate limited."
    "BACKOFF" -> "Waiting before the next check."
    "NO_DUE" -> "No check was needed."
    "ERROR" -> result.message ?: "Check failed."
    else -> when {
        result.newJobs == 0 -> "No new matches."
        result.notified > 0 -> "${result.newJobs} new · ${result.notified} notified."
        else -> "${result.newJobs} found · none alerted."
    }
}

private fun resultShort(result: CheckResult): String = when (result.status) {
    "OK" -> when {
        result.newJobs == 0 -> "0 new"
        result.notified > 0 -> "${result.newJobs} new · ${result.notified} notified"
        else -> "${result.newJobs} found"
    }
    "NO_DUE" -> "no check needed"
    "AUTH" -> "sign in"
    "OFFLINE" -> "offline"
    "NETWORK" -> "network error"
    "RATE_LIMIT" -> "rate limited"
    "BACKOFF" -> "backing off"
    else -> "check failed"
}

@Composable
private fun JobRow(
    job: Job,
    now: Long,
    onOpen: () -> Unit,
    onSave: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().pressable(onOpen).padding(vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(9.dp),
    ) {
        Avatar(Fmt.initials(job.title), size = 36.dp, textSize = 9.5f)
        Column(Modifier.weight(1f)) {
            AppText(
                job.title.ifBlank { "Untitled job" },
                style = UpType.bodyStrong.copy(fontWeight = FontWeight.W700, fontSize = 12.sp),
                color = Upcroc.Text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(1.dp))
            AppText(
                listOf(
                    job.budget.ifBlank { "Budget not listed" },
                    job.country.ifBlank { "Anywhere" },
                ).joinToString(" · "),
                style = UpType.tiny.copy(fontSize = 11.sp),
                color = Upcroc.Dim,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Column(horizontalAlignment = Alignment.End) {
            AppText(
                Fmt.ageShort(job.publishedAt, now),
                style = UpType.caption.copy(fontWeight = FontWeight.W800, fontSize = 11.5.sp),
                color = Upcroc.Text,
                maxLines = 1,
            )
            Spacer(Modifier.height(1.dp))
            AppText(
                job.experienceLevel.ifBlank { job.jobType.ifBlank { "New" } },
                style = UpType.tiny.copy(fontSize = 9.5.sp),
                color = Upcroc.Dim,
                maxLines = 1,
            )
            job.invitesSent?.takeIf { it > 0 }?.let { invites ->
                AppText(
                    "$invites invite${if (invites == 1) "" else "s"}",
                    style = UpType.tiny.copy(fontSize = 9.5.sp, fontWeight = FontWeight.W700),
                    color = Upcroc.Blue,
                    maxLines = 1,
                )
            }
        }
        Box(
            Modifier
                .size(28.dp)
                .pressable(onSave),
            contentAlignment = Alignment.Center,
        ) {
            IconGlyph(
                if (job.saved) UiIcon.BookmarkFilled else UiIcon.Bookmark,
                Modifier.size(15.dp),
                if (job.saved) Upcroc.Blue else Upcroc.Dim,
            )
        }
    }
}
