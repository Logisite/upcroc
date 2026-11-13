package app.upcroc.core.engine

import app.upcroc.core.platform.Clock
import app.upcroc.core.platform.EngineStateStore
import app.upcroc.core.util.CoreJson
import kotlinx.serialization.Serializable

@Serializable
data class GroupRuntime(
    val nextDueAt: Long = 0L,
    val lastCheckAt: Long = 0L,
    val failures: Int = 0,
    val lastError: String? = null,
    val lastPages: Int = 0,
    val lastNew: Int = 0,
    val lastNotified: Int = 0,
)

object StateKeys {
    const val LAST_CHECK = "last_check"
    const val LAST_TICK = "last_tick"
    const val LAST_ATTEMPT = "last_attempt"
    const val NEXT_CHECK = "next_check"
    const val BACKOFF_LEVEL = "backoff_level"
    const val BACKOFF_UNTIL = "backoff_until"
    const val LAST_ERROR = "last_error"
    const val LAST_ERROR_AT = "last_error_at"
    const val BUDGET = "budget"
    const val TOOL_NAME = "tool_name"
    const val TOOL_SCHEMA = "tool_schema"
    const val ORG_UID = "org_uid"
    const val LAST_CHECK_GROUP = "last_check_group"
    const val CHECK_IN_PROGRESS = "check_in_progress"
    const val CHECK_STARTED_AT = "check_started_at"
    const val LAST_RESULT = "last_result"

    fun group(id: String) = "group.$id"
}

class EngineState(
    private val store: EngineStateStore,
    private val clock: Clock,
) {
    suspend fun string(key: String): String? = store.get(key)
    suspend fun putString(key: String, value: String) = store.put(key, value)
    suspend fun remove(key: String) = store.remove(key)

    suspend fun long(key: String, default: Long = 0L): Long =
        store.get(key)?.toLongOrNull() ?: default

    suspend fun putLong(key: String, value: Long) = store.put(key, value.toString())

    suspend fun group(id: String): GroupRuntime {
        val raw = store.get(StateKeys.group(id)) ?: return GroupRuntime()
        return runCatching { CoreJson.decodeFromString(GroupRuntime.serializer(), raw) }
            .getOrElse { GroupRuntime() }
    }

    suspend fun putGroup(id: String, runtime: GroupRuntime) {
        store.put(StateKeys.group(id), CoreJson.encodeToString(GroupRuntime.serializer(), runtime))
    }

    suspend fun budget(): BudgetState {
        val raw = store.get(StateKeys.BUDGET)
        return RateBudget(clock::now).decode(raw)
    }

    suspend fun putBudget(state: BudgetState) {
        store.put(StateKeys.BUDGET, RateBudget(clock::now).encode(state))
    }

    suspend fun toolName(): String? = store.get(StateKeys.TOOL_NAME)?.takeIf { it.isNotBlank() }

    suspend fun putToolName(name: String) = store.put(StateKeys.TOOL_NAME, name)

    suspend fun toolSchema(): String? = store.get(StateKeys.TOOL_SCHEMA)

    suspend fun putToolSchema(schema: String) = store.put(StateKeys.TOOL_SCHEMA, schema)

    suspend fun orgUid(): String? = store.get(StateKeys.ORG_UID)?.takeIf { it.isNotBlank() }

    suspend fun putOrgUid(uid: String) = store.put(StateKeys.ORG_UID, uid)
}
