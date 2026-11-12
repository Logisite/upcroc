package app.upcroc.runtime

import app.upcroc.core.platform.WatcherStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

data class CheckResult(
    val at: Long,
    val source: String,
    val status: String,
    val requests: Int,
    val newJobs: Int,
    val notified: Int,
    val message: String?,
)

class WatcherRuntime {
    private val _running = MutableStateFlow(false)
    val running: StateFlow<Boolean> = _running

    private val _status = MutableStateFlow(WatcherStatus())
    val status: StateFlow<WatcherStatus> = _status

    private val _lastResult = MutableStateFlow<CheckResult?>(null)
    val lastResult: StateFlow<CheckResult?> = _lastResult

    private val _nextAlarmAt = MutableStateFlow(0L)
    val nextAlarmAt: StateFlow<Long> = _nextAlarmAt

    fun setRunning(running: Boolean) {
        _running.value = running
        _status.value = _status.value.copy(running = running)
    }

    fun updateStatus(transform: (WatcherStatus) -> WatcherStatus) {
        _status.value = transform(_status.value).copy(running = _running.value)
    }

    fun setNextAlarm(at: Long) {
        _nextAlarmAt.value = at
        _status.value = _status.value.copy(nextCheckAt = at.takeIf { it > 0 })
    }

    fun setChecking(checking: Boolean) {
        _status.value = _status.value.copy(checking = checking)
    }

    fun publishResult(result: CheckResult) {
        _lastResult.value = result
    }
}
