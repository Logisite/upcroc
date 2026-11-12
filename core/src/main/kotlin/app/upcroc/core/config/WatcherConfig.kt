package app.upcroc.core.config

import app.upcroc.core.model.SearchDraft
import app.upcroc.core.model.SearchFilters
import app.upcroc.core.model.SearchGroup
import app.upcroc.core.model.defaultGroups
import app.upcroc.core.util.CoreJson
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

@Serializable
enum class CadencePreset { BALANCED, AGGRESSIVE, BATTERY_SAVER, CUSTOM }

@Serializable
data class QuietHours(
    val enabled: Boolean = true,
    val startMinute: Int = 23 * 60,
    val endMinute: Int = 7 * 60,
) {
    fun contains(minuteOfDay: Int): Boolean {
        if (!enabled) return false
        val start = startMinute.coerceIn(0, 1439)
        val end = endMinute.coerceIn(0, 1439)
        return if (start <= end) {
            minuteOfDay in start until end
        } else {
            minuteOfDay >= start || minuteOfDay < end
        }
    }
}

@Serializable
data class CadenceConfig(
    val preset: CadencePreset = CadencePreset.BALANCED,
    val primaryIntervalSec: Int = 90,
    val secondaryIntervalSec: Int = 300,
    val quietIntervalSec: Int = 900,
    val quietSecondaryIntervalSec: Int = 3600,
    val watchdogIntervalSec: Int = 900,
    val aggressiveWakelock: Boolean = false,
    val quietHours: QuietHours = QuietHours(),
)

@Serializable
data class RateBudgetConfig(
    val dailyLimit: Int = 750,
    val hourlyLimit: Int = 150,
    val minuteLimit: Int = 15,
    val warningPercent: Int = 90,
    val hardCap: Boolean = false,
    val pauseOn429: Boolean = true,
)

@Serializable
data class NotificationConfig(
    val enabled: Boolean = true,
    val soundUri: String? = null,
    val vibration: Boolean = true,
    val respectDnd: Boolean = true,
    val freshWindowSec: Int = 300,
    val lateNotifySec: Int = 600,
    val summaryThreshold: Int = 4,
    val showClientStats: Boolean = true,
    val showProposals: Boolean = true,
    val openUrlOnTap: Boolean = true,
    val enrich: Boolean = true,
    val enrichBeforeNotify: Boolean = false,
)

@Serializable
data class StorageConfig(
    val retentionDays: Int = 30,
    val statsRetentionDays: Int = 7,
    val maxJobs: Int = 5000,
)

@Serializable
data class NetworkConfig(
    val connectTimeoutSec: Int = 15,
    val readTimeoutSec: Int = 30,
    val callTimeoutSec: Int = 45,
    val userAgent: String = "Upcroc/0.1.0 (Android)",
)

@Serializable
data class AuthConfig(
    val orgUid: String = "",
    val accountName: String = "",
    val clientAsset: String = "oauth_client.json",
    val useDynamicRegistration: Boolean = false,
)

@Serializable
data class AdvancedConfig(
    val alwaysOn: Boolean = false,
    val resumeOnBoot: Boolean = false,
    val logLevel: String = "info",
    val updateCheckEnabled: Boolean = true,
    val updateManifestUrl: String = "",
    val pageParam: String = "",
    val pageSize: Int = 20,
    val pageCap: Int = 3,
    val paceMs: Long = 150L,
    val resumeCheckWithinSec: Int = 600,
    val watchdogEnabled: Boolean = true,
    val verboseLogs: Boolean = false,
)

@Serializable
data class WatcherConfig(
    val schemaVersion: Int = 2,
    val searches: List<SearchGroup> = defaultGroups(),
    val filters: SearchFilters = SearchFilters(),
    val cadence: CadenceConfig = CadenceConfig(),
    val budget: RateBudgetConfig = RateBudgetConfig(),
    val notifications: NotificationConfig = NotificationConfig(),
    val storage: StorageConfig = StorageConfig(),
    val network: NetworkConfig = NetworkConfig(),
    val auth: AuthConfig = AuthConfig(),
    val advanced: AdvancedConfig = AdvancedConfig(),
) {
    fun enabledGroups(): List<SearchGroup> = searches
        .filter { it.enabled && (it.mode == app.upcroc.core.model.SearchMode.SMART_SEARCH || it.keywords.isNotBlank()) }
        .sortedBy { it.priority }

    fun primaryGroup(): SearchGroup? = enabledGroups().minByOrNull { it.priority }

    fun validate(): List<String> {
        val errors = mutableListOf<String>()
        if (searches.isEmpty()) errors += "At least one search group is required."
        if (searches.size > 3) errors += "At most three search groups are supported."
        searches.forEach { group ->
            if (group.intervalSec !in 30..300) {
                errors += "${group.name}: interval must be between 30 seconds and 5 minutes."
            }
            if (group.intervalMaxSec != 0 && group.intervalMaxSec !in group.intervalSec..300) {
                errors += "${group.name}: the jitter maximum must be between the minimum and 5 minutes."
            }
            if (group.mode == app.upcroc.core.model.SearchMode.SEARCH && group.keywords.isBlank() && group.enabled) {
                errors += "${group.name}: add keywords or disable the group."
            }
        }
        if (cadence.primaryIntervalSec < 30) errors += "Primary interval must be at least 30 seconds."
        if (cadence.secondaryIntervalSec < 60) errors += "Secondary interval must be at least 60 seconds."
        if (budget.dailyLimit < 1) errors += "Daily budget must be positive."
        if (budget.warningPercent !in 1..100) errors += "Budget warning percent must be 1..100."
        if (notifications.lateNotifySec < notifications.freshWindowSec) {
            errors += "Late-notify window must be at least the fresh window."
        }
        if (storage.retentionDays < 1) errors += "Retention must be at least one day."
        if (advanced.pageSize !in 1..100) errors += "Page size must be 1..100."
        return errors
    }

    fun sanitized(): WatcherConfig = copy(
        searches = searches.take(3).mapIndexed { index, group ->
            group.copy(
                id = group.id.ifBlank { if (index == 0) "primary" else "secondary-$index" },
                intervalSec = group.intervalSec.coerceIn(30, 300),
                intervalMaxSec = if (group.intervalMaxSec <= 0) {
                    0
                } else {
                    group.intervalMaxSec.coerceIn(group.intervalSec.coerceIn(30, 300), 300)
                },
                priority = group.priority.coerceAtLeast(0),
            )
        },
        cadence = cadence.copy(
            primaryIntervalSec = cadence.primaryIntervalSec.coerceIn(30, 86_400),
            secondaryIntervalSec = cadence.secondaryIntervalSec.coerceIn(60, 86_400),
            quietIntervalSec = cadence.quietIntervalSec.coerceIn(60, 86_400),
            quietSecondaryIntervalSec = cadence.quietSecondaryIntervalSec.coerceIn(60, 86_400),
            watchdogIntervalSec = cadence.watchdogIntervalSec.coerceIn(300, 86_400),
        ),
        budget = budget.copy(
            dailyLimit = budget.dailyLimit.coerceIn(1, 100_000),
            hourlyLimit = budget.hourlyLimit.coerceIn(1, 100_000),
            minuteLimit = budget.minuteLimit.coerceIn(1, 1_000),
            warningPercent = budget.warningPercent.coerceIn(1, 100),
        ),
        storage = storage.copy(
            retentionDays = storage.retentionDays.coerceIn(1, 365),
            statsRetentionDays = storage.statsRetentionDays.coerceIn(1, 90),
            maxJobs = storage.maxJobs.coerceIn(100, 100_000),
        ),
        advanced = advanced.copy(
            pageSize = advanced.pageSize.coerceIn(1, 100),
            pageCap = advanced.pageCap.coerceIn(1, 50),
            paceMs = advanced.paceMs.coerceIn(0, 5_000),
            resumeCheckWithinSec = advanced.resumeCheckWithinSec.coerceIn(0, 86_400),
        ),
    )

    companion object {
        fun defaults(): WatcherConfig = WatcherConfig()
    }
}

object ConfigCodec {
    fun encode(config: WatcherConfig): String = CoreJson.encodeToString(WatcherConfig.serializer(), config)

    fun decode(text: String?): WatcherConfig {
        if (text.isNullOrBlank()) return WatcherConfig.defaults()
        return runCatching {
            migrate(CoreJson.decodeFromString(WatcherConfig.serializer(), text)).sanitized()
        }.getOrElse { WatcherConfig.defaults() }
    }

    /** Version 2 moves old fixed 2 minute slots onto the 90 to 300 second jitter. */
    private fun migrate(config: WatcherConfig): WatcherConfig {
        if (config.schemaVersion >= 2) return config
        return config.copy(schemaVersion = 2, searches = migrateGroups(config.searches))
    }

    private fun migrateGroups(groups: List<SearchGroup>): List<SearchGroup> = groups.map { group ->
        if (group.intervalMaxSec == 0 && group.intervalSec >= 120) {
            group.copy(intervalSec = 90, intervalMaxSec = 300)
        } else {
            group
        }
    }

    fun decodeDraft(text: String?): SearchDraft {
        if (text.isNullOrBlank()) return SearchDraft()
        val draft = runCatching {
            CoreJson.decodeFromString(SearchDraft.serializer(), text)
        }.getOrElse { return SearchDraft() }
        val clean = WatcherConfig.defaults()
            .copy(searches = draft.groups, filters = draft.filters)
            .sanitized()
        return draft.copy(groups = migrateGroups(clean.searches), filters = clean.filters)
    }

    fun decodeStrict(text: String): WatcherConfig? = runCatching {
        CoreJson.decodeFromString(WatcherConfig.serializer(), text).sanitized()
    }.getOrNull()

    fun encodeDraft(draft: SearchDraft): String = CoreJson.encodeToString(SearchDraft.serializer(), draft)

    fun pretty(config: WatcherConfig): String = CoreJson.encodeToString(WatcherConfig.serializer(), config.sanitized())
}
