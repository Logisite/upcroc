package app.upcroc.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.upcroc.core.model.Job
import app.upcroc.ui.MainViewModel
import app.upcroc.ui.components.AppText
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.upcroc.ui.components.Avatar
import app.upcroc.ui.components.Banner
import app.upcroc.ui.components.BannerTone
import app.upcroc.ui.components.Chip
import app.upcroc.ui.components.EmptyState
import app.upcroc.ui.components.IconButton40
import app.upcroc.ui.icons.IconGlyph
import app.upcroc.ui.components.PageHeader
import app.upcroc.ui.components.PrimaryButton
import app.upcroc.ui.components.Spinner
import app.upcroc.ui.components.ToastHost
import app.upcroc.ui.components.pressable
import app.upcroc.ui.components.rememberToastState
import app.upcroc.ui.icons.UiIcon
import app.upcroc.ui.theme.UpType
import app.upcroc.ui.theme.Upcroc
import app.upcroc.ui.util.Fmt
import app.upcroc.ui.util.rememberNow

/**
 * Job detail, ported from prototype/job.html. Only fields that exist on the
 * posting are shown; there is no invented client name or screening questions.
 * The bottom bar saves, hides or opens the job, and nothing writes to Upwork.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun JobDetailScreen(vm: MainViewModel, jobId: String, onBack: () -> Unit) {
    val context = LocalContext.current
    val config by vm.config.collectAsStateWithLifecycle()
    val now = rememberNow(30_000L)
    val toast = rememberToastState()
    var job by remember(jobId) { mutableStateOf<Job?>(null) }
    var loading by remember(jobId) { mutableStateOf(true) }
    var saved by remember(jobId) { mutableStateOf(false) }

    LaunchedEffect(jobId) {
        job = vm.jobById(jobId)
        saved = job?.saved == true
        loading = false
        vm.updateUiState { it.copy(selectedJobId = jobId) }
    }

    fun openJob(current: Job) {
        if (current.url.isBlank()) {
            toast.show("This job has no link.")
            return
        }
        runCatching {
            context.startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse(current.url))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
            toast.show("Opened on Upwork.")
        }.onFailure { toast.show("Could not open the link.") }
    }

    Box(Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(start = 16.dp, end = 16.dp, top = 2.dp, bottom = 112.dp),
        ) {
            PageHeader(
                kicker = config.searches.firstOrNull()?.name?.takeIf { it.isNotBlank() } ?: "Matches",
                title = "Job alert",
                onBack = onBack,
                actions = {
                    IconButton40(
                        icon = UiIcon.Copy,
                        contentDescription = "Copy link",
                        onClick = {
                            val current = job
                            if (current == null || current.url.isBlank()) {
                                toast.show("This job has no link.")
                            } else {
                                val clipboard = context.getSystemService(ClipboardManager::class.java)
                                clipboard?.setPrimaryClip(ClipData.newPlainText("Job link", current.url))
                                toast.show("Link copied")
                            }
                        },
                    )
                    IconButton40(
                        icon = UiIcon.External,
                        contentDescription = "Open on Upwork",
                        onClick = { job?.let(::openJob) },
                    )
                },
            )

            when {
                loading -> Box(Modifier.fillMaxWidth().padding(vertical = 60.dp), contentAlignment = Alignment.Center) {
                    Spinner(size = 24.dp)
                }

                job == null -> EmptyState(
                    icon = UiIcon.Search,
                    title = "This job is no longer stored.",
                    body = "It may have been removed by retention. New alerts are still watched.",
                )

                else -> {
                    val current = job!!
                    AppText(
                        current.title.ifBlank { "Untitled job" },
                        style = UpType.screenTitle.copy(
                            fontSize = 22.sp,
                            lineHeight = 27.sp,
                            letterSpacing = (-0.6).sp,
                        ),
                        color = Upcroc.Text,
                    )
                    Spacer(Modifier.height(7.dp))
                    JobSubLine(current, now)
                    Spacer(Modifier.height(12.dp))
                    PayLine(current)
                    Spacer(Modifier.height(10.dp))
                    JobChips(current)
                    Spacer(Modifier.height(16.dp))
                    ClientCard(current)
                    if (current.description.isNotBlank()) {
                        SectionTitle("About the job")
                        DescriptionBody(Fmt.plain(current.description))
                    }
                    if (current.skills.isNotEmpty()) {
                        SectionTitle("Skills")
                        FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            current.skills.forEach { skill ->
                                Chip(skill)
                            }
                        }
                    }
                    Spacer(Modifier.height(20.dp))
                    Banner(
                        icon = UiIcon.Shield,
                        title = "Upcroc never applies for you.",
                        body = "This screen is read-only; the button below opens the job on Upwork.",
                        tone = BannerTone.Ok,
                    )
                }
            }
        }

        if (job != null) {
            val current = job!!
            Row(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.92f))
                    .navigationBarsPadding()
                    .padding(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                BarIcon(
                    icon = if (saved) UiIcon.BookmarkFilled else UiIcon.Bookmark,
                    description = if (saved) "Remove save" else "Save job",
                    active = saved,
                ) {
                    saved = !saved
                    vm.setSaved(current.id, saved)
                    toast.show(if (saved) "Job saved." else "Save removed.")
                }
                BarIcon(UiIcon.Close, "Hide job") {
                    vm.hideJob(current.id)
                    toast.show("Job hidden")
                    onBack()
                }
                PrimaryButton(
                    text = "Open on Upwork",
                    onClick = { openJob(current) },
                    modifier = Modifier.weight(1f),
                )
            }
        }

        ToastHost(
            state = toast,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 104.dp),
        )
    }
}

@Composable
private fun JobSubLine(job: Job, now: Long) {
    val parts = buildList {
        if (job.publishedAt > 0L) add("Posted ${Fmt.age(job.publishedAt, now)}")
        if (job.country.isNotBlank()) add(job.country)
        if (job.experienceLevel.isNotBlank()) add(job.experienceLevel)
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        parts.forEachIndexed { index, part ->
            if (index > 0) {
                Box(Modifier.size(2.5.dp).clip(CircleShape).background(Upcroc.Faint))
            }
            AppText(part, style = UpType.caption, color = Upcroc.Muted, maxLines = 1)
        }
    }
}

@Composable
private fun PayLine(job: Job) {
    val pay = job.budget.ifBlank {
        when {
            job.isHourly -> "Hourly"
            job.jobType.isNotBlank() -> job.jobType.replaceFirstChar { it.uppercase() }
            else -> "Budget not listed"
        }
    }
    val suffix = if (job.budget.isNotBlank()) {
        if (job.isHourly) "Hourly" else "Fixed price"
    } else {
        ""
    }
    Row(verticalAlignment = Alignment.Bottom) {
        AppText(
            pay,
            style = UpType.screenTitle.copy(
                fontSize = 23.sp,
                lineHeight = 26.sp,
                letterSpacing = (-0.8).sp,
            ),
            color = Upcroc.Text,
            maxLines = 1,
        )
        if (suffix.isNotBlank()) {
            AppText(
                " · $suffix",
                style = UpType.tiny.copy(fontWeight = FontWeight.W600),
                color = Upcroc.Muted,
                modifier = Modifier.padding(bottom = 3.dp),
                maxLines = 1,
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun JobChips(job: Job) {
    val chips = buildList {
        if (job.jobType.isNotBlank()) add(job.jobType.replaceFirstChar { it.uppercase() })
        if (job.experienceLevel.isNotBlank()) add(job.experienceLevel)
        if (job.proposalsTier.isNotBlank()) add(job.proposalsTier)
        job.connectsCost?.takeIf { it.isNotBlank() }?.let { add("$it connects") }
    }
    if (chips.isEmpty()) return
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        chips.forEachIndexed { index, label ->
            Chip(label, active = index == 0)
        }
    }
}

@Composable
private fun ClientCard(job: Job) {
    val meta = buildList {
        if (job.country.isNotBlank()) add(job.country)
        job.jobsPosted?.let { add("$it jobs posted") }
        job.spendTotal?.let { add("${Fmt.money(it)} spent") }
    }.joinToString(" · ")
    val facts = buildList {
        job.feedbackScore?.let { score ->
            add(if (score % 1.0 == 0.0) score.toInt().toString() else score.toString())
        }
        job.hireRate?.let { add("$it% hire rate") }
        job.totalHired?.let { add("$it hired") }
        job.invitesSent?.takeIf { it > 0 }?.let {
            add("$it invite${if (it == 1) "" else "s"} sent")
        }
        job.invitedToInterview?.takeIf { it > 0 }?.let { add("$it interviewing") }
    }
    val initials = job.country.trim().take(2).uppercase().ifBlank { "UP" }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Upcroc.Card)
            .padding(13.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(11.dp),
    ) {
        Avatar(initials, size = 40.dp, textSize = 10f)
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                AppText("Client", style = UpType.rowTitle, color = Upcroc.Text, maxLines = 1)
                IconGlyph(UiIcon.Shield, Modifier.size(12.dp), Upcroc.Blue)
            }
            if (meta.isNotBlank()) {
                AppText(meta, style = UpType.tiny.copy(fontSize = 11.sp), color = Upcroc.Muted, maxLines = 2)
            }
            if (facts.isNotEmpty()) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                    IconGlyph(UiIcon.Star, Modifier.size(11.dp), Upcroc.Orange)
                    AppText(
                        facts.joinToString(" · "),
                        style = UpType.tiny.copy(fontSize = 10.5.sp),
                        color = Upcroc.Dim,
                        maxLines = 2,
                    )
                }
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Spacer(Modifier.height(22.dp))
    AppText(text, style = UpType.groupTitle, color = Upcroc.Text)
    Spacer(Modifier.height(8.dp))
}

@Composable
private fun DescriptionBody(text: String) {
    val lines = remember(text) { text.split('\n').map { it.trim() }.filter { it.isNotEmpty() } }
    Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
        lines.forEach { line ->
            if (line.startsWith("•") || line.startsWith("-")) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    AppText("•", style = UpType.body, color = Upcroc.Faint)
                    AppText(
                        line.removePrefix("•").removePrefix("-").trim(),
                        style = UpType.body.copy(lineHeight = 19.sp),
                        color = Upcroc.Muted,
                    )
                }
            } else {
                AppText(line, style = UpType.body.copy(lineHeight = 19.sp), color = Upcroc.Muted)
            }
        }
    }
}

@Composable
private fun BarIcon(
    icon: UiIcon,
    description: String,
    active: Boolean = false,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(48.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(if (active) Upcroc.BlueSoft else Upcroc.Hero)
            .semantics { this.contentDescription = description }
            .pressable(onClick),
        contentAlignment = Alignment.Center,
    ) {
        IconGlyph(icon, Modifier.size(19.dp), if (active) Upcroc.Blue else Upcroc.Muted)
    }
}
