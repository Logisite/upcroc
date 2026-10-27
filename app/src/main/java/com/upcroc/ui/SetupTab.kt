package com.upcroc.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.upcroc.data.SettingsEntity

/**
 * Every tunable the engine reads. Writes go straight to the settings row, and
 * the engine re-reads that row each tick, so a change takes effect within a
 * second without restarting anything.
 */
@Composable
fun SetupTab(model: UiModel) {
    val settings by model.settings.collectAsStateWithLifecycle()
    val resident by model.resident.collectAsStateWithLifecycle()
    var draft by remember(settings) { mutableStateOf(settings) }
    fun commit(block: (SettingsEntity) -> SettingsEntity) {
        draft = block(draft)
        model.save(draft)
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Section("Process")
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            SegmentedButton(
                selected = !resident,
                onClick = { model.stopResident() },
                shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
            ) { Text("Stopped") }
            SegmentedButton(
                selected = resident,
                onClick = { model.ensureResident() },
                shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
            ) { Text("Running") }
        }
        Text(
            text = "Running keeps upcroc resident with its saved state so it survives " +
                "you leaving the app. Stopped ends that background process; the app " +
                "itself stays open.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Section("Timing")
        IntField(
            label = "Base seconds",
            value = draft.bucketBaseSec,
            supporting = "checks every ${fmt(draft.bucketBaseSec)} to " +
                "${fmt(draft.bucketBaseSec + draft.bucketJitterSec)}",
        ) { v -> commit { it.copy(bucketBaseSec = v) } }
        IntField(
            label = "Rotation seconds",
            value = draft.bucketJitterSec,
            supporting = "random extra wait added to the base each cycle",
        ) { v -> commit { it.copy(bucketJitterSec = v) } }

        Section("Paging")
        IntField(
            label = "Page cap",
            value = draft.pageCap,
            supporting = "most pages fetched per source per check",
        ) { v -> commit { it.copy(pageCap = v) } }
        IntField(
            label = "Pace milliseconds",
            value = draft.paceMs.toInt(),
            supporting = "gap between requests, which protects your account",
        ) { v -> commit { it.copy(paceMs = v.toLong()) } }

        Section("Freshness")
        IntField(
            label = "Fresh window seconds",
            value = draft.freshWindowSec,
            supporting = "a post younger than this counts as caught in time",
        ) { v -> commit { it.copy(freshWindowSec = v) } }
        IntField(
            label = "Late notify seconds",
            value = draft.lateNotifySec,
            supporting = "posts older than this are stored but never notified",
        ) { v -> commit { it.copy(lateNotifySec = v) } }

        Section("Session")
        IntField(
            label = "Session cap hours",
            value = draft.sessionCapHours,
            supporting = "watching pauses itself after this long",
        ) { v -> commit { it.copy(sessionCapHours = v) } }
        IntField(
            label = "Retention days",
            value = draft.retentionDays,
            supporting = "cached jobs older than this are deleted",
        ) { v -> commit { it.copy(retentionDays = v) } }

        Section("Notifications")
        Row(verticalAlignment = Alignment.CenterVertically) {
            Switch(
                checked = draft.notify,
                onCheckedChange = { v -> commit { it.copy(notify = v) } },
            )
            Spacer(Modifier.width(12.dp))
            Text("Notify", style = MaterialTheme.typography.bodyLarge)
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Switch(
                checked = draft.notifySound,
                enabled = draft.notify,
                onCheckedChange = { v -> commit { it.copy(notifySound = v) } },
            )
            Spacer(Modifier.width(12.dp))
            Text(
                "Sound",
                style = MaterialTheme.typography.bodyLarge,
                color = if (draft.notify) MaterialTheme.colorScheme.onSurface
                else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Switch(
                checked = draft.notifyVibrate,
                enabled = draft.notify,
                onCheckedChange = { v -> commit { it.copy(notifyVibrate = v) } },
            )
            Spacer(Modifier.width(12.dp))
            Text(
                "Vibrate",
                style = MaterialTheme.typography.bodyLarge,
                color = if (draft.notify) MaterialTheme.colorScheme.onSurface
                else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** 90 -> "1m30s". The timing formula is only legible once it has units. */
private fun fmt(seconds: Int): String {
    val s = seconds.coerceAtLeast(0)
    return if (s < 60) "${s}s" else "${s / 60}m${(s % 60).toString().padStart(2, '0')}s"
}

@Composable
private fun Section(title: String) {
    Spacer(Modifier.height(8.dp))
    Text(title, style = MaterialTheme.typography.titleMedium)
}

@Composable
internal fun IntField(
    label: String,
    value: Int,
    supporting: String = "",
    onChange: (Int) -> Unit,
) {
    var text by remember(value) { mutableStateOf(value.toString()) }
    OutlinedTextField(
        value = text,
        onValueChange = { raw ->
            val digits = raw.filter { it.isDigit() }.take(7)
            text = digits
            digits.toIntOrNull()?.let(onChange)
        },
        label = { Text(label) },
        supportingText = if (supporting.isNotEmpty()) {
            { Text(supporting) }
        } else null,
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = Modifier.fillMaxWidth(),
    )
}