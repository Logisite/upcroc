package com.upcroc.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.upcroc.data.EventEntity
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val STAMP: DateTimeFormatter =
    DateTimeFormatter.ofPattern("HH:mm:ss").withZone(ZoneId.systemDefault())

/**
 * Jobs and the raw log in one place.
 *
 * The log is colour-coded by kind because the difference between a check that
 * ran and a check that failed is the entire point of reading it, and a flat list
 * of identical grey rows hides that.
 */
@Composable
fun HistoryTab(model: UiModel) {
    var which by rememberSaveable { mutableIntStateOf(1) }
    Column(Modifier.fillMaxSize()) {
        SingleChoiceSegmentedButtonRow(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            SegmentedButton(
                selected = which == 0,
                onClick = { which = 0 },
                shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
            ) { Text("Jobs") }
            SegmentedButton(
                selected = which == 1,
                onClick = { which = 1 },
                shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
            ) { Text("Log") }
        }
        if (which == 0) JobList(model) else LogList(model)
    }
}

@Composable
private fun JobList(model: UiModel) {
    val jobs by model.recentJobs.collectAsStateWithLifecycle()
    if (jobs.isEmpty()) {
        Empty(
            "No jobs cached yet",
            "upcroc checks on a timer once you start watching. Jobs are kept for " +
                "as many days as you set under Setup.",
        )
        return
    }
    LazyColumn(Modifier.fillMaxSize()) {
        items(jobs, key = { it.id }) { job ->
            Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                Text(job.title, style = MaterialTheme.typography.bodyLarge)
                Spacer(Modifier.height(4.dp))
                Text(
                    "${job.pay} ${job.jobType} · ${job.country} · ${job.experienceLevel}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(job.url, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun LogList(model: UiModel) {
    val events by model.events.collectAsStateWithLifecycle()
    var filter by rememberSaveable { mutableStateOf("") }
    val clipboard = LocalClipboardManager.current

    val kinds = remember(events) { events.map { it.kind }.distinct().sorted() }
    val ordered = remember(events, filter) {
        val picked = if (filter.isEmpty()) events else events.filter { it.kind == filter }
        picked.sortedBy { it.id }
    }
    val state = rememberLazyListState()

    // Append at the bottom, and only scroll when the reader is already there,
    // so reading back through history is never disturbed.
    LaunchedEffect(ordered.size, filter) {
        val lastVisible = state.layoutInfo.visibleItemsInfo.lastOrNull()?.index
        val atEnd = lastVisible == null || ordered.size - lastVisible < 4
        if (atEnd && ordered.isNotEmpty()) state.scrollToItem(ordered.lastIndex)
    }

    if (events.isEmpty()) {
        Empty("No activity yet", "Checks, jobs and problems are logged here as they happen.")
        return
    }

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            KindChip("all", filter.isEmpty()) { filter = "" }
            kinds.forEach { k -> KindChip(k, filter == k) { filter = if (filter == k) "" else k } }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            TextButton(onClick = {
                clipboard.setText(AnnotatedString(ordered.joinToString("\n") { line(it) }))
            }) { Text("Copy ${ordered.size} lines") }
        }
        LazyColumn(Modifier.fillMaxSize(), state) {
            items(ordered, key = { it.id }) { LogRow(it) }
        }
    }
}

@Composable
private fun KindChip(label: String, on: Boolean, onClick: () -> Unit) {
    AssistChip(
        onClick = onClick,
        label = { Text(label) },
        colors = AssistChipDefaults.assistChipColors(
            containerColor = if (on) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent,
        ),
    )
}

private fun line(e: EventEntity) =
    "${STAMP.format(Instant.ofEpochMilli(e.ts))}  ${e.kind.padEnd(7)}  ${e.title}"

@Composable
private fun LogRow(event: EventEntity) {
    val tint = kindColor(event.kind)
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 3.dp)) {
        Text(
            text = STAMP.format(Instant.ofEpochMilli(event.ts)),
            style = MaterialTheme.typography.labelSmall,
            fontFamily = FontFamily.Monospace,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(62.dp),
        )
        Box(
            Modifier
                .width(3.dp)
                .height(14.dp)
                .background(tint)
        )
        Text(
            text = event.title,
            style = MaterialTheme.typography.bodySmall,
            color = tint,
            modifier = Modifier.padding(start = 8.dp),
        )
    }
}

@Composable
private fun kindColor(kind: String): Color = when (kind) {
    "check" -> MaterialTheme.colorScheme.primary
    "job" -> MaterialTheme.colorScheme.tertiary
    "query" -> MaterialTheme.colorScheme.secondary
    "error", "enrich", "auth" -> MaterialTheme.colorScheme.error
    "service", "session" -> MaterialTheme.colorScheme.tertiary
    else -> MaterialTheme.colorScheme.onSurfaceVariant
}

@Composable
private fun Empty(title: String, body: String) {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        Text(
            body,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}