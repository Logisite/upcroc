package com.upcroc.ui

/**
 * Parses find_jobs' parameter catalogue out of `get_tool_help`.
 *
 * Why this exists: find_jobs publishes an `inputSchema` of only three fields —
 * action, org_uid, and a free-form `params` object with no properties and no
 * allOf. The per-action filters are documented nowhere in the MCP schema, so
 * there is no JSON Schema to render a form from.
 *
 * They *are* documented, by the server, in `get_tool_help`. So the field list
 * still comes from Upwork and still updates when Upwork updates it — it just
 * arrives as prose rather than JSON. This walks that prose.
 *
 * Acceptance is deliberately strict. A token is accepted as a parameter only if
 * its parenthesis opens with a declared type, or is a pure slash-separated
 * value list. That keeps response fields out: the doc mentions proposals_tier,
 * applied, featured and liveness, but always as outputs, and always in a
 * parenthesis that fails this test. Erring toward fewer fields is the right
 * failure mode — a missing filter costs a search, an invented one costs a
 * rejected request against a real account.
 */
object HelpSchema {

    private val TYPES = setOf("string", "number", "integer", "boolean", "array")

    private val ACTION_HEAD = Regex("^- (search|get|smart_search):", RegexOption.MULTILINE)

    fun parse(description: String): Map<String, List<FieldSpec>> {
        val marks = ACTION_HEAD.findAll(description).map { it.range.first to it.groupValues[1] }.toList()
        if (marks.isEmpty()) return emptyMap()
        val out = mutableMapOf<String, List<FieldSpec>>()
        marks.forEachIndexed { i, (start, action) ->
            val end = marks.getOrNull(i + 1)?.first ?: description.length
            out[action] = section(description.substring(start, end), action)
        }
        return out
    }

    private fun section(body: String, action: String): List<FieldSpec> {
        val specs = mutableListOf<FieldSpec>()
        PATTERN.findAll(body).forEach { m ->
            val name = m.groupValues[1]
            if (name in specs.map { it.name }) return@forEach
            val spec = specFor(name, m.groupValues[2], action) ?: return@forEach
            specs += spec
        }
        return specs.sortedWith(compareBy({ groupRank(it.group) }, { it.name }))
    }

    /** Returns null when the parenthesis is prose rather than a declaration. */
    private fun specFor(name: String, inner: String, action: String): FieldSpec? {
        val text = inner.trim()
        val declared = text.substringBefore(' ').substringBefore(',').lowercase()

        val kind: FieldSpec.Kind
        val options: List<String>
        val min: Double?
        val max: Double?

        when {
            declared in TYPES -> {
                kind = when (declared) {
                    "boolean" -> FieldSpec.Kind.BOOLEAN
                    "number", "integer" -> FieldSpec.Kind.NUMBER
                    else -> FieldSpec.Kind.TEXT
                }
                options = emptyList()
                min = BOUNDS.find(text.lowercase())?.groupValues?.get(1)?.toDoubleOrNull()
                max = BOUNDS.find(text.lowercase())?.groupValues?.get(2)?.toDoubleOrNull()
            }
            // A bare slash-separated list is a closed value set: fixed/hourly.
            text.matches(ENUM) -> {
                kind = FieldSpec.Kind.ENUM
                options = text.split('/').map { it.trim() }.filter { it.isNotBlank() }
                min = null
                max = null
            }
            else -> return null
        }

        return FieldSpec(
            name = name,
            label = humanise(name),
            description = describe(text),
            kind = kind,
            options = options,
            min = min,
            max = max,
            required = text.contains("required", ignoreCase = true),
            group = GROUPS[name] ?: groupFor(action, kind),
        )
    }

    /** Trailing prose after the declaration, trimmed to something readable. */
    private fun describe(text: String): String {
        val tail = text.substringAfter('—', "").substringAfter('-', "")
        if (tail.isBlank()) return ""
        val flat = tail.replace(Regex("\\s+"), " ").trim().trimEnd('.')
        return if (flat.length > 140) flat.take(137) + "..." else flat
    }

    private val PATTERN = Regex("(?<![\\w.])([a-z][a-z_]{1,28})\\s*\\(([^)]{0,80})")
    private val ENUM = Regex("^[a-z][a-z_0-9]*(/[a-z][a-z_0-9]*)+$")
    private val BOUNDS = Regex("(\\d+)\\s*[–-]\\s*(\\d+)")

    private val GROUPS = mapOf(
        "query" to GROUP_MATCH, "title" to GROUP_MATCH, "skills" to GROUP_MATCH,
        "category" to GROUP_MATCH, "subcategory" to GROUP_MATCH,
        "budget_min" to GROUP_FILTERS, "budget_max" to GROUP_FILTERS,
        "rate_min" to GROUP_FILTERS, "rate_max" to GROUP_FILTERS,
        "job_type" to GROUP_FILTERS, "experience_level" to GROUP_FILTERS,
        "workload" to GROUP_FILTERS, "duration" to GROUP_FILTERS,
        "verified_payment_only" to GROUP_FILTERS, "previous_clients_only" to GROUP_FILTERS,
        "proposals_min" to GROUP_FILTERS, "proposals_max" to GROUP_FILTERS,
        "client_hires_min" to GROUP_CLIENT, "client_hires_max" to GROUP_CLIENT,
        "total_spent" to GROUP_CLIENT, "total_posted_jobs" to GROUP_CLIENT,
        "timezone" to GROUP_CLIENT, "location" to GROUP_CLIENT,
        "sort" to GROUP_RESULTS, "limit" to GROUP_RESULTS, "cursor" to GROUP_RESULTS,
        "include_full_details" to GROUP_RESULTS, "days_posted" to GROUP_RESULTS,
        "mode" to GROUP_RESULTS, "job_id" to GROUP_RESULTS,
    )

    private fun groupFor(action: String, kind: FieldSpec.Kind) = when {
        action == "get" -> GROUP_RESULTS
        kind == FieldSpec.Kind.ENUM -> GROUP_FILTERS
        else -> GROUP_OTHER
    }
}