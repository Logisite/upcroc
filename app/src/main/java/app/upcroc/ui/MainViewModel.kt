package app.upcroc.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import app.upcroc.backup.BackupOptions
import app.upcroc.backup.RestoreOptions
import app.upcroc.core.config.WatcherConfig
import app.upcroc.core.engine.EngineSnapshot
import app.upcroc.core.metrics.LatencyStats
import app.upcroc.core.metrics.Metrics
import app.upcroc.core.model.Job
import app.upcroc.core.model.SearchDraft
import app.upcroc.core.model.SearchFilters
import app.upcroc.core.model.SearchGroup
import app.upcroc.data.db.CheckEntity
import app.upcroc.data.db.toModel
import app.upcroc.data.prefs.UiState
import app.upcroc.di.AppContainer
import app.upcroc.logging.LogLine
import app.upcroc.runtime.CheckResult
import app.upcroc.service.WatcherService
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class DiagStats(
    val checks: Int = 0,
    val alerts: Int = 0,
    val errors: Int = 0,
)

sealed interface PreviewState {
    data object Idle : PreviewState
    data object Loading : PreviewState
    data class Success(val jobs: List<Job>) : PreviewState
    data class Error(val message: String) : PreviewState
}

class MainViewModel(private val container: AppContainer) : ViewModel() {
    val config: StateFlow<WatcherConfig> = container.settings.configFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, WatcherConfig.defaults())

    val draft: StateFlow<SearchDraft> = container.settings.draftFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, SearchDraft())

    val onboardingComplete: StateFlow<Boolean> = container.settings.onboardingCompleteFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    val uiState: StateFlow<UiState> = container.settings.uiStateFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, UiState())

    val authState = container.auth.state
    val running = container.runtime.running
    val status = container.runtime.status
    val updateState = container.updateManager.state
    val lastResult: StateFlow<CheckResult?> = container.runtime.lastResult

    val jobs: StateFlow<List<Job>> = container.db.jobDao().observeFeed(500)
        .map { list -> list.map { it.toModel() } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val savedJobs: StateFlow<List<Job>> = container.db.jobDao().observeSaved(500)
        .map { list -> list.map { it.toModel() } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val logs: StateFlow<List<LogLine>> = container.logger.lines

    private val _ready = MutableStateFlow(false)
    val ready: StateFlow<Boolean> = _ready

    private val _preview = MutableStateFlow<PreviewState>(PreviewState.Idle)
    val preview: StateFlow<PreviewState> = _preview

    private val _snapshot = MutableStateFlow<EngineSnapshot?>(null)
    val snapshot: StateFlow<EngineSnapshot?> = _snapshot

    private val _latency = MutableStateFlow<LatencyStats?>(null)
    val latency: StateFlow<LatencyStats?> = _latency

    private val _jobsStored = MutableStateFlow(0)
    val jobsStored: StateFlow<Int> = _jobsStored

    private val _recentChecks = MutableStateFlow<List<CheckEntity>>(emptyList())
    val recentChecks: StateFlow<List<CheckEntity>> = _recentChecks

    private val _recentAlerts = MutableStateFlow<List<Job>>(emptyList())
    val recentAlerts: StateFlow<List<Job>> = _recentAlerts

    private val _diagStats = MutableStateFlow(DiagStats())
    val diagStats: StateFlow<DiagStats> = _diagStats

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val messages = _messages.asSharedFlow()

    init {
        viewModelScope.launch {
            container.settings.configFlow.first()
            container.settings.onboardingCompleteFlow.first()
            container.settings.uiStateFlow.first()
            _ready.value = true
            refreshDiagnostics()
        }
    }

    fun saveUiState(state: UiState) {
        viewModelScope.launch { container.settings.saveUiState(state) }
    }

    fun updateUiState(transform: (UiState) -> UiState) {
        viewModelScope.launch {
            val current = container.settings.uiStateFlow.first()
            container.settings.saveUiState(transform(current))
        }
    }

    fun setOnboardingComplete(complete: Boolean) {
        viewModelScope.launch { container.settings.setOnboardingComplete(complete) }
    }

    fun completeOnboarding(
        groups: List<SearchGroup>,
        filters: SearchFilters,
        notifications: app.upcroc.core.config.NotificationConfig,
        quietHours: app.upcroc.core.config.QuietHours,
        context: Context,
        onDone: () -> Unit,
    ) {
        val next = config.value.copy(
            searches = groups,
            filters = filters,
            notifications = notifications,
            cadence = config.value.cadence.copy(quietHours = quietHours),
        )
        val errors = next.validate()
        if (errors.isNotEmpty()) {
            emit(errors.first())
            return
        }
        viewModelScope.launch {
            container.settings.saveDraft(
                SearchDraft(groups = groups, filters = filters, updatedAt = System.currentTimeMillis())
            )
            container.settings.saveConfig(next)
            container.settings.setOnboardingComplete(true)
            container.settings.setRunning(true)
            WatcherService.start(context.applicationContext)
            onDone()
        }
    }

    fun updateDraft(groups: List<SearchGroup>, filters: SearchFilters) {
        viewModelScope.launch {
            container.settings.saveDraft(
                SearchDraft(groups = groups, filters = filters, updatedAt = System.currentTimeMillis())
            )
        }
    }

    fun applyDraftToConfig(onDone: (() -> Unit)? = null) {
        val d = draft.value
        applySearch(d.groups, d.filters, onDone)
    }

    fun applySearch(groups: List<SearchGroup>, filters: SearchFilters, onDone: (() -> Unit)? = null) {
        val next = config.value.copy(searches = groups, filters = filters)
        val errors = next.validate()
        if (errors.isNotEmpty()) {
            emit(errors.first())
            return
        }
        viewModelScope.launch {
            container.settings.saveDraft(
                SearchDraft(groups = groups, filters = filters, updatedAt = System.currentTimeMillis())
            )
            container.settings.saveConfig(next)
            emit("Search saved.")
            onDone?.invoke()
        }
    }

    fun saveConfig(next: WatcherConfig, onDone: (() -> Unit)? = null) {
        val errors = next.validate()
        if (errors.isNotEmpty()) {
            emit(errors.first())
            return
        }
        viewModelScope.launch {
            container.settings.saveConfig(next)
            onDone?.invoke()
        }
    }

    fun updateConfig(transform: (WatcherConfig) -> WatcherConfig) {
        viewModelScope.launch {
            container.settings.updateConfig { current ->
                val next = transform(current)
                val errors = next.validate()
                if (errors.isNotEmpty()) {
                    emit(errors.first())
                    current
                } else {
                    next
                }
            }
            refreshDiagnostics()
        }
    }

    fun saveRawConfig(text: String) {
        viewModelScope.launch {
            val parsed = app.upcroc.core.config.ConfigCodec.decodeStrict(text)
            if (parsed == null) {
                emit("That JSON could not be read. The previous config is still active.")
                return@launch
            }
            val errors = parsed.validate()
            if (errors.isNotEmpty()) {
                emit(errors.first())
                return@launch
            }
            container.settings.saveConfig(parsed)
            emit("Configuration saved.")
        }
    }

    /** Restarts the foreground service after a process kill without a toast. */
    fun ensureWatcherRunning(context: Context) {
        viewModelScope.launch {
            if (container.settings.runningFlow.first()) {
                WatcherService.ensure(context.applicationContext)
            }
        }
    }

    fun startWatcher(context: Context) {
        viewModelScope.launch {
            container.settings.setRunning(true)
            WatcherService.start(context.applicationContext)
            if (!container.scheduler.canScheduleExact()) {
                emit("Started. Allow “Alarms & reminders” in Settings so checks stay on time.")
            } else {
                emit("Watcher started.")
            }
        }
    }

    fun stopWatcher(context: Context) {
        viewModelScope.launch {
            WatcherService.stop(context.applicationContext)
            emit("Watcher stopped.")
        }
    }

    fun checkNow(context: Context, announce: Boolean = true, auto: Boolean = false) {
        viewModelScope.launch {
            container.settings.setRunning(true)
            WatcherService.checkNow(context.applicationContext, auto = auto)
            if (announce) emit("Checking now…")
        }
    }

    fun signIn() = container.auth.signIn()
    fun submitManualAuth(input: String) = container.auth.submitManual(input)
    fun signOut() = container.auth.signOut()

    fun setSaved(id: String, saved: Boolean) {
        viewModelScope.launch { container.db.jobDao().setSaved(id, saved) }
    }

    fun hideJob(id: String) {
        viewModelScope.launch { container.db.jobDao().setHidden(id, true) }
    }

    fun preview(groupId: String) {
        _preview.value = PreviewState.Loading
        viewModelScope.launch {
            _preview.value = try {
                val jobs = container.engine.preview(groupId, limit = 5)
                PreviewState.Success(jobs)
            } catch (e: Exception) {
                PreviewState.Error(e.message ?: "Preview failed.")
            }
        }
    }

    fun clearPreview() {
        _preview.value = PreviewState.Idle
    }

    fun postTest() {
        container.notifier.postTest()
        emit("Test alert sent. Check your notification shade.")
    }

    fun refreshDiagnostics() {
        viewModelScope.launch {
            _snapshot.value = runCatching { container.engine.snapshot() }.getOrNull()
            _jobsStored.value = runCatching { container.jobRepository.jobCount() }.getOrDefault(0)
            _latency.value = runCatching {
                Metrics.latency(container.db.jobDao().recentNotified(500).map { it.toModel() })
            }.getOrNull()
            val week = runCatching {
                container.db.checkDao().since(System.currentTimeMillis() - 7 * 86_400_000L)
            }.getOrDefault(emptyList())
            _recentChecks.value = week.take(60)
            _diagStats.value = DiagStats(
                checks = week.size,
                alerts = week.sumOf { it.notified },
                errors = week.count { !it.status.equals("OK", ignoreCase = true) || it.error != null },
            )
            _recentAlerts.value = runCatching {
                container.db.jobDao().recentNotified(40).map { it.toModel() }
            }.getOrDefault(emptyList())
        }
    }

    fun logText(): String = container.logger.exportText()

    fun shareLogs(context: Context) {
        val text = container.logger.exportText()
        val intent = Intent(Intent.ACTION_SEND)
            .setType("text/plain")
            .putExtra(Intent.EXTRA_SUBJECT, "Upcroc logs")
            .putExtra(Intent.EXTRA_TEXT, text)
        context.startActivity(
            Intent.createChooser(intent, "Share Upcroc logs")
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }

    fun clearLogs() {
        container.logger.clear()
        emit("Logs cleared.")
    }

    fun createBackup(uri: Uri, password: String, options: BackupOptions) {
        if (password.length < 8) {
            emit("Use at least 8 characters for the backup password.")
            return
        }
        viewModelScope.launch {
            try {
                container.backupManager.create(uri, password.toCharArray(), options)
                emit("Backup saved.")
            } catch (e: Exception) {
                container.logger.error("backup failed", e)
                emit("Backup failed: ${e.message ?: "could not write the file."}")
            }
        }
    }

    fun restoreBackup(uri: Uri, password: String, options: RestoreOptions) {
        if (password.isBlank()) {
            emit("Enter the backup password.")
            return
        }
        viewModelScope.launch {
            try {
                container.backupManager.restore(uri, password.toCharArray(), options)
                emit("Backup restored.")
            } catch (e: Exception) {
                container.logger.error("restore failed", e)
                emit(
                    when (e.message?.startsWith("That password") == true) {
                        true -> e.message ?: "Wrong password."
                        false -> "Restore failed: ${e.message ?: "could not read the backup."}"
                    },
                )
            }
        }
    }

    fun checkForUpdates(force: Boolean = false) {
        viewModelScope.launch {
            if (force || config.value.advanced.updateCheckEnabled) {
                container.updateManager.check(force)
            }
        }
    }

    fun downloadUpdate(info: app.upcroc.update.UpdateInfo) {
        viewModelScope.launch { container.updateManager.downloadAndInstall(info) }
    }

    fun dismissUpdate() = container.updateManager.dismiss()

    suspend fun jobById(id: String): Job? = container.db.jobDao().byId(id)?.toModel()

    fun configText(): String = app.upcroc.core.config.ConfigCodec.pretty(config.value)

    private fun emit(message: String) {
        _messages.tryEmit(message)
    }

    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    MainViewModel(container) as T
            }
    }
}
