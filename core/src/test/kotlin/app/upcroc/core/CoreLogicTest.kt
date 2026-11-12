package app.upcroc.core

import app.upcroc.core.config.AdvancedConfig
import app.upcroc.core.auth.OAuthClientConfig
import app.upcroc.core.config.QuietHours
import app.upcroc.core.config.RateBudgetConfig
import app.upcroc.core.engine.BudgetState
import app.upcroc.core.engine.RateBudget
import app.upcroc.core.filter.JobFilter
import app.upcroc.core.filter.QuietHoursCalc
import app.upcroc.core.filter.SearchParams
import app.upcroc.core.model.Job
import app.upcroc.core.model.SearchFilters
import app.upcroc.core.model.SearchGroup
import app.upcroc.core.model.SearchMode
import app.upcroc.core.model.parseJobs
import app.upcroc.core.notify.NotificationFormatter
import app.upcroc.core.util.CoreJson
import app.upcroc.core.util.cleanUrl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.ZoneId

class RateBudgetTest {
    private var now = 1_000_000L
    private val budget = RateBudget { now }

    @Test
    fun `soft budget warns without blocking`() {
        val config = RateBudgetConfig(dailyLimit = 10, hourlyLimit = 10, minuteLimit = 10, warningPercent = 80)
        var state = budget.decode(null)
        repeat(8) { state = budget.consume(state, config).state }
        val result = budget.consume(state, config)
        assertTrue(result.allowed)
        assertTrue(result.warning)
    }

    @Test
    fun `hard cap blocks at the daily limit`() {
        val config = RateBudgetConfig(dailyLimit = 2, hourlyLimit = 100, minuteLimit = 100, hardCap = true)
        var state = budget.decode(null)
        state = budget.consume(state, config).state
        state = budget.consume(state, config).state
        val result = budget.consume(state, config)
        assertFalse(result.allowed)
        assertEquals(2, result.state.dayCount)
    }

    @Test
    fun `windows reset on the next day`() {
        val config = RateBudgetConfig(dailyLimit = 1, hourlyLimit = 1, minuteLimit = 1, hardCap = true)
        var state = budget.decode(null)
        state = budget.consume(state, config).state
        assertFalse(budget.consume(state, config).allowed)
        now += 86_400_000L
        val result = budget.consume(state, config)
        assertTrue(result.allowed)
        assertEquals(1, result.state.dayCount)
    }

    @Test
    fun `429 pauses the budget`() {
        val state = budget.decode(null)
        val paused = budget.on429(state, retryAfterSec = 90, pause = true)
        assertTrue(paused.pausedUntil > now)
        val result = budget.consume(paused, RateBudgetConfig())
        assertFalse(result.allowed)
        assertEquals(1, paused.total429)
    }
}

class JobFilterTest {
    private val job = Job(
        id = "1",
        title = "AI Automation Engineer",
        country = "Australia",
        jobType = "hourly",
        experienceLevel = "expert",
    )

    @Test
    fun `include match is case insensitive`() {
        assertTrue(JobFilter.matches(job, SearchFilters(includeKeywords = "automation")))
    }

    @Test
    fun `exclude wins`() {
        assertFalse(JobFilter.matches(job, SearchFilters(excludeKeywords = "engineer")))
    }

    @Test
    fun `country filter works`() {
        assertTrue(JobFilter.matches(job, SearchFilters(countries = "australia")))
        assertFalse(JobFilter.matches(job, SearchFilters(countries = "canada")))
    }
}

class SearchParamsTest {
    @Test
    fun `search includes shared filters`() {
        val group = SearchGroup(mode = SearchMode.SEARCH, keywords = "Kotlin")
        val params = SearchParams.build(
            group,
            SearchFilters(jobType = "hourly", rateMin = 40.0, verifiedPaymentOnly = true),
            AdvancedConfig(pageSize = 20),
        )
        assertEquals("Kotlin", params["title"]?.toString()?.trim('"'))
        assertEquals("recency", params["sort"]?.toString()?.trim('"'))
        assertEquals("hourly", params["job_type"]?.toString()?.trim('"'))
        assertEquals("40.0", params["rate_min"]?.toString())
        assertEquals("true", params["verified_payment_only"]?.toString())
    }

    @Test
    fun `smart search uses limit only`() {
        val group = SearchGroup(mode = SearchMode.SMART_SEARCH)
        val params = SearchParams.build(group, SearchFilters(), AdvancedConfig(pageSize = 15))
        assertEquals("15", params["limit"]?.toString())
        assertFalse(params.containsKey("sort"))
    }
}

class ParsingTest {
    @Test
    fun `parses jobs and strips tracking`() {
        val text = """
            {"jobs":[{"id":"abc","title":"AI dev","url":"https://www.upwork.com/jobs/~123?tracking=1",
            "published_date":"2026-01-01T00:00:00Z","budget":"${'$'}20-${'$'}30/hr","job_type":"hourly",
            "proposals_tier":"Fewer than 5","experience_level":"Expert","client":{"country":"Australia"}}]}
        """.trimIndent()
        val jobs = parseJobs(CoreJson.parseToJsonElement(text), now = 1L)
        assertEquals(1, jobs.size)
        assertEquals("https://www.upwork.com/jobs/~123", jobs.first().url)
        assertEquals("Australia", jobs.first().country)
        assertEquals(Instant.parse("2026-01-01T00:00:00Z").toEpochMilli(), jobs.first().publishedAt)
    }

    @Test
    fun `clean url leaves plain urls alone`() {
        assertEquals("https://example.com/a", cleanUrl("https://example.com/a?x=1"))
        assertEquals("", cleanUrl(""))
    }
}

class QuietHoursTest {
    private val quiet = QuietHours(enabled = true, startMinute = 23 * 60, endMinute = 7 * 60)
    private val zone = ZoneId.of("UTC")

    @Test
    fun `late night is quiet`() {
        val at = Instant.parse("2026-01-01T23:30:00Z").toEpochMilli()
        assertTrue(QuietHoursCalc.isQuiet(at, quiet, zone))
    }

    @Test
    fun `midday is not quiet`() {
        val at = Instant.parse("2026-01-01T12:00:00Z").toEpochMilli()
        assertFalse(QuietHoursCalc.isQuiet(at, quiet, zone))
    }

    @Test
    fun `next active start is morning`() {
        val at = Instant.parse("2026-01-01T23:30:00Z").toEpochMilli()
        val next = QuietHoursCalc.nextActiveStart(at, quiet, zone)
        assertEquals(Instant.parse("2026-01-02T07:00:00Z").toEpochMilli(), next)
    }
}

class OAuthConfigTest {
    @Test
    fun `decodes snake case client document`() {
        val cfg = OAuthClientConfig.decode(
            """{"client_id":"https://example/client.json","client_name":"x","authorization_endpoint":"https://auth","token_endpoint":"https://token","revocation_endpoint":"https://revoke","resource":"https://mcp","redirect_uri_template":"http://127.0.0.1:{port}/callback","scopes":[]}"""
        )
        assertEquals("https://example/client.json", cfg.clientId)
        assertEquals("https://auth", cfg.authorizationEndpoint)
        assertEquals("https://token", cfg.tokenEndpoint)
        assertEquals("http://127.0.0.1:1234/callback", cfg.redirectUri(1234))
    }
}

class NotificationFormatterTest {
    @Test
    fun `body carries pay country url and stats`() {
        val job = Job(
            id = "1",
            title = "Business Systems Architect",
            url = "https://www.upwork.com/jobs/~1",
            publishedAt = 0,
            budget = "${'$'}14.00-${'$'}27.00/hr",
            jobType = "hourly",
            experienceLevel = "Expert",
            country = "United States",
            connectsCost = "12",
            jobsPosted = 3,
            hireRate = 50,
            invitesSent = 2,
        )
        val text = NotificationFormatter.forJob(job, now = 120_000)
        assertNotNull(text.body)
        assertTrue(text.body.contains("Business Systems Architect"))
        assertTrue(text.body.contains("https://www.upwork.com/jobs/~1"))
        assertTrue(text.body.contains("Connects"))
        assertTrue(text.title.contains("Proposals"))
    }
}
