package app.upcroc.data.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import app.upcroc.core.config.ConfigCodec
import app.upcroc.core.config.WatcherConfig
import app.upcroc.core.model.SearchDraft
import app.upcroc.core.util.CoreJson
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import java.io.IOException

private val Context.upcrocDataStore: DataStore<Preferences> by preferencesDataStore(name = "upcroc_prefs")

@Serializable
data class UiState(
    val lastRoute: String = "feed",
    val selectedTab: Int = 0,
    val feedAnchorJobId: String? = null,
    val feedAnchorOffset: Int = 0,
    val selectedJobId: String? = null,
    val fontScale: Float = 1f,
)

class SettingsRepository(private val context: Context) {
    private val store = context.upcrocDataStore

    private val safeData = store.data.catch { e ->
        if (e is IOException) emit(emptyPreferences()) else throw e
    }

    val configFlow: Flow<WatcherConfig> = safeData
        .map { ConfigCodec.decode(it[KEY_CONFIG]) }
        .distinctUntilChanged()

    val draftFlow: Flow<SearchDraft> = safeData
        .map { ConfigCodec.decodeDraft(it[KEY_DRAFT]) }
        .distinctUntilChanged()

    val uiStateFlow: Flow<UiState> = safeData
        .map { decodeUiState(it[KEY_UI]) }
        .distinctUntilChanged()

    val onboardingCompleteFlow: Flow<Boolean> = safeData
        .map { it[KEY_ONBOARDING] ?: false }
        .distinctUntilChanged()

    val runningFlow: Flow<Boolean> = safeData
        .map { it[KEY_RUNNING] ?: false }
        .distinctUntilChanged()

    val lastUpdateCheckFlow: Flow<Long> = safeData
        .map { it[KEY_UPDATE_CHECK] ?: 0L }
        .distinctUntilChanged()

    suspend fun configOnce(): WatcherConfig = configFlow.first()

    suspend fun saveConfig(config: WatcherConfig) {
        store.edit { it[KEY_CONFIG] = ConfigCodec.encode(config.sanitized()) }
    }

    suspend fun updateConfig(transform: (WatcherConfig) -> WatcherConfig) {
        store.edit { prefs ->
            val current = ConfigCodec.decode(prefs[KEY_CONFIG])
            prefs[KEY_CONFIG] = ConfigCodec.encode(transform(current).sanitized())
        }
    }

    suspend fun saveDraft(draft: SearchDraft) {
        store.edit { it[KEY_DRAFT] = ConfigCodec.encodeDraft(draft) }
    }

    suspend fun saveUiState(state: UiState) {
        store.edit { it[KEY_UI] = CoreJson.encodeToString(UiState.serializer(), state) }
    }

    suspend fun setOnboardingComplete(complete: Boolean) {
        store.edit { it[KEY_ONBOARDING] = complete }
    }

    suspend fun setRunning(running: Boolean) {
        store.edit { it[KEY_RUNNING] = running }
    }

    suspend fun setLastUpdateCheck(at: Long) {
        store.edit { it[KEY_UPDATE_CHECK] = at }
    }

    suspend fun lastUpdateCheckOnce(): Long = lastUpdateCheckFlow.first()

    private fun decodeUiState(text: String?): UiState {
        if (text.isNullOrBlank()) return UiState()
        return runCatching { CoreJson.decodeFromString(UiState.serializer(), text) }.getOrElse { UiState() }
    }

    private companion object {
        val KEY_CONFIG = stringPreferencesKey("config_json")
        val KEY_DRAFT = stringPreferencesKey("search_draft_json")
        val KEY_UI = stringPreferencesKey("ui_state_json")
        val KEY_ONBOARDING = booleanPreferencesKey("onboarding_complete")
        val KEY_RUNNING = booleanPreferencesKey("watcher_running")
        val KEY_UPDATE_CHECK = longPreferencesKey("last_update_check")
    }
}
