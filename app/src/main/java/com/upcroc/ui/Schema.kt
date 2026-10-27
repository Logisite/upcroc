package com.upcroc.ui

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * One field of an MCP tool's inputSchema, reduced to what a control needs.
 *
 * The schema is the contract: Upwork publishes it, we render it. A field the
 * app has never seen still gets a real control, because its type comes from the
 * schema rather than from a table we would otherwise keep in sync by hand.
 */
data class FieldSpec(
    val name: String,
    val label: String,
    val description: String = "",
    val kind: Kind,
    val options: List<String> = emptyList(),
    val min: Double? = null,
    val max: Double? = null,
    val required: Boolean = false,
    val group: String = GROUP_OTHER,
) {
    enum class Kind { TEXT, NUMBER, BOOLEAN, ENUM, RAW }

    /** Bounds are shown, so an out-of-range value is never a surprise. */
    val rangeHint: String
        get() = when {
            min != null && max != null -> "$min to $max"
            max != null -> "max $max"
            min != null -> "min $min"
            else -> ""
        }

    val help: String
        get() = listOf(description, rangeHint).filter { it.isNotBlank() }.joinToString(" · ")

    fun inRange(n: Double): Boolean =
        (min == null || n >= min) && (max == null || n <= max)
}

const val GROUP_MATCH = "Match"
const val GROUP_FILTERS = "Filters"
const val GROUP_CLIENT = "Client"
const val GROUP_RESULTS = "Results"
const val GROUP_OTHER = "Other (from Upwork)"

val GROUP_ORDER = listOf(GROUP_MATCH, GROUP_FILTERS, GROUP_CLIENT, GROUP_RESULTS, GROUP_OTHER)

/**
 * Field -> section. This is the one table worth hand-maintaining: it decides
 * how the form reads, not what it sends. Anything unmapped still renders, under
 * GROUP_OTHER, so a filter Upwork adds tomorrow is never silently dropped.
 */
private val GROUPS = mapOf(
    "title" to GROUP_MATCH, "description" to GROUP_MATCH, "keywords" to GROUP_MATCH,
    "skills" to GROUP_MATCH, "category" to GROUP_MATCH, "subcategory" to GROUP_MATCH,
    "relevance" to GROUP_MATCH, "query" to GROUP_MATCH,
    "budget" to GROUP_FILTERS, "rate" to GROUP_FILTERS, "duration" to GROUP_FILTERS,
    "experience" to GROUP_FILTERS, "payment" to GROUP_FILTERS, "proposal" to GROUP_FILTERS,
    "client" to GROUP_CLIENT, "timezone" to GROUP_CLIENT, "location" to GROUP_CLIENT,
    "sort" to GROUP_RESULTS, "limit" to GROUP_RESULTS, "cursor" to GROUP_RESULTS,
)

private val ACRONYMS = mapOf("ai" to "AI", "id" to "ID", "url" to "URL", "usd" to "USD")

fun humanise(key: String): String = key.split('_').joinToString(" ") { w ->
    ACRONYMS[w.lowercase()] ?: w.replaceFirstChar { it.uppercase() }
}

fun groupRank(g: String): Int = GROUP_ORDER.indexOf(g).let { if (it < 0) GROUP_ORDER.size else it }

/**
 * Collapse the schema's `properties` into renderable specs, grouped by rank.
 *
 * Anything that is not a primitive keeps raw JSON, but scoped to that single
 * field. The whole-params blob is never offered: it is the editor this screen
 * exists to replace.
 */
fun parseSchema(schema: JsonObject, required: List<String>): List<FieldSpec> {
    val props = schema["properties"] as? JsonObject ?: return emptyList()
    return props.entries
        .map { (key, raw) -> specOf(key, raw, key in required) }
        .sortedWith(compareBy({ groupRank(it.group) }, { it.name }))
}

private fun specOf(key: String, raw: JsonElement, req: Boolean): FieldSpec {
    val o = raw as? JsonObject
    val options = enumOf(o)
    return FieldSpec(
        name = key,
        label = humanise(key),
        description = o?.str("description").orEmpty(),
        kind = if (options.isNotEmpty()) FieldSpec.Kind.ENUM else kindOf(o?.str("type")),
        options = options,
        min = o?.num("minimum"),
        max = o?.num("maximum"),
        required = req,
        group = GROUPS[key] ?: GROUP_OTHER,
    )
}

private fun kindOf(type: String?): FieldSpec.Kind = when (type) {
    "boolean" -> FieldSpec.Kind.BOOLEAN
    "integer", "number" -> FieldSpec.Kind.NUMBER
    "string" -> FieldSpec.Kind.TEXT
    else -> FieldSpec.Kind.RAW
}

private fun enumOf(o: JsonObject?): List<String> {
    val e = o?.get("enum") as? JsonArray ?: return emptyList()
    return e.mapNotNull { (it as? JsonPrimitive)?.contentOrNull() }
}

private fun JsonObject.str(k: String): String? = (this[k] as? JsonPrimitive)?.contentOrNull()

private fun JsonObject.num(k: String): Double? = this[k]?.contentOrNull()?.toDoubleOrNull()

private fun JsonElement.contentOrNull(): String? =
    (this as? JsonPrimitive)?.content?.takeIf { it != "null" && it.isNotEmpty() }

/**
 * Resolve the fields that actually apply to one action.
 *
 * Upwork publishes the per-action filters as `allOf` branches under `params`,
 * each guarded by an `if` on the action — not as `oneOf` variants. Falling
 * through to the top level shows the plumbing (action, org_uid, params) instead
 * of the filters, which is what this is here to avoid.
 *
 * allOf semantics: every branch applies, but a guarded branch only applies when
 * its `if` matches. So the matching rule is "include unguarded branches plus
 * guarded ones whose condition is this action", not "pick the one branch".
 */
private val IMPLICIT = setOf("action", "org_uid")

fun resolveFields(schema: JsonObject, action: String): List<FieldSpec> {
    val params = schema["params"] as? JsonObject
    val src = params ?: schema
    val branches = branchesOf(src)

    if (branches.isNotEmpty()) {
        val applicable = branches.filter { branchApplies(it, action) }
        val merged = mutableMapOf<String, JsonElement>()
        (if (applicable.isNotEmpty()) applicable else branches).forEach { b ->
            val props = propsOf(b)
            props?.forEach { (k, e) -> merged.putIfAbsent(k, e) }
        }
        if (merged.isNotEmpty()) {
            return parseSchema(JsonObject(merged), emptyList()).filterNot { it.name in IMPLICIT }
        }
    }
    return parseSchema(src, requiredOf(src)).filterNot { it.name in IMPLICIT }
}

/**
 * Properties live wherever the branch puts them. A JSON Schema conditional
 * nests them under `then`; a hand-rolled branch may hang them off the branch or
 * off a nested `params`. Checking only the branch root silently yields nothing,
 * which is how a form ends up with one stray field instead of the real set.
 */
private fun propsOf(b: JsonObject): JsonObject? {
    val then = b["then"] as? JsonObject
    val target = then ?: (b["params"] as? JsonObject) ?: b
    return target["properties"] as? JsonObject
        ?: (target["then"] as? JsonObject)?.get("properties") as? JsonObject
}

private fun branchesOf(o: JsonObject): List<JsonObject> {
    val arr = (o["allOf"] as? JsonArray)
        ?: (o["oneOf"] as? JsonArray)
        ?: (o["anyOf"] as? JsonArray)
        ?: return emptyList()
    return arr.mapNotNull { it as? JsonObject }
}

/**
 * True when the branch has no guard, or its guard names this action. Both the
 * discriminator spellings are accepted: a direct title/const, and the JSON
 * Schema conditional form { if: { properties: { action: { const } } } }.
 */
private fun branchApplies(b: JsonObject, action: String): Boolean {
    val guard = (b["if"] as? JsonObject)
        ?: return true
    val cond = (guard["properties"] as? JsonObject)?.get("action") as? JsonObject
    val raw = cond?.get("const") ?: cond?.get("enum")
    val named = when (raw) {
        is JsonPrimitive -> raw.contentOrNull()
        is JsonArray -> raw.mapNotNull { (it as? JsonPrimitive)?.contentOrNull() }
            .firstOrNull { it == action }
            ?.let { action }
        else -> null
    }
    return named == null || named == action
}

private fun requiredOf(o: JsonObject): List<String> =
    (o["required"] as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull() } ?: emptyList()

/**
 * Documented refusals, from get_tool_help.
 *
 * Upwork's wording matters here: these combinations are "refused rather than
 * silently ignored". So the field is hidden and the reason is shown, instead of
 * letting the user assemble a request that comes back as an error and having to
 * work out which box was the problem.
 */
fun suppression(spec: FieldSpec, values: Map<String, String>): String? {
    val jobType = values["job_type"]
    val title = values["title"].orEmpty()
    return when {
        spec.name.startsWith("rate_") && jobType == "fixed" ->
            "Hourly only — Job Type is fixed-price."
        spec.name.startsWith("budget_") && jobType == "hourly" ->
            "Fixed-price only — Job Type is hourly."
        spec.name == "query" && title.isNotBlank() ->
            "Cannot be combined with Title."
        spec.name == "sort" && title.isNotBlank() && values["sort"] == "relevance" ->
            "Relevance drops the Title filter entirely."
        else -> null
    }
}

/** A value left behind by a suppression must not be sent. */
fun effectiveValues(values: Map<String, String>): Map<String, String> {
    val jobType = values["job_type"]
    val title = values["title"].orEmpty()
    return values.filter { (k, v) ->
        v.isNotBlank() &&
            !(k.startsWith("rate_") && jobType == "fixed") &&
            !(k.startsWith("budget_") && jobType == "hourly") &&
            !(k == "query" && title.isNotBlank()) &&
            !(k == "sort" && title.isNotBlank() && v == "relevance")
    }
}
