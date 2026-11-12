package app.upcroc.core.filter

import app.upcroc.core.config.AdvancedConfig
import app.upcroc.core.config.QuietHours
import app.upcroc.core.model.Job
import app.upcroc.core.model.SearchFilters
import app.upcroc.core.model.SearchGroup
import app.upcroc.core.model.SearchMode
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.time.Instant
import java.time.ZoneId

object SearchParams {
    fun build(
        group: SearchGroup,
        filters: SearchFilters,
        advanced: AdvancedConfig,
        page: Int = 0,
    ): JsonObject {
        val params = buildJsonObject {
            put("limit", advanced.pageSize)
            when (group.mode) {
                SearchMode.SMART_SEARCH -> {
                    put("mode", "most_recent")
                    put("days_posted", 1)
                    if (group.keywords.isNotBlank()) put("title", group.keywords.trim())
                }
                SearchMode.SEARCH -> {
                    put("title", group.keywords.trim())
                    put("sort", "recency")
                    filters.jobType?.let { put("job_type", it) }
                    filters.category?.let { put("category", it) }
                    filters.subcategory?.let { put("subcategory", it) }
                    filters.budgetMin?.let { put("budget_min", it) }
                    filters.budgetMax?.let { put("budget_max", it) }
                    filters.rateMin?.let { put("rate_min", it) }
                    filters.rateMax?.let { put("rate_max", it) }
                    filters.experienceLevel?.let { put("experience_level", it) }
                    filters.proposalsMin?.let { put("proposals_min", it) }
                    filters.proposalsMax?.let { put("proposals_max", it) }
                    filters.clientLocation?.let { put("client_location", it) }
                    if (filters.verifiedPaymentOnly) put("verified_payment_only", true)
                    filters.clientHiresMin?.let { put("client_hires_min", it) }
                    filters.clientSpendMin?.let { put("client_spend_min", it) }
                    filters.duration?.let { put("duration", it) }
                    filters.hoursPerWeekMin?.let { put("hours_per_week_min", it) }
                    filters.hoursPerWeekMax?.let { put("hours_per_week_max", it) }
                    filters.englishLevel?.let { put("english_level", it) }
                    filters.skills.takeIf { it.isNotBlank() }?.let { put("skills", it) }
                }
            }
            if (advanced.pageParam.isNotBlank() && page > 0) {
                put(advanced.pageParam, page)
            }
        }
        return merge(params, group.extraParams)
    }

    fun merge(base: JsonObject, extra: JsonObject?): JsonObject {
        if (extra == null || extra.isEmpty()) return base
        return buildJsonObject {
            base.forEach { (k, v) -> put(k, v) }
            extra.forEach { (k, v) -> put(k, v) }
        }
    }
}

object JobFilter {
    fun matches(job: Job, filters: SearchFilters): Boolean {
        val title = job.title.lowercase()
        val description = job.description.lowercase()

        val includes = split(filters.includeKeywords)
        if (includes.isNotEmpty() && includes.none { title.contains(it) || description.contains(it) }) {
            return false
        }
        val excludes = split(filters.excludeKeywords)
        if (excludes.any { title.contains(it) || description.contains(it) }) {
            return false
        }
        val countries = split(filters.countries)
        if (countries.isNotEmpty()) {
            val country = job.country.lowercase()
            if (countries.none { country.contains(it) }) return false
        }
        if (filters.jobType != null && job.jobType.isNotBlank() &&
            !job.jobType.equals(filters.jobType, ignoreCase = true)
        ) {
            return false
        }
        if (filters.experienceLevel != null && job.experienceLevel.isNotBlank() &&
            !job.experienceLevel.equals(filters.experienceLevel, ignoreCase = true)
        ) {
            return false
        }
        return true
    }

    private fun split(value: String): List<String> = value
        .split(',', '\n')
        .map { it.trim().lowercase() }
        .filter { it.length >= 2 }
}

object QuietHoursCalc {
    fun isQuiet(now: Long, quiet: QuietHours, zone: ZoneId = ZoneId.systemDefault()): Boolean {
        if (!quiet.enabled) return false
        val minute = Instant.ofEpochMilli(now).atZone(zone).toLocalTime().let { it.hour * 60 + it.minute }
        return quiet.contains(minute)
    }

    fun nextActiveStart(now: Long, quiet: QuietHours, zone: ZoneId = ZoneId.systemDefault()): Long {
        if (!quiet.enabled) return now
        if (!isQuiet(now, quiet, zone)) return now
        val zoned = Instant.ofEpochMilli(now).atZone(zone)
        var target = zoned.toLocalDate().atStartOfDay(zone).plusMinutes(quiet.endMinute.toLong())
        if (!target.isAfter(zoned)) target = target.plusDays(1)
        return target.toInstant().toEpochMilli()
    }
}
