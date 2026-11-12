package app.upcroc.core.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

@Serializable
enum class SearchMode { SMART_SEARCH, SEARCH }

@Serializable
data class SearchGroup(
    val id: String = "primary",
    val name: String = "Primary matches",
    val mode: SearchMode = SearchMode.SMART_SEARCH,
    val keywords: String = "",
    val enabled: Boolean = true,
    val intervalSec: Int = 90,
    /** 0 keeps the gap fixed at [intervalSec]. Otherwise checks land uniformly between the two. */
    val intervalMaxSec: Int = 300,
    val priority: Int = 0,
    val extraParams: JsonObject? = null,
) {
    /** Effective upper bound for one gap, used by the scheduler and the UI. */
    val intervalUpperSec: Int
        get() = intervalMaxSec.takeIf { it > intervalSec }?.coerceAtMost(300) ?: intervalSec
}

@Serializable
data class SearchFilters(
    val jobType: String? = null,
    val category: String? = null,
    val subcategory: String? = null,
    val budgetMin: Double? = null,
    val budgetMax: Double? = null,
    val rateMin: Double? = null,
    val rateMax: Double? = null,
    val experienceLevel: String? = null,
    val proposalsMin: Int? = null,
    val proposalsMax: Int? = null,
    val clientLocation: String? = null,
    val verifiedPaymentOnly: Boolean = false,
    val clientHiresMin: Int? = null,
    val clientSpendMin: Double? = null,
    val duration: String? = null,
    val hoursPerWeekMin: Int? = null,
    val hoursPerWeekMax: Int? = null,
    val englishLevel: String? = null,
    val includeKeywords: String = "",
    val excludeKeywords: String = "",
    val countries: String = "",
    val skills: String = "",
) {
    fun isEmpty(): Boolean = this == SearchFilters()
}

@Serializable
data class SearchDraft(
    val groups: List<SearchGroup> = defaultGroups(),
    val filters: SearchFilters = SearchFilters(),
    val updatedAt: Long = 0L,
)

fun defaultGroups(): List<SearchGroup> = listOf(
    SearchGroup(
        id = "primary",
        name = "Primary matches",
        mode = SearchMode.SMART_SEARCH,
        keywords = "",
        enabled = true,
        intervalSec = 90,
        intervalMaxSec = 300,
        priority = 0,
    ),
    SearchGroup(
        id = "secondary-1",
        name = "Keyword search 2",
        mode = SearchMode.SEARCH,
        keywords = "",
        enabled = false,
        intervalSec = 90,
        intervalMaxSec = 300,
        priority = 1,
    ),
    SearchGroup(
        id = "secondary-2",
        name = "Keyword search 3",
        mode = SearchMode.SEARCH,
        keywords = "",
        enabled = false,
        intervalSec = 90,
        intervalMaxSec = 300,
        priority = 2,
    ),
)
