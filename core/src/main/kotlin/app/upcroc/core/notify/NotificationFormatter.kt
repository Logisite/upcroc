package app.upcroc.core.notify

import app.upcroc.core.model.Job

data class NotificationText(
    val title: String,
    val body: String,
)

object NotificationFormatter {
    fun forJob(
        job: Job,
        now: Long,
        showClientStats: Boolean = true,
        showProposals: Boolean = true,
    ): NotificationText {
        val age = job.ageLabel(now)
        val proposals = job.proposalsTier.ifBlank { "Proposals" }
        val summary = if (showProposals) "$age · $proposals" else age

        val kind = if (job.isHourly) "hourly" else "fixed"
        val pay = job.budget.ifBlank { job.jobType.ifBlank { "Budget not listed" } }
        val country = job.country.ifBlank { "Location not listed" }
        val level = job.experienceLevel.ifBlank { "Level not listed" }

        val lines = mutableListOf<String>()
        if (job.title.isNotBlank()) lines += job.title
        lines += ""
        lines += "$pay $kind · $country · $level"
        if (job.url.isNotBlank()) lines += job.url

        if (showClientStats) {
            val bits = mutableListOf<String>()
            job.connectsCost?.takeIf { it.isNotBlank() }?.let { bits += "$it Connects" }
            job.jobsPosted?.let { if (it > 0) bits += "$it jobs" }
            job.hireRate?.let { bits += "$it% hire" }
            job.invitesSent?.let { if (it >= 1) bits += "sent $it invites" }
            if (bits.isNotEmpty()) lines += bits.joinToString(" · ")
        }
        return NotificationText(summary, lines.joinToString("\n").trim())
    }

    fun summary(jobs: List<Job>, now: Long): NotificationText {
        if (jobs.isEmpty()) return NotificationText("No new jobs", "")
        if (jobs.size == 1) return forJob(jobs.first(), now)
        val title = "${jobs.size} new jobs · Upcroc"
        val lines = jobs.take(6).map { job ->
            val age = job.ageLabel(now)
            "• $age · ${job.title.ifBlank { "Untitled job" }}"
        }.toMutableList()
        if (jobs.size > 6) lines += "…and ${jobs.size - 6} more"
        return NotificationText(title, lines.joinToString("\n"))
    }

    fun authNeeded(): NotificationText = NotificationText(
        "Upcroc needs you to sign in",
        "Your Upwork session expired. Open Upcroc and sign in again to keep alerts running.",
    )
}
