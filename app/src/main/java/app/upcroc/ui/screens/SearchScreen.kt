package app.upcroc.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.upcroc.core.model.SearchFilters
import app.upcroc.core.model.SearchGroup
import app.upcroc.core.model.SearchMode
import app.upcroc.core.model.defaultGroups
import app.upcroc.ui.MainViewModel
import app.upcroc.ui.components.AppDialog
import app.upcroc.ui.components.AppText
import app.upcroc.ui.components.Avatar
import app.upcroc.ui.components.Chip
import app.upcroc.ui.components.DialogAction
import app.upcroc.ui.components.DividerSoft
import app.upcroc.ui.components.FilterPanel
import app.upcroc.ui.components.GhostButton
import app.upcroc.ui.components.IconButton40
import app.upcroc.ui.icons.IconGlyph
import app.upcroc.ui.components.ModalSheet
import app.upcroc.ui.components.NumberField
import app.upcroc.ui.components.PrimaryButton
import app.upcroc.ui.components.SectionHeader
import app.upcroc.ui.components.Segmented
import app.upcroc.ui.components.SwitchRow
import app.upcroc.ui.components.TopBar
import app.upcroc.ui.components.UpCard
import app.upcroc.ui.components.UpField
import app.upcroc.ui.components.UpSwitch
import app.upcroc.ui.components.pressable
import app.upcroc.ui.icons.UiIcon
import app.upcroc.ui.theme.UpType
import app.upcroc.ui.theme.Upcroc
import app.upcroc.ui.util.Fmt
import app.upcroc.ui.util.rememberNow
import kotlin.math.roundToInt

/**
 * Search screen, ported from prototype/search.html + search.css. The two-panel
 * segmented control, three slots, shared filters, quota ring, and recent checks
 * all use the real draft and check history.
 */
@Composable
fun SearchScreen(
    vm: MainViewModel,
    onLayer0: () -> Unit,
    onDiagnostics: () -> Unit,
    onToast: (String) -> Unit,
) {
    val config by vm.config.collectAsStateWithLifecycle()
    val draft by vm.draft.collectAsStateWithLifecycle()
    val status by vm.status.collectAsStateWithLifecycle()
    val snapshot by vm.snapshot.collectAsStateWithLifecycle()
    val checks by vm.recentChecks.collectAsStateWithLifecycle()
    val latency by vm.latency.collectAsStateWithLifecycle()
    val jobs by vm.jobs.collectAsStateWithLifecycle()
    val saved by vm.savedJobs.collectAsStateWithLifecycle()
    val now = rememberNow()

    var panel by rememberSaveable { mutableIntStateOf(0) }
    var groups by remember { mutableStateOf(padGroups(draft.groups)) }
    var filters by remember { mutableStateOf(draft.filters) }
    var groupEditor by remember { mutableStateOf<Int?>(null) }
    var filterSheet by remember { mutableStateOf(false) }
    var numberTarget by remember { mutableStateOf<NumberTarget?>(null) }
    var numberText by remember { mutableStateOf("") }

    LaunchedEffect(draft.updatedAt) {
        groups = padGroups(draft.groups)
        filters = draft.filters
    }
    LaunchedEffect(Unit) { vm.refreshDiagnostics() }

    fun updateGroup(index: Int, transform: (SearchGroup) -> SearchGroup) {
        groups = groups.mapIndexed { i, group -> if (i == index) transform(group) else group }
    }

    fun addGroup() {
        val free = groups.indexOfFirst { it.mode == SearchMode.SEARCH && it.keywords.isBlank() }
        if (free < 0) {
            onToast("Three search slots are already in use.")
            return
        }
        groupEditor = free
    }

    fun openNumber(target: NumberTarget) {
        numberTarget = target
        numberText = when (target) {
            NumberTarget.Proposals -> filters.proposalsMax?.toString().orEmpty()
            NumberTarget.Hires -> filters.clientHiresMin?.toString().orEmpty()
        }
    }

    val used = status.requestsToday.takeIf { it > 0 } ?: snapshot?.budget?.dayCount ?: 0
    val limit = config.budget.dailyLimit.coerceAtLeast(1)
    val todayStart = remember(now) {
        val cal = java.util.Calendar.getInstance()
        cal.set(java.util.Calendar.HOUR_OF_DAY, 0)
        cal.set(java.util.Calendar.MINUTE, 0)
        cal.set(java.util.Calendar.SECOND, 0)
        cal.set(java.util.Calendar.MILLISECOND, 0)
        cal.timeInMillis
    }
    val todayChecks = checks.filter { it.startedAt >= todayStart }
    val smartCount = todayChecks.filter { check ->
        groups.firstOrNull { it.id == check.groupId }?.mode == SearchMode.SMART_SEARCH
    }.sumOf { it.requests }
    val keywordCount = todayChecks.filter { check ->
        groups.firstOrNull { it.id == check.groupId }?.mode == SearchMode.SEARCH
    }.sumOf { it.requests }
    val enrichCount = (used - smartCount - keywordCount).coerceAtLeast(0)
    val alertsThisWeek = jobs.count {
        it.notifiedAt != null && it.notifiedAt!! >= now - 7 * 86_400_000L
    }

    LazyColumn(
        modifier = Modifier.fillMaxWidth().statusBarsPadding(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 112.dp),
    ) {
        item {
            TopBar(
                kicker = "${groups.size} groups · shared filters",
                title = "Search",
                avatar = "SC",
                actions = { IconButton40(UiIcon.Layers, "Layer 0", onLayer0) },
            )
        }
        item {
            Segmented(
                options = listOf("Searches", "Quota"),
                selected = panel,
                onSelect = { panel = it },
            )
        }

        if (panel == 0) {
            item {
                Spacer(Modifier.height(21.dp))
                SectionHeader("Search groups", "Add", ::addGroup)
                Spacer(Modifier.height(11.dp))
                Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    groups.forEachIndexed { index, group ->
                        GroupCard(
                            number = index + 1,
                            group = group,
                            onOpen = { groupEditor = index },
                            onToggle = { enabled -> updateGroup(index) { it.copy(enabled = enabled) } },
                        )
                    }
                }
            }

            item {
                Spacer(Modifier.height(10.dp))
                UpCard {
                    AppText("Core filters", style = UpType.section, color = Upcroc.Text)
                    Row(
                        Modifier.fillMaxWidth().padding(top = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Chip(
                            "Hourly",
                            active = filters.jobType == "Hourly",
                            onClick = {
                                filters = filters.copy(jobType = if (filters.jobType == "Hourly") null else "Hourly")
                            },
                        )
                        Chip(
                            "Fixed",
                            active = filters.jobType == "Fixed",
                            onClick = {
                                filters = filters.copy(jobType = if (filters.jobType == "Fixed") null else "Fixed")
                            },
                        )
                        Chip(
                            "Expert",
                            active = filters.experienceLevel == "Expert",
                            onClick = {
                                filters = filters.copy(
                                    experienceLevel = if (filters.experienceLevel == "Expert") null else "Expert"
                                )
                            },
                        )
                        Chip(
                            "$40+/hr",
                            active = filters.rateMin == 40.0,
                            onClick = {
                                filters = filters.copy(rateMin = if (filters.rateMin == 40.0) null else 40.0)
                            },
                        )
                    }
                    Row(
                        Modifier.fillMaxWidth().padding(top = 7.dp),
                        horizontalArrangement = Arrangement.spacedBy(7.dp),
                    ) {
                        FilterBox(
                            label = "Proposals",
                            value = filters.proposalsMax?.let { "≤ $it" } ?: "Any",
                            modifier = Modifier.weight(1f),
                            onClick = { openNumber(NumberTarget.Proposals) },
                        )
                        FilterBox(
                            label = "Client hires",
                            value = filters.clientHiresMin?.let { "$it+" } ?: "Any",
                            modifier = Modifier.weight(1f),
                            onClick = { openNumber(NumberTarget.Hires) },
                        )
                    }
                    Spacer(Modifier.height(7.dp))
                    FilterBox(
                        label = "More filters",
                        value = filterSummary(filters),
                        modifier = Modifier.fillMaxWidth(),
                        chevron = true,
                        onClick = { filterSheet = true },
                    )
                }
                PrimaryButton(
                    text = "Save search",
                    onClick = { vm.applySearch(groups, filters) },
                    modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                )
            }

            item {
                Spacer(Modifier.height(21.dp))
                SectionHeader("Recent checks", "View all", onDiagnostics)
                Spacer(Modifier.height(9.dp))
                UpCard(padding = PaddingValues(horizontal = 15.dp, vertical = 4.dp)) {
                    val recent = checks.take(2)
                    if (recent.isEmpty()) {
                        AppText(
                            "No checks yet. Checks appear here after the watcher runs.",
                            style = UpType.small,
                            color = Upcroc.Muted,
                            modifier = Modifier.padding(vertical = 12.dp),
                        )
                    } else {
                        recent.forEachIndexed { index, check ->
                            CheckRow(check.groupId, check.status, check.newJobs, check.pages, check.durationMs, index + 1, groups)
                            if (index < recent.lastIndex) DividerSoft()
                        }
                    }
                }
            }
        } else {
            item {
                Spacer(Modifier.height(10.dp))
                UpCard {
                    AppText("Requests today", style = UpType.section, color = Upcroc.Text)
                    Spacer(Modifier.height(2.dp))
                    AppText("Soft limit · resets at midnight", style = UpType.tiny.copy(fontSize = 10.sp), color = Upcroc.Dim)
                    Box(
                        Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 2.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Box(Modifier.size(166.dp), contentAlignment = Alignment.Center) {
                            QuotaDonut(
                                smart = smartCount,
                                keyword = keywordCount,
                                enrich = enrichCount,
                                remaining = (limit - used).coerceAtLeast(0),
                                limit = limit,
                            )
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                AppText(
                                    "$used / $limit",
                                    style = UpType.donutBig,
                                    color = Upcroc.Text,
                                )
                                AppText(
                                    "REQUESTS TODAY",
                                    style = UpType.tiny.copy(fontSize = 10.sp, letterSpacing = 0.6.sp),
                                    color = Upcroc.Dim,
                                )
                            }
                        }
                    }
                    Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Column(Modifier.weight(1f)) {
                            LegendRow(Upcroc.Blue, "Smart", smartCount)
                            Spacer(Modifier.height(6.dp))
                            LegendRow(Upcroc.Purple, "Keyword", keywordCount)
                        }
                        Column(Modifier.weight(1f)) {
                            LegendRow(Upcroc.Orange, "Enrich", enrichCount)
                            Spacer(Modifier.height(6.dp))
                            LegendRow(Color(0xFF3A3A3A), "Remaining", (limit - used).coerceAtLeast(0))
                        }
                    }
                    Row(Modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        StatBox(
                            value = "$alertsThisWeek",
                            label = "alerts this week",
                            modifier = Modifier.weight(1f),
                        )
                        StatBox(
                            value = latency?.let { Fmt.countdown(it.p50Sec * 1000L) } ?: "-",
                            label = "p50 latency",
                            modifier = Modifier.weight(1f),
                        )
                    }
                    Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        StatBox(
                            value = "${saved.size}",
                            label = "saved jobs",
                            modifier = Modifier.weight(1f),
                        )
                        StatBox(
                            value = "${snapshot?.failures ?: 0}",
                            label = "failures",
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }

            item {
                Spacer(Modifier.height(10.dp))
                UpCard(padding = PaddingValues(horizontal = 15.dp, vertical = 15.dp)) {
                    AppText("Recent checks", style = UpType.section, color = Upcroc.Text)
                    Spacer(Modifier.height(4.dp))
                    val recent = checks.take(3)
                    if (recent.isEmpty()) {
                        AppText(
                            "No checks yet.",
                            style = UpType.small,
                            color = Upcroc.Muted,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                    } else {
                        recent.forEachIndexed { index, check ->
                            CheckRow(check.groupId, check.status, check.newJobs, check.pages, check.durationMs, index + 1, groups)
                            if (index < recent.lastIndex) DividerSoft()
                        }
                    }
                }
            }
        }
    }

    if (filterSheet) {
        ModalSheet(visible = true, onDismiss = { filterSheet = false }) {
            FilterPanel(
                filters = filters,
                onChange = { filters = it },
                onApply = {
                    filterSheet = false
                    onToast("Filters ready. Tap Save search to apply them.")
                },
                onReset = { filters = SearchFilters() },
            )
        }
    }

    val editing = groupEditor
    if (editing != null) {
        ModalSheet(visible = true, onDismiss = { groupEditor = null }) {
            val group = groups.getOrNull(editing)
            if (group != null) {
                AppText("Search slot ${editing + 1}", style = UpType.section, color = Upcroc.Text)
                Spacer(Modifier.height(3.dp))
                AppText(
                    if (group.mode == SearchMode.SMART_SEARCH) {
                        "Smart search looks at the newest jobs that match your profile."
                    } else {
                        "Keyword search checks one exact phrase across titles."
                    },
                    style = UpType.small,
                    color = Upcroc.Muted,
                )
                Spacer(Modifier.height(14.dp))
                UpField(
                    label = "Name",
                    value = group.name,
                    onValueChange = { value -> updateGroup(editing) { it.copy(name = value) } },
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                UpField(
                    label = if (group.mode == SearchMode.SMART_SEARCH) "Title filter (optional)" else "Keywords",
                    value = group.keywords,
                    onValueChange = { value -> updateGroup(editing) { it.copy(keywords = value) } },
                    placeholder = if (group.mode == SearchMode.SMART_SEARCH) "Leave blank for profile matches" else "AI automation",
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(14.dp))
                AppText(
                    "CHECK EVERY",
                    style = UpType.tiny.copy(fontWeight = FontWeight.W700, letterSpacing = 0.6.sp),
                    color = Upcroc.Dim,
                )
                Row(
                    Modifier.fillMaxWidth().padding(top = 7.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    listOf(60, 90, 120, 180, 300).forEach { seconds ->
                        Chip(
                            label = if (seconds == 90) "90s" else Fmt.interval(seconds),
                            active = group.intervalSec == seconds && group.intervalUpperSec == seconds,
                            onClick = {
                                updateGroup(editing) { it.copy(intervalSec = seconds, intervalMaxSec = seconds) }
                            },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    NumberField(
                        label = "Min gap (s)",
                        value = group.intervalSec.toDouble(),
                        onValueChange = { value ->
                            val min = (value ?: group.intervalSec.toDouble()).toInt().coerceIn(30, 300)
                            updateGroup(editing) {
                                it.copy(
                                    intervalSec = min,
                                    intervalMaxSec = if (it.intervalMaxSec > min) it.intervalMaxSec else min,
                                )
                            }
                        },
                        modifier = Modifier.weight(1f),
                        integer = true,
                    )
                    NumberField(
                        label = "Max gap (s)",
                        value = group.intervalUpperSec.toDouble(),
                        onValueChange = { value ->
                            val max = (value ?: group.intervalUpperSec.toDouble()).toInt()
                                .coerceIn(group.intervalSec, 300)
                            updateGroup(editing) {
                                it.copy(intervalMaxSec = max)
                            }
                        },
                        modifier = Modifier.weight(1f),
                        integer = true,
                    )
                }
                Spacer(Modifier.height(6.dp))
                AppText(
                    "Checks land at a random gap in this range. Five minutes is the maximum.",
                    style = UpType.small,
                    color = Upcroc.Dim,
                )
                Spacer(Modifier.height(14.dp))
                SwitchRow(
                    title = "Enabled",
                    subtitle = "Paused slots keep their settings and make no requests.",
                    checked = group.enabled,
                    onCheckedChange = { enabled -> updateGroup(editing) { it.copy(enabled = enabled) } },
                    background = Upcroc.SurfaceHigh,
                )
                Spacer(Modifier.height(18.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    GhostButton("Close", { groupEditor = null }, Modifier.weight(1f))
                    PrimaryButton("Done", { groupEditor = null }, Modifier.weight(1f), icon = UiIcon.Check)
                }
                Spacer(Modifier.height(4.dp))
            }
        }
    }

    val target = numberTarget
    if (target != null) {
        AppDialog(
            onDismissRequest = { numberTarget = null },
            title = when (target) {
                NumberTarget.Proposals -> "Maximum proposals"
                NumberTarget.Hires -> "Minimum client hires"
            },
            body = {
                AppText(
                    "Leave it blank for any value.",
                    style = UpType.small,
                    color = Upcroc.Muted,
                )
                Spacer(Modifier.height(9.dp))
                UpField(
                    label = "Value",
                    value = numberText,
                    onValueChange = { value -> numberText = value.filter { it.isDigit() } },
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            buttons = {
                DialogAction("Cancel", { numberTarget = null }, Modifier.weight(1f))
                DialogAction(
                    "Save",
                    {
                        val value = numberText.toIntOrNull()
                        filters = when (target) {
                            NumberTarget.Proposals -> filters.copy(proposalsMax = value)
                            NumberTarget.Hires -> filters.copy(clientHiresMin = value)
                        }
                        numberTarget = null
                    },
                    Modifier.weight(1f),
                    filled = true,
                )
            },
        )
    }
}

private enum class NumberTarget { Proposals, Hires }



private fun padGroups(input: List<SearchGroup>): List<SearchGroup> {
    if (input.size >= 3) return input.take(3)
    return input + defaultGroups().drop(input.size)
}

@Composable
private fun GroupCard(
    number: Int,
    group: SearchGroup,
    onOpen: () -> Unit,
    onToggle: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(Upcroc.CardSoft)
            .pressable(onOpen)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(9.dp),
    ) {
        Box(
            Modifier
                .size(24.dp)
                .clip(RoundedCornerShape(7.dp))
                .background(if (group.enabled) Upcroc.BlueSoft else Color(0xFF1E1E1E)),
            contentAlignment = Alignment.Center,
        ) {
            AppText(
                "$number",
                style = UpType.caption.copy(fontWeight = FontWeight.W800, fontSize = 11.sp),
                color = if (group.enabled) Upcroc.Blue else Upcroc.Dim,
            )
        }
        Column(Modifier.weight(1f)) {
            AppText(
                group.name,
                style = UpType.groupTitle,
                color = Upcroc.Text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            AppText(
                "${if (group.mode == SearchMode.SMART_SEARCH) "Smart" else "Keyword"} · " +
                    if (group.enabled) {
                        "every ${Fmt.intervalRange(group.intervalSec, group.intervalUpperSec)}"
                    } else {
                        "paused"
                    },
                style = UpType.tiny.copy(fontSize = 10.5.sp),
                color = Upcroc.Dim,
            )
            Spacer(Modifier.height(2.dp))
            Row {
                AppText("Keywords ", style = UpType.tiny.copy(fontSize = 10.5.sp), color = Upcroc.Dim)
                AppText(
                    group.keywords.ifBlank { "Optional" },
                    style = UpType.tiny.copy(fontSize = 10.5.sp),
                    color = if (group.keywords.isBlank()) Upcroc.Dim else Upcroc.Muted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        UpSwitch(checked = group.enabled, onCheckedChange = onToggle)
    }
}

@Composable
private fun FilterBox(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    chevron: Boolean = false,
    onClick: () -> Unit,
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(Upcroc.Field)
            .pressable(onClick)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            AppText(
                label,
                style = UpType.tiny.copy(fontWeight = FontWeight.W600),
                color = Upcroc.Muted,
            )
            AppText(
                value,
                style = UpType.bodyStrong,
                color = Upcroc.Text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (chevron) IconGlyph(UiIcon.Chevron, Modifier.size(15.dp), Upcroc.Dim)
    }
}

@Composable
private fun QuotaDonut(
    smart: Int,
    keyword: Int,
    enrich: Int,
    remaining: Int,
    limit: Int,
) {
    Canvas(Modifier.size(166.dp)) {
        val stroke = 12.dp.toPx()
        val radius = (size.minDimension - stroke) / 2f
        val center = Offset(size.width / 2f, size.height / 2f)
        drawCircle(
            color = Color(0xFF1C1C1C),
            radius = radius,
            center = center,
            style = Stroke(stroke),
        )
        var start = -90f
        listOf(
            smart to Upcroc.Blue,
            keyword to Upcroc.Purple,
            enrich to Upcroc.Orange,
        ).forEach { (count, color) ->
            val sweep = 360f * count.toFloat() / limit.toFloat().coerceAtLeast(1f)
            if (sweep > 0f) {
                drawArc(
                    color = color,
                    startAngle = start,
                    sweepAngle = sweep,
                    useCenter = false,
                    topLeft = Offset(center.x - radius, center.y - radius),
                    size = Size(radius * 2f, radius * 2f),
                    style = Stroke(stroke),
                )
                start += sweep
            }
        }
    }
}

@Composable
private fun LegendRow(color: Color, label: String, value: Int) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(Modifier.size(6.dp).clip(CircleShape).background(color))
        AppText(label, style = UpType.tiny.copy(fontSize = 10.5.sp), color = Upcroc.Muted)
        Spacer(Modifier.weight(1f))
        AppText(
            "$value",
            style = UpType.tiny.copy(fontSize = 10.5.sp, fontWeight = FontWeight.W700),
            color = Upcroc.Text,
            textAlign = TextAlign.End,
        )
    }
}

@Composable
private fun StatBox(value: String, label: String, modifier: Modifier = Modifier) {
    Column(
        modifier
            .clip(RoundedCornerShape(10.dp))
            .background(Upcroc.SurfaceHigh)
            .padding(horizontal = 9.dp, vertical = 8.dp),
    ) {
        AppText(value, style = UpType.statBig.copy(fontSize = 15.sp), color = Upcroc.Text, maxLines = 1)
        AppText(
            label.uppercase(),
            style = UpType.tiny.copy(fontSize = 9.5.sp, letterSpacing = 0.5.sp),
            color = Upcroc.Dim,
            maxLines = 1,
        )
    }
}

@Composable
private fun CheckRow(
    groupId: String,
    status: String,
    newJobs: Int,
    pages: Int,
    durationMs: Long,
    number: Int,
    groups: List<SearchGroup>,
) {
    val group = groups.firstOrNull { it.id == groupId }
    val ok = status.equals("OK", ignoreCase = true)
    Row(
        Modifier.fillMaxWidth().padding(vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(9.dp),
    ) {
        Avatar(
            "$number",
            size = 30.dp,
            textSize = 8.5f,
            color = if (ok) Upcroc.Blue else Upcroc.BannerDanger,
        )
        Column(Modifier.weight(1f)) {
            AppText(
                group?.name ?: groupId,
                style = UpType.caption.copy(fontWeight = FontWeight.W700, fontSize = 11.sp),
                color = Upcroc.Text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            AppText(
                if (ok) {
                    "${Fmt.plural(newJobs, "new")} · ${Fmt.plural(pages, "page")} · ${Fmt.durationMs(durationMs)}"
                } else {
                    status.lowercase().replace('_', ' ')
                },
                style = UpType.tiny.copy(fontSize = 10.sp),
                color = Upcroc.Dim,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        AppText(
            when {
                ok -> "OK"
                status.contains("RATE", ignoreCase = true) -> "LIMIT"
                else -> "ERR"
            },
            style = UpType.tiny.copy(fontSize = 10.sp, fontWeight = FontWeight.W700),
            color = if (ok) Upcroc.Green else Upcroc.BannerDanger,
        )
    }
}

private fun filterSummary(filters: SearchFilters): String {
    val parts = buildList {
        filters.clientLocation?.takeIf { it.isNotBlank() }?.let { add(it) }
        if (filters.budgetMin != null || filters.budgetMax != null) add("Budget")
        if (filters.includeKeywords.isNotBlank()) add("Words")
        filters.duration?.let { add(it) }
        if (filters.verifiedPaymentOnly) add("Verified")
    }
    return parts.ifEmpty { listOf("Location", "budget", "words", "duration") }.joinToString(", ")
}
