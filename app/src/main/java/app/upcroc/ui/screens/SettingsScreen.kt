package app.upcroc.ui.screens

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.upcroc.BuildConfig
import app.upcroc.core.config.QuietHours
import app.upcroc.permissions.Permissions
import app.upcroc.ui.MainViewModel
import app.upcroc.ui.components.AppDialog
import app.upcroc.ui.components.AppText
import app.upcroc.ui.components.Avatar
import app.upcroc.ui.components.ConfirmDialog
import app.upcroc.ui.components.DialogAction
import app.upcroc.ui.components.GhostButton
import app.upcroc.ui.components.IconButton40
import app.upcroc.ui.components.InfoDialog
import app.upcroc.ui.components.ModalSheet
import app.upcroc.ui.components.PrimaryButton
import app.upcroc.ui.components.Segmented
import app.upcroc.ui.components.SettingRow
import app.upcroc.ui.components.StatusChip
import app.upcroc.ui.components.StatusTone
import app.upcroc.ui.components.TextLink
import app.upcroc.ui.components.TimeStepper
import app.upcroc.ui.components.ToastHost
import app.upcroc.ui.components.TopBar
import app.upcroc.ui.components.UpField
import app.upcroc.ui.components.UpSwitch
import app.upcroc.ui.components.rememberToastState
import app.upcroc.ui.icons.UiIcon
import app.upcroc.ui.theme.UpType
import app.upcroc.ui.theme.Upcroc
import app.upcroc.ui.util.Fmt

/**
 * Settings, ported from prototype/settings.html. The account card, three row
 * groups, and about card keep the prototype's copy and rhythm. Every row is
 * backed by the real config, auth state or Android permission.
 */
@Composable
fun SettingsScreen(
    vm: MainViewModel,
    onDiagnostics: () -> Unit,
    onLayer0: () -> Unit,
) {
    val context = LocalContext.current
    val config by vm.config.collectAsStateWithLifecycle()
    val auth by vm.authState.collectAsStateWithLifecycle()
    val uiState by vm.uiState.collectAsStateWithLifecycle()
    val running by vm.running.collectAsStateWithLifecycle()
    val toast = rememberToastState()

    var permissionTick by remember { mutableIntStateOf(0) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { permissionTick++ }
    val notificationsGranted = remember(permissionTick) { Permissions.notificationsEnabled(context) }
    val exactGranted = remember(permissionTick) { Permissions.exactAlarmsGranted(context) }
    val batteryExempt = remember(permissionTick) { Permissions.batteryExempt(context) }

    var accountOpen by remember { mutableStateOf(false) }
    var signOutOpen by remember { mutableStateOf(false) }
    var aboutDialog by remember { mutableStateOf<Pair<String, String>?>(null) }
    var quietOpen by remember { mutableStateOf(false) }
    var editQuiet by remember { mutableStateOf(config.cadence.quietHours) }
    var alertAgeOpen by remember { mutableStateOf(false) }
    var alertAgeText by remember { mutableStateOf("") }
    var backgroundOpen by remember { mutableStateOf(false) }
    var textSizeOpen by remember { mutableStateOf(false) }

    val notificationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { permissionTick++ }

    val ringtoneLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val uri = if (Build.VERSION.SDK_INT >= 33) {
                result.data?.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI, Uri::class.java)
            } else {
                @Suppress("DEPRECATION")
                result.data?.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI)
            }
            vm.updateConfig {
                it.copy(notifications = it.notifications.copy(soundUri = uri?.toString()))
            }
        }
    }

    fun toggleNotifications(on: Boolean) {
        if (on && !Permissions.notificationPermissionGranted(context)) {
            notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        vm.updateConfig { it.copy(notifications = it.notifications.copy(enabled = on)) }
    }

    fun toggleWatching(on: Boolean) {
        if (!on) {
            vm.stopWatcher(context)
        } else if (!auth.signedIn) {
            toast.show("Sign in to Upwork first.")
        } else {
            vm.startWatcher(context)
        }
    }

    val displayName = auth.accountName.ifBlank { "Upcroc" }
    val enabledSearches = config.searches.count { it.enabled }
    val primary = config.primaryGroup()
    val cadenceMin = primary?.intervalSec ?: config.cadence.primaryIntervalSec
    val cadenceMax = primary?.intervalUpperSec ?: cadenceMin
    val quiet = config.cadence.quietHours

    Box(Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(start = 16.dp, end = 16.dp, top = 2.dp, bottom = 112.dp),
        ) {
            TopBar(
                kicker = "Account & device",
                title = "Settings",
                actions = {
                    IconButton40(UiIcon.Edit, "Account", { accountOpen = true })
                },
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(Upcroc.Account)
                    .padding(15.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Avatar(Fmt.initials(displayName), size = 46.dp, textSize = 12f)
                Column(Modifier.weight(1f)) {
                    AppText(displayName, style = UpType.cardTitle, color = Upcroc.Text, maxLines = 1)
                    Spacer(Modifier.height(2.dp))
                    AppText(
                        if (auth.signedIn) "Signed in to Upwork" else "Not connected",
                        style = UpType.small,
                        color = Upcroc.Muted,
                        maxLines = 1,
                    )
                }
                StatusChip(
                    label = if (running) "Live" else "Paused",
                    tone = if (running) StatusTone.Live else StatusTone.Paused,
                )
            }

            SettingsGroup("Watching") {
                SettingRow(
                    title = "Watching",
                    subtitle = "${Fmt.plural(enabledSearches, "search", "searches")} · every ${Fmt.intervalRange(cadenceMin, cadenceMax)}",
                    onClick = { toggleWatching(!running) },
                    trailing = {
                        UpSwitch(checked = running, onCheckedChange = { toggleWatching(it) })
                    },
                )
                SettingRow(
                    title = "Battery",
                    subtitle = "Background reliability",
                    value = if (batteryExempt) "Unrestricted" else "Limited",
                    onClick = { backgroundOpen = true },
                )
                SettingRow(
                    title = "Exact alarms",
                    subtitle = "Needed for precise checks",
                    value = if (exactGranted) "Allowed" else "Not allowed",
                    onClick = { Permissions.openExactAlarmSettings(context) },
                )
            }

            SettingsGroup("Alerts") {
                SettingRow(
                    title = "Push notifications",
                    subtitle = if (notificationsGranted) "Banners and sound" else "Blocked in Android",
                    onClick = {
                        if (notificationsGranted) {
                            toggleNotifications(!config.notifications.enabled)
                        } else {
                            Permissions.openNotificationSettings(context)
                        }
                    },
                    trailing = {
                        UpSwitch(
                            checked = config.notifications.enabled && notificationsGranted,
                            onCheckedChange = { on ->
                                if (notificationsGranted) {
                                    toggleNotifications(on)
                                } else {
                                    Permissions.openNotificationSettings(context)
                                }
                            },
                        )
                    },
                )
                SettingRow(
                    title = "Quiet hours",
                    subtitle = if (quiet.enabled) {
                        "${Fmt.minuteOfDay(quiet.startMinute)} to ${Fmt.minuteOfDay(quiet.endMinute)}"
                    } else {
                        "Off"
                    },
                    onClick = {
                        editQuiet = quiet
                        quietOpen = true
                    },
                    trailing = {
                        UpSwitch(
                            checked = quiet.enabled,
                            onCheckedChange = { on ->
                                vm.updateConfig {
                                    it.copy(cadence = it.cadence.copy(quietHours = it.cadence.quietHours.copy(enabled = on)))
                                }
                            },
                        )
                    },
                )
                SettingRow(
                    title = "Alert age",
                    subtitle = "Notifications only, jobs older than this",
                    value = "${config.notifications.lateNotifySec / 60} min",
                    onClick = {
                        alertAgeText = (config.notifications.lateNotifySec / 60).toString()
                        alertAgeOpen = true
                    },
                )
                SettingRow(
                    title = "Alert sound",
                    subtitle = "Notification sound",
                    value = soundName(context, config.notifications.soundUri),
                    onClick = {
                        val intent = Intent(RingtoneManager.ACTION_RINGTONE_PICKER).apply {
                            putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_NOTIFICATION)
                            putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
                            putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, true)
                            putExtra(
                                RingtoneManager.EXTRA_RINGTONE_DEFAULT_URI,
                                android.provider.Settings.System.DEFAULT_NOTIFICATION_URI,
                            )
                            config.notifications.soundUri?.let {
                                putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, Uri.parse(it))
                            }
                        }
                        ringtoneLauncher.launch(intent)
                    },
                )
            }

            SettingsGroup("System") {
                SettingRow(
                    title = "Text size",
                    subtitle = "Applies across the whole app",
                    value = textScaleLabels[textScaleIndex(uiState.fontScale)],
                    onClick = { textSizeOpen = true },
                )
                SettingRow(
                    title = "Diagnostics",
                    subtitle = "Live status, logs, quota",
                    onClick = onDiagnostics,
                )
                SettingRow(
                    title = "Layer 0",
                    subtitle = "Raw config and interval overrides",
                    onClick = onLayer0,
                )
                SettingRow(
                    title = "Check for updates",
                    subtitle = if (BuildConfig.SELF_UPDATE) "Signed APK updates" else "Not available in this build",
                    onClick = if (BuildConfig.SELF_UPDATE) {
                        {
                            vm.checkForUpdates(force = true)
                            toast.show("Checking for updates")
                        }
                    } else {
                        null
                    },
                )
                SettingRow(
                    title = "Sign out",
                    subtitle = "Keep local saves and history",
                    titleColor = Upcroc.Error,
                    onClick = { signOutOpen = true },
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 20.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(Upcroc.Account)
                    .padding(15.dp),
            ) {
                AppText(
                    "Upcroc ${BuildConfig.VERSION_NAME}",
                    style = UpType.caption.copy(fontWeight = FontWeight.W800),
                    color = Upcroc.Text,
                )
                Spacer(Modifier.height(4.dp))
                AppText(
                    "Read-only job alerts. Never applies to jobs or changes anything on Upwork.",
                    style = UpType.small,
                    color = Upcroc.Muted,
                )
                Spacer(Modifier.height(11.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    TextLink(
                        "Licences",
                        onClick = {
                            aboutDialog = "Licences" to
                                "Upcroc is free software under GPL-3.0. It uses Haze (Apache-2.0), " +
                                "OkHttp (Apache-2.0), and Manrope (SIL OFL 1.1)."
                        },
                        style = UpType.caption,
                    )
                    TextLink(
                        "Source",
                        onClick = {
                            aboutDialog = "Source" to
                                "Upcroc is distributed as a signed APK. The GPL-3.0 licence gives " +
                                "you the right to the source code for this build."
                        },
                        style = UpType.caption,
                    )
                    TextLink(
                        "Privacy",
                        onClick = {
                            aboutDialog = "Privacy" to
                                "Your Upwork token is kept on this device. Job data stays in the app. " +
                                "Nothing is sent to another server."
                        },
                        style = UpType.caption,
                    )
                }
            }
        }

        ToastHost(
            state = toast,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 94.dp),
        )
    }

    if (accountOpen) {
        AppDialog(
            onDismissRequest = { accountOpen = false },
            title = "Account",
            body = {
                AppText(
                    if (auth.signedIn) {
                        "Signed in as $displayName. Upcroc has read-only access to jobs on this account."
                    } else {
                        "Not connected to Upwork. Sign in from onboarding or the alerts screen."
                    },
                    style = UpType.muted,
                    color = Upcroc.Muted,
                )
            },
            buttons = {
                DialogAction("Close", { accountOpen = false }, Modifier.weight(1f), filled = true)
            },
        )
    }

    if (signOutOpen) {
        ConfirmDialog(
            title = "Sign out?",
            body = "Local saves and history stay on this device. The watcher stops until you sign in again.",
            confirmLabel = "Sign out",
            onConfirm = {
                signOutOpen = false
                if (running) vm.stopWatcher(context)
                vm.signOut()
                toast.show("Signed out.")
            },
            onDismiss = { signOutOpen = false },
            danger = true,
        )
    }

    aboutDialog?.let { (title, body) ->
        InfoDialog(title = title, body = body, onDismiss = { aboutDialog = null })
    }

    if (alertAgeOpen) {
        AppDialog(
            onDismissRequest = { alertAgeOpen = false },
            title = "Alert age",
            body = {
                AppText(
                    "Jobs older than this are not notified. Ten minutes is the default.",
                    style = UpType.muted,
                    color = Upcroc.Muted,
                )
                Spacer(Modifier.height(10.dp))
                UpField(
                    label = "Minutes",
                    value = alertAgeText,
                    onValueChange = { alertAgeText = it.filter { ch -> ch.isDigit() } },
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
            },
            buttons = {
                DialogAction("Cancel", { alertAgeOpen = false }, Modifier.weight(1f))
                DialogAction(
                    "Save",
                    {
                        val minutes = alertAgeText.toIntOrNull()?.coerceIn(1, 1440) ?: 10
                        vm.updateConfig {
                            it.copy(
                                notifications = it.notifications.copy(
                                    lateNotifySec = (minutes * 60)
                                        .coerceAtLeast(it.notifications.freshWindowSec),
                                ),
                            )
                        }
                        alertAgeOpen = false
                    },
                    Modifier.weight(1f),
                    filled = true,
                )
            },
        )
    }

    ModalSheet(visible = textSizeOpen, onDismiss = { textSizeOpen = false }) {
        AppText("Text size", style = UpType.section, color = Upcroc.Text)
        Spacer(Modifier.height(4.dp))
        AppText(
            "Changes every screen. Default matches the designed size.",
            style = UpType.muted,
            color = Upcroc.Muted,
        )
        Spacer(Modifier.height(14.dp))
        Segmented(
            options = textScaleLabels,
            selected = textScaleIndex(uiState.fontScale),
            onSelect = { index ->
                vm.updateUiState { it.copy(fontScale = textScales[index]) }
            },
        )
        Spacer(Modifier.height(16.dp))
        AppText(
            "Preview",
            style = UpType.tiny.copy(fontWeight = FontWeight.W700),
            color = Upcroc.Dim,
        )
        Spacer(Modifier.height(4.dp))
        AppText(
            "Senior AI Automation Engineer",
            style = UpType.groupTitle,
            color = Upcroc.Text,
        )
        AppText(
            "Upwork job alerts, readable at a glance.",
            style = UpType.body,
            color = Upcroc.Muted,
        )
        Spacer(Modifier.height(16.dp))
        PrimaryButton(
            "Done",
            onClick = { textSizeOpen = false },
            modifier = Modifier.fillMaxWidth(),
        )
    }

    ModalSheet(visible = backgroundOpen, onDismiss = { backgroundOpen = false }) {
        AppText("Background reliability", style = UpType.section, color = Upcroc.Text)
        Spacer(Modifier.height(6.dp))
        AppText(
            if (batteryExempt) {
                "Battery is unrestricted. If checks still pause, allow autostart and lock Upcroc in the recent apps list."
            } else {
                "Android is restricting background work. Set Upcroc to Unrestricted first."
            },
            style = UpType.muted,
            color = Upcroc.Muted,
        )
        Spacer(Modifier.height(10.dp))
        AppText(
            Permissions.oemInstructions(),
            style = UpType.small.copy(lineHeight = 15.sp),
            color = Upcroc.Dim,
        )
        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            GhostButton(
                "Battery settings",
                onClick = { Permissions.openBatterySettings(context) },
                modifier = Modifier.weight(1f),
            )
            GhostButton(
                "App info",
                onClick = { Permissions.openAppDetails(context) },
                modifier = Modifier.weight(1f),
            )
        }
        Spacer(Modifier.height(8.dp))
        PrimaryButton(
            "Close",
            onClick = { backgroundOpen = false },
            modifier = Modifier.fillMaxWidth(),
        )
    }

    ModalSheet(visible = quietOpen, onDismiss = { quietOpen = false }) {
        AppText("Quiet hours", style = UpType.section, color = Upcroc.Text)
        Spacer(Modifier.height(4.dp))
        AppText(
            "Checks slow down and alerts stay silent during this window.",
            style = UpType.muted,
            color = Upcroc.Muted,
        )
        Spacer(Modifier.height(14.dp))
        SettingRow(
            title = "Quiet hours",
            subtitle = if (editQuiet.enabled) "On" else "Off",
            onClick = { editQuiet = editQuiet.copy(enabled = !editQuiet.enabled) },
            trailing = {
                UpSwitch(
                    checked = editQuiet.enabled,
                    onCheckedChange = { editQuiet = editQuiet.copy(enabled = it) },
                )
            },
        )
        Spacer(Modifier.height(8.dp))
        TimeStepper("Start", editQuiet.startMinute) {
            editQuiet = editQuiet.copy(startMinute = it)
        }
        Spacer(Modifier.height(8.dp))
        TimeStepper("End", editQuiet.endMinute) {
            editQuiet = editQuiet.copy(endMinute = it)
        }
        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            GhostButton("Cancel", { quietOpen = false }, Modifier.weight(1f))
            PrimaryButton(
                "Save",
                {
                    vm.updateConfig {
                        it.copy(cadence = it.cadence.copy(quietHours = editQuiet))
                    }
                    quietOpen = false
                },
                Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun SettingsGroup(label: String, content: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(top = 18.dp)) {
        AppText(label, style = UpType.section, color = Upcroc.Text)
        Spacer(Modifier.height(8.dp))
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            content()
        }
    }
}

private val textScales = listOf(0.9f, 1f, 1.1f, 1.2f, 1.3f)
private val textScaleLabels = listOf("Small", "Default", "Large", "XL", "XXL")

private fun textScaleIndex(value: Float): Int {
    val found = textScales.indexOfFirst { kotlin.math.abs(it - value) < 0.01f }
    return if (found >= 0) found else 1
}

private fun soundName(context: Context, uri: String?): String {
    if (uri.isNullOrBlank()) return "System default"
    return runCatching {
        RingtoneManager.getRingtone(context, Uri.parse(uri))?.getTitle(context)
    }.getOrNull()?.takeIf { it.isNotBlank() } ?: "Custom sound"
}
