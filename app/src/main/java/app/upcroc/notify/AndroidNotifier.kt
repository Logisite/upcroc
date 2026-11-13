package app.upcroc.notify

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import app.upcroc.MainActivity
import app.upcroc.core.config.WatcherConfig
import app.upcroc.core.filter.QuietHoursCalc
import app.upcroc.core.model.Job
import app.upcroc.core.model.SearchGroup
import app.upcroc.core.notify.NotificationFormatter
import app.upcroc.core.platform.LogLevel
import app.upcroc.core.platform.Logger
import app.upcroc.core.platform.Notifier
import app.upcroc.core.platform.WatcherStatus
import app.upcroc.service.WatcherService
import java.util.concurrent.atomic.AtomicInteger

class AndroidNotifier(
    private val context: Context,
    private val configProvider: () -> WatcherConfig,
    private val logger: Logger,
) : Notifier {
    private val manager = NotificationManagerCompat.from(context)
    private val requestCodes = AtomicInteger(4000)

    override suspend fun postJob(job: Job, group: SearchGroup?) {
        post(job, group, updateOnly = false)
    }

    override suspend fun updateJob(job: Job, group: SearchGroup?) {
        post(job, group, updateOnly = true)
    }

    private fun post(job: Job, group: SearchGroup?, updateOnly: Boolean) {
        if (!NotificationChannels.canNotify(context)) {
            logger.log(LogLevel.WARN, "notifications are disabled; skipped ${job.id}")
            return
        }
        val config = configProvider()
        val now = System.currentTimeMillis()
        val quiet = QuietHoursCalc.isQuiet(now, config.cadence.quietHours)
        val channel = NotificationChannels.jobChannel(context, config.notifications.soundUri, config.notifications.vibration, quiet)
        val text = NotificationFormatter.forJob(job, now, config.notifications.showClientStats, config.notifications.showProposals)
        val builder = NotificationCompat.Builder(context, channel)
            .setSmallIcon(NotificationChannels.icon())
            .setColor(0xFF4F8DFD.toInt())
            .setContentTitle(text.title)
            .setContentText(text.body.lineSequence().firstOrNull().orEmpty())
            .setStyle(NotificationCompat.BigTextStyle().bigText(text.body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_RECOMMENDATION)
            .setAutoCancel(true)
            .setOnlyAlertOnce(updateOnly)
            .setWhen(if (job.publishedAt > 0) job.publishedAt else now)
            .setShowWhen(true)
            .setGroup(GROUP_JOBS)
            .setGroupAlertBehavior(NotificationCompat.GROUP_ALERT_CHILDREN)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setLocalOnly(true)
            .setContentIntent(contentIntent(job.url))
        if (updateOnly) builder.setSilent(true)
        if (job.url.isNotBlank()) {
            builder.addAction(0, "Open on Upwork", contentIntent(job.url))
        }
        manager.notify(notificationId(job.id), builder.build())
    }

    override suspend fun postSummary(jobs: List<Job>, group: SearchGroup?) {
        if (!NotificationChannels.canNotify(context) || jobs.isEmpty()) return
        val config = configProvider()
        val now = System.currentTimeMillis()
        val quiet = QuietHoursCalc.isQuiet(now, config.cadence.quietHours)
        val channel = NotificationChannels.jobChannel(context, config.notifications.soundUri, config.notifications.vibration, quiet)
        val text = NotificationFormatter.summary(jobs, now)
        val style = NotificationCompat.InboxStyle()
        jobs.take(6).forEach { job ->
            style.addLine("${job.ageLabel(now)} · ${job.title.ifBlank { "Untitled job" }}")
        }
        if (jobs.size > 6) style.setSummaryText("+${jobs.size - 6} more")
        val builder = NotificationCompat.Builder(context, channel)
            .setSmallIcon(NotificationChannels.icon())
            .setColor(0xFF4F8DFD.toInt())
            .setContentTitle(text.title)
            .setContentText(text.body.lineSequence().firstOrNull().orEmpty())
            .setStyle(style)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_RECOMMENDATION)
            .setAutoCancel(true)
            .setWhen(now)
            .setGroup(GROUP_JOBS)
            .setGroupSummary(true)
            .setContentIntent(appIntent())
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setLocalOnly(true)
        manager.notify(ID_SUMMARY, builder.build())
    }

    override suspend fun postAuthNeeded() {
        if (!NotificationChannels.canNotify(context)) return
        val text = NotificationFormatter.authNeeded()
        val config = configProvider()
        val channel = NotificationChannels.jobChannel(context, config.notifications.soundUri, config.notifications.vibration, quiet = false)
        val builder = NotificationCompat.Builder(context, channel)
            .setSmallIcon(NotificationChannels.icon())
            .setColor(0xFFE06C6C.toInt())
            .setContentTitle(text.title)
            .setContentText(text.body.lineSequence().firstOrNull().orEmpty())
            .setStyle(NotificationCompat.BigTextStyle().bigText(text.body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ERROR)
            .setAutoCancel(true)
            .setContentIntent(appIntent())
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
        manager.notify(ID_AUTH, builder.build())
    }

    override suspend fun postStatus(status: WatcherStatus) {
        manager.notify(ID_STATUS, statusNotification(status))
    }

    fun statusNotification(status: WatcherStatus): android.app.Notification {
        NotificationChannels.ensureStatus(context)
        val now = System.currentTimeMillis()
        val title = if (status.running) "Upcroc is watching" else "Upcroc is paused"
        val parts = mutableListOf<String>()
        val lastAgeSec = status.lastCheckAt?.let { ((now - it) / 1000).coerceAtLeast(0) }
        val next = status.nextCheckAt
        if (status.running && (lastAgeSec ?: 0L) > 10 * 60L) {
            parts += "Android may be delaying checks"
        }
        if (status.running && next != null && next > 0L && now >= next) {
            parts += "Check was due; running shortly"
        }
        if (status.failures > 0) parts += "${status.failures} failure(s)"
        if (parts.isEmpty()) {
            parts += if (status.running) "Waiting for the next check" else "No checks running"
        }
        val stopIntent = PendingIntent.getService(
            context,
            12,
            Intent(context, WatcherService::class.java).setAction(WatcherService.ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val builder = NotificationCompat.Builder(context, NotificationChannels.STATUS)
            .setSmallIcon(NotificationChannels.icon())
            .setContentTitle(title)
            .setContentText(parts.joinToString(" · "))
            .setStyle(NotificationCompat.BigTextStyle().bigText(parts.joinToString(" · ")))
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setContentIntent(appIntent())
            .addAction(0, "Stop", stopIntent)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
        if (status.running && next != null) {
            // Android ticks this countdown itself, so it stays honest when the
            // process is frozen. At zero it simply waits for the delayed check.
            builder
                .setWhen(if (next > now) next else now)
                .setShowWhen(true)
                .setUsesChronometer(true)
                .setChronometerCountDown(true)
        } else {
            builder.setShowWhen(false)
        }
        return builder.build()
    }

    override suspend fun cancelStatus() {
        manager.cancel(ID_STATUS)
    }

    fun postTest() {
        val job = Job(
            id = "test-alert",
            title = "Test alert: Upcroc is working",
            url = "https://www.upwork.com/",
            publishedAt = System.currentTimeMillis(),
            budget = "$25.00-$40.00/hr",
            jobType = "hourly",
            proposalsTier = "Fewer than 5",
            experienceLevel = "Expert",
            country = "Australia",
            connectsCost = "12",
            jobsPosted = 8,
            hireRate = 75,
            invitesSent = 1,
        )
        post(job, null, updateOnly = false)
    }

    private fun notificationId(jobId: String): Int {
        val raw = jobId.hashCode() and 0x7fffffff
        return if (raw < 3000) raw + 3000 else raw
    }

    private fun appIntent(): PendingIntent = PendingIntent.getActivity(
        context,
        10,
        Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private fun contentIntent(url: String): PendingIntent {
        val intent = if (url.isNotBlank()) {
            Intent(Intent.ACTION_VIEW, Uri.parse(url))
        } else {
            Intent(context, MainActivity::class.java)
        }
        return PendingIntent.getActivity(
            context,
            requestCodes.incrementAndGet(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private companion object {
        const val GROUP_JOBS = "upcroc_jobs"
        const val ID_STATUS = 1000
        const val ID_SUMMARY = 1001
        const val ID_AUTH = 1002
    }
}
