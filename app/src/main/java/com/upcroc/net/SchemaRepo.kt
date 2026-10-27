package com.upcroc.net

import com.upcroc.ui.FieldSpec
import com.upcroc.ui.HelpSchema
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Holds find_jobs' filter catalogue.
 *
 * It does not come from `tools/list`: find_jobs declares only action, org_uid
 * and an undocumented free-form `params` with no properties and no allOf. It
 * comes from `get_tool_help`, which the same server publishes and which
 * documents every per-action filter. So the field list is still Upwork's own
 * and still tracks Upwork — it just arrives as prose rather than JSON Schema.
 * See HelpSchema for the acceptance rules.
 */
object SchemaRepo {

    val ACTIONS = listOf("search", "get", "smart_search")

    private val _fields = MutableStateFlow<Map<String, List<FieldSpec>>>(emptyMap())
    val fields = _fields.asStateFlow()

    fun forAction(action: String): List<FieldSpec> = _fields.value[action].orEmpty()

    fun publishHelp(description: String) {
        val parsed = HelpSchema.parse(description)
        if (parsed.isNotEmpty()) _fields.value = parsed
    }

    fun isLoaded(): Boolean = _fields.value.values.any { it.isNotEmpty() }
}