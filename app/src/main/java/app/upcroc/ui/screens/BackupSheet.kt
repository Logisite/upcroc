package app.upcroc.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.upcroc.backup.BackupOptions
import app.upcroc.backup.RestoreOptions
import app.upcroc.ui.MainViewModel
import app.upcroc.ui.components.AppText
import app.upcroc.ui.components.ModalSheet
import app.upcroc.ui.components.PrimaryButton
import app.upcroc.ui.components.SwitchRow
import app.upcroc.ui.components.UpField
import app.upcroc.ui.theme.UpType
import app.upcroc.ui.theme.Upcroc

@Composable
fun BackupSheet(
    vm: MainViewModel,
    onDismiss: () -> Unit,
    restore: Boolean = false,
) {
    var password by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    var includeAuth by remember { mutableStateOf(true) }
    var includeConfig by remember { mutableStateOf(true) }
    var includeUi by remember { mutableStateOf(true) }
    var includeDraft by remember { mutableStateOf(true) }
    var includeJobs by remember { mutableStateOf(true) }
    var includeChecks by remember { mutableStateOf(true) }
    var includeLogs by remember { mutableStateOf(false) }

    var restoreAuth by remember { mutableStateOf(true) }
    var restoreConfig by remember { mutableStateOf(true) }
    var restoreUi by remember { mutableStateOf(true) }
    var restoreDraft by remember { mutableStateOf(true) }
    var restoreJobs by remember { mutableStateOf(true) }
    var restoreChecks by remember { mutableStateOf(true) }
    var restoreLogs by remember { mutableStateOf(true) }
    var restoreOnboarding by remember { mutableStateOf(true) }
    var restoreRunning by remember { mutableStateOf(true) }

    val createLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream"),
    ) { uri ->
        if (uri != null) {
            if (password != confirm) {
                error = "The backup passwords do not match."
            } else {
                vm.createBackup(
                    uri,
                    password,
                    BackupOptions(
                        includeAuth = includeAuth,
                        includeConfig = includeConfig,
                        includeUiState = includeUi,
                        includeDraft = includeDraft,
                        includeJobs = includeJobs,
                        includeChecks = includeChecks,
                        includeLogs = includeLogs,
                    ),
                )
            }
        }
    }

    val openLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) {
            vm.restoreBackup(
                uri,
                password,
                RestoreOptions(
                    restoreAuth = restoreAuth,
                    restoreConfig = restoreConfig,
                    restoreUiState = restoreUi,
                    restoreDraft = restoreDraft,
                    restoreJobs = restoreJobs,
                    restoreChecks = restoreChecks,
                    restoreLogs = restoreLogs,
                    restoreOnboarding = restoreOnboarding,
                    restoreRunning = restoreRunning,
                ),
            )
        }
    }

    ModalSheet(visible = true, onDismiss = onDismiss) {
        AppText(
            if (restore) "Restore backup" else "Create backup",
            style = UpType.section,
            color = Upcroc.Text,
        )
        AppText(
            if (restore) {
                "Restore into this install. If the backup contains a token, it replaces the current session."
            } else {
                "A backup is encrypted with a password you choose. Keep that password, because it is not stored in the app."
            },
            style = UpType.muted,
            color = Upcroc.Muted,
        )
        error?.let {
            AppText(it, style = UpType.small, color = Upcroc.Error)
        }

        if (!restore) {
            SheetLabel("Include in backup")
            SwitchRow(
                title = "Sign-in token",
                subtitle = "Lets you restore without signing in again.",
                checked = includeAuth,
                onCheckedChange = { includeAuth = it },
            )
            SwitchRow(
                title = "Searches and settings",
                subtitle = "Config, filters, quiet hours, Layer 0.",
                checked = includeConfig,
                onCheckedChange = { includeConfig = it },
            )
            SwitchRow(
                title = "UI and text size",
                subtitle = "Font, text size, last screen, saved state.",
                checked = includeUi,
                onCheckedChange = { includeUi = it },
            )
            SwitchRow(
                title = "Search draft",
                subtitle = "Unsaved search edits.",
                checked = includeDraft,
                onCheckedChange = { includeDraft = it },
            )
            SwitchRow(
                title = "Jobs and check history",
                subtitle = "Saved jobs, hidden jobs and check records.",
                checked = includeJobs,
                onCheckedChange = { includeJobs = it },
            )
            SwitchRow(
                title = "Event log",
                subtitle = "The seven-day diagnostics log.",
                checked = includeLogs,
                onCheckedChange = { includeLogs = it },
            )
            UpField(
                label = "Backup password",
                value = password,
                onValueChange = { password = it },
                modifier = Modifier.fillMaxWidth(),
                isPassword = true,
            )
            UpField(
                label = "Repeat password",
                value = confirm,
                onValueChange = { confirm = it },
                modifier = Modifier.fillMaxWidth(),
                isPassword = true,
            )
            PrimaryButton(
                "Save backup",
                onClick = { createLauncher.launch("upcroc-backup.ucb") },
                modifier = Modifier.fillMaxWidth(),
            )
        } else {
            SheetLabel("Restore from backup")
            SwitchRow(
                title = "Restore sign-in",
                subtitle = "Use the token from the backup.",
                checked = restoreAuth,
                onCheckedChange = { restoreAuth = it },
            )
            SwitchRow(
                title = "Restore searches and settings",
                subtitle = "Replaces the current configuration.",
                checked = restoreConfig,
                onCheckedChange = { restoreConfig = it },
            )
            SwitchRow(
                title = "Restore UI and text size",
                subtitle = "Font, text size and saved state.",
                checked = restoreUi,
                onCheckedChange = { restoreUi = it },
            )
            SwitchRow(
                title = "Restore search draft",
                subtitle = "Unsaved search edits.",
                checked = restoreDraft,
                onCheckedChange = { restoreDraft = it },
            )
            SwitchRow(
                title = "Restore jobs and history",
                subtitle = "Adds backup jobs and checks without deleting current ones.",
                checked = restoreJobs,
                onCheckedChange = { restoreJobs = it },
            )
            SwitchRow(
                title = "Restore event log",
                subtitle = "Adds the saved log lines.",
                checked = restoreLogs,
                onCheckedChange = { restoreLogs = it },
            )
            SwitchRow(
                title = "Mark setup complete",
                subtitle = "Skip onboarding after restore.",
                checked = restoreOnboarding,
                onCheckedChange = { restoreOnboarding = it },
            )
            SwitchRow(
                title = "Restore watcher state",
                subtitle = "Keep the watcher on or off as saved.",
                checked = restoreRunning,
                onCheckedChange = { restoreRunning = it },
            )
            UpField(
                label = "Backup password",
                value = password,
                onValueChange = { password = it },
                modifier = Modifier.fillMaxWidth(),
                isPassword = true,
            )
            PrimaryButton(
                "Choose backup file",
                onClick = { openLauncher.launch(arrayOf("application/octet-stream", "*/*")) },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun SheetLabel(text: String) {
    AppText(
        text,
        style = UpType.groupTitle.copy(fontSize = 15.sp),
        color = Upcroc.Text,
        modifier = Modifier.padding(top = 18.dp, bottom = 6.dp),
    )
}
