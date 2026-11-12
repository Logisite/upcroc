package app.upcroc.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.upcroc.core.config.ConfigCodec
import app.upcroc.core.config.WatcherConfig
import app.upcroc.ui.MainViewModel
import app.upcroc.ui.components.AppText
import app.upcroc.ui.components.ConfirmDialog
import app.upcroc.ui.components.GhostButton
import app.upcroc.ui.components.NumberField
import app.upcroc.ui.components.PageHeader
import app.upcroc.ui.components.PrimaryButton
import app.upcroc.ui.components.SwitchRow
import app.upcroc.ui.components.TextLink
import app.upcroc.ui.components.UpField
import app.upcroc.ui.theme.UpType
import app.upcroc.ui.theme.Upcroc

/**
 * Layer 0 is the unfiltered control room: the real interval overrides, the
 * switches that actually drive the watcher, and the raw JSON config. Anything
 * that has no effect is not shown.
 */
@Composable
fun Layer0Screen(vm: MainViewModel, onBack: () -> Unit) {
    val config by vm.config.collectAsStateWithLifecycle()
    var text by remember { mutableStateOf("") }
    var initialized by remember { mutableStateOf(false) }
    var resetOpen by remember { mutableStateOf(false) }

    LaunchedEffect(config) {
        if (!initialized) {
            text = ConfigCodec.pretty(config)
            initialized = true
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(start = 16.dp, end = 16.dp, top = 2.dp, bottom = 40.dp),
    ) {
        PageHeader(
            kicker = "System",
            title = "Layer 0",
            onBack = onBack,
        )

        AppText(
            "Raw control. Every change is validated before it applies.",
            style = UpType.muted,
            color = Upcroc.Muted,
        )

        LayerSection("Behaviour")
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SwitchRow(
                title = "Always on",
                subtitle = "Start after a reboot and keep running until Stop.",
                checked = config.advanced.alwaysOn,
                onCheckedChange = { value ->
                    vm.updateConfig { it.copy(advanced = it.advanced.copy(alwaysOn = value)) }
                },
            )
            SwitchRow(
                title = "Resume after reboot",
                subtitle = "Restart only if the watcher was running before.",
                checked = config.advanced.resumeOnBoot,
                onCheckedChange = { value ->
                    vm.updateConfig { it.copy(advanced = it.advanced.copy(resumeOnBoot = value)) }
                },
            )
            SwitchRow(
                title = "Watchdog",
                subtitle = "Re-checks when a scheduled check is missed.",
                checked = config.advanced.watchdogEnabled,
                onCheckedChange = { value ->
                    vm.updateConfig { it.copy(advanced = it.advanced.copy(watchdogEnabled = value)) }
                },
            )
            SwitchRow(
                title = "Hard quota cap",
                subtitle = "Block checks at the daily budget instead of only warning.",
                checked = config.budget.hardCap,
                onCheckedChange = { value ->
                    vm.updateConfig { it.copy(budget = it.budget.copy(hardCap = value)) }
                },
            )
            SwitchRow(
                title = "Enrich new jobs",
                subtitle = "Fetch client history and invite data for each job.",
                checked = config.notifications.enrich,
                onCheckedChange = { value ->
                    vm.updateConfig { it.copy(notifications = it.notifications.copy(enrich = value)) }
                },
            )
            SwitchRow(
                title = "Update checks",
                subtitle = "Look for a newer signed APK on launch.",
                checked = config.advanced.updateCheckEnabled,
                onCheckedChange = { value ->
                    vm.updateConfig { it.copy(advanced = it.advanced.copy(updateCheckEnabled = value)) }
                },
            )
        }

        LayerSection("Search intervals")
        AppText(
            "Seconds between checks for each slot. Minimum 30.",
            style = UpType.small,
            color = Upcroc.Muted,
        )
        Spacer(Modifier.height(8.dp))
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            config.searches.forEachIndexed { index, group ->
                AppText(
                    group.name.ifBlank { "Search ${index + 1}" },
                    style = UpType.tiny.copy(fontWeight = FontWeight.W700),
                    color = Upcroc.Dim,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    NumberField(
                        label = "Min gap (s)",
                        value = group.intervalSec.toDouble(),
                        onValueChange = { value ->
                            val min = (value ?: group.intervalSec.toDouble()).toInt().coerceIn(30, 300)
                            val next = config.searches.toMutableList()
                            next[index] = group.copy(
                                intervalSec = min,
                                intervalMaxSec = if (group.intervalMaxSec > min) group.intervalMaxSec else min,
                            )
                            vm.updateConfig { it.copy(searches = next) }
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
                            val next = config.searches.toMutableList()
                            next[index] = group.copy(
                                intervalMaxSec = max,
                            )
                            vm.updateConfig { it.copy(searches = next) }
                        },
                        modifier = Modifier.weight(1f),
                        integer = true,
                    )
                }
            }
            NumberField(
                label = "Quiet interval (s)",
                value = config.cadence.quietIntervalSec.toDouble(),
                onValueChange = { value ->
                    vm.updateConfig {
                        it.copy(cadence = it.cadence.copy(quietIntervalSec = (value ?: 900.0).toInt().coerceIn(60, 86_400)))
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                integer = true,
            )
        }

        LayerSection("Alerts")
        NumberField(
            label = "Max alert age (min)",
            value = config.notifications.lateNotifySec / 60.0,
            onValueChange = { value ->
                val minutes = (value ?: 10.0).toInt().coerceIn(1, 1440)
                vm.updateConfig {
                    it.copy(
                        notifications = it.notifications.copy(
                            lateNotifySec = (minutes * 60)
                                .coerceAtLeast(it.notifications.freshWindowSec),
                        ),
                    )
                }
            },
            modifier = Modifier.fillMaxWidth(),
            integer = true,
        )
        Spacer(Modifier.height(6.dp))
        AppText(
            "Jobs older than this are not notified. Default is 10 minutes.",
            style = UpType.small,
            color = Upcroc.Dim,
        )

        LayerSection("Budget and pages")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            NumberField(
                label = "Daily limit",
                value = config.budget.dailyLimit.toDouble(),
                onValueChange = { value ->
                    vm.updateConfig {
                        it.copy(budget = it.budget.copy(dailyLimit = (value ?: 750.0).toInt().coerceIn(1, 100_000)))
                    }
                },
                modifier = Modifier.weight(1f),
                integer = true,
            )
            NumberField(
                label = "Warning %",
                value = config.budget.warningPercent.toDouble(),
                onValueChange = { value ->
                    vm.updateConfig {
                        it.copy(budget = it.budget.copy(warningPercent = (value ?: 90.0).toInt().coerceIn(1, 100)))
                    }
                },
                modifier = Modifier.weight(1f),
                integer = true,
            )
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            NumberField(
                label = "Page size",
                value = config.advanced.pageSize.toDouble(),
                onValueChange = { value ->
                    vm.updateConfig {
                        it.copy(advanced = it.advanced.copy(pageSize = (value ?: 10.0).toInt().coerceIn(1, 10)))
                    }
                },
                modifier = Modifier.weight(1f),
                integer = true,
            )
            NumberField(
                label = "Page cap",
                value = config.advanced.pageCap.toDouble(),
                onValueChange = { value ->
                    vm.updateConfig {
                        it.copy(advanced = it.advanced.copy(pageCap = (value ?: 3.0).toInt().coerceIn(1, 50)))
                    }
                },
                modifier = Modifier.weight(1f),
                integer = true,
            )
        }
        Spacer(Modifier.height(8.dp))
        UpField(
            label = "Pagination parameter",
            value = config.advanced.pageParam,
            onValueChange = { value ->
                vm.updateConfig { it.copy(advanced = it.advanced.copy(pageParam = value.trim())) }
            },
            modifier = Modifier.fillMaxWidth(),
            placeholder = "leave blank unless the schema exposes one",
        )

        LayerSection("Raw configuration")
        AppText(
            "Full JSON. Invalid JSON is rejected and the previous config stays active.",
            style = UpType.small,
            color = Upcroc.Muted,
        )
        Spacer(Modifier.height(8.dp))
        BasicTextField(
            value = text,
            onValueChange = { text = it },
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 280.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(Upcroc.Field)
                .padding(12.dp),
            textStyle = UpType.mono.copy(color = Upcroc.Text, lineHeight = 16.sp),
            cursorBrush = SolidColor(Upcroc.Blue),
        )
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            GhostButton(
                text = "Reload",
                onClick = { text = ConfigCodec.pretty(config) },
                modifier = Modifier.weight(1f),
            )
            PrimaryButton(
                text = "Save JSON",
                onClick = { vm.saveRawConfig(text) },
                modifier = Modifier.weight(1f),
            )
        }
        Spacer(Modifier.height(10.dp))
        TextLink(
            "Reset to defaults",
            onClick = { resetOpen = true },
            color = Upcroc.Error,
            style = UpType.link,
        )

        Spacer(Modifier.height(22.dp))
        AppText(
            "Layer 0 can change cadence and budgets beyond Upwork's published limits. You are responsible for your account.",
            style = UpType.small.copy(lineHeight = 15.sp),
            color = Upcroc.Dim,
        )
    }

    if (resetOpen) {
        ConfirmDialog(
            title = "Reset to defaults?",
            body = "Searches, filters, cadence, budget and advanced settings return to their first-install values.",
            confirmLabel = "Reset",
            onConfirm = {
                resetOpen = false
                val defaults = WatcherConfig.defaults()
                vm.saveConfig(defaults) {
                    text = ConfigCodec.pretty(defaults)
                }
            },
            onDismiss = { resetOpen = false },
            danger = true,
        )
    }
}

@Composable
private fun LayerSection(label: String) {
    Spacer(Modifier.height(22.dp))
    AppText(
        label,
        style = UpType.section.copy(fontWeight = FontWeight.W800),
        color = Upcroc.Text,
    )
    Spacer(Modifier.height(8.dp))
}
