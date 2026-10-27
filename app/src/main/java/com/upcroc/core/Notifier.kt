package com.upcroc.core

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.net.Uri
import android.provider.Settings
import com.upcroc.R
import com.upcroc.data.JobEntity
import com.upcroc.ui.MainActivity

/**
 * Notification body, layout 3: title, pay line, clean link, then client and
 * cost facts along the bottom. The proposals and age line is carried in the
 * collapsed summary, which also carries the real age.
 */
fun JobEntity.body(): String {
    val kind = if (jobType == "hourly") "hourly" else "fixed"
    val facts = buildList {
        if (connectsCost.isNotEmpty()) add("$connectsCost connects")
        if (jobsPosted.isNotEmpty()) add("$jobsPosted jobs")
        if (hireRate.isNotEmpty()) add("$hireRate% hire")
        // One invite already marks an invite-first client rather than an open
        // competition, so the flag starts at 1.
        if (invitesSent >= 1) add("sent $invitesSent invites")
    }
    return buildString {
        appendLine(title)
        appendLine()
        appendLine("$pay $kind · ${country.ifEmpty { "?" }} · ${experienceLevel.ifEmpty { "?" }}")
        appendLine(url)
        if (facts.isNotEmpty()) appendLine(facts.joinToString(" · "))
    }.trimEnd()
}

fun JobEntity.summary(ageSeconds: Long): String {
    val minutes = (ageSeconds / 60).coerceAtLeast(0)
    val age = if (minutes < 60) "${minutes}m ago" else "${minutes / 60}h ago"
    return "$age · ${proposalsTier.ifEmpty { "?" }} proposals"
}

class Notifier(private val context: Context) {

    private val manager: NotificationManager =
        context.getSystemService(NotificationManager::class.java)

    init {
        manager.createNotificationChannel(
            NotificationChannel(SERVICE, "Watcher", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Ongoing watcher status"
            },
        )
        for (sound in listOf(false, true)) {
            for (vibrate in listOf(false, true)) channelFor(sound, vibrate)
        }
    }

    /**
     * Android locks sound and vibration to the channel, so a user-selectable
     * combination needs one channel per combination rather than a per-notification
     * setting. Channels are immutable once created, hence the four variants.
     *
     * The silent variant stays at DEFAULT, not LOW: LOW suppresses heads-up
     * entirely, so switching sound off would silently demote every job banner
     * to a shade-only notification.
     */
    private fun channelFor(sound: Boolean, vibrate: Boolean): String {
        val id = buildString {
            append(JOBS)
            if (sound) append("_sound")
            if (vibrate) append("_vibe")
        }
        if (manager.getNotificationChannel(id) != null) return id
        val channel = NotificationChannel(
            id,
            "Jobs",
            if (sound) NotificationManager.IMPORTANCE_HIGH
            else NotificationManager.IMPORTANCE_DEFAULT,
        )
        channel.enableVibration(vibrate)
        if (vibrate) channel.vibrationPattern = longArrayOf(0, 400, 200, 400)
        val attrs = AudioAttributes.Builder()
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .setUsage(AudioAttributes.USAGE_NOTIFICATION)
            .build()
        // Must be set explicitly in both directions: leaving it unset on the
        // silent variant inherits the platform ringtone, so turning sound off
        // would not actually silence the phone.
        channel.setSound(
            if (sound) Settings.System.DEFAULT_NOTIFICATION_URI else null,
            attrs,
        )
        manager.createNotificationChannel(channel)
        return id
    }

    fun notify(
        job: JobEntity,
        ageSeconds: Long,
        enabled: Boolean,
        sound: Boolean,
        vibrate: Boolean,
    ) {
        if (!enabled) return
        val open = Intent(Intent.ACTION_VIEW, Uri.parse(job.url)).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val pi = PendingIntent.getActivity(
            context, 0, open,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val n = Notification.Builder(context, channelFor(sound, vibrate))
            .setSmallIcon(R.drawable.ic_stat)
            .setContentTitle(job.title.ifEmpty { "Upwork job" })
            .setContentText(job.summary(ageSeconds))
            .setStyle(Notification.BigTextStyle().bigText(job.body()))
            .setContentIntent(pi)
            // A banner, not a sticky row. Ongoing notifications never float, so
            // this has to stay false for the job to appear over the screen.
            .setOngoing(false)
            .setAutoCancel(true)
            .setWhen(System.currentTimeMillis())
            .build()
        // One slot, newest wins, matching the reference behaviour.
        manager.notify(ONGOING_JOB_ID, n)
    }

    fun authRequired() {
        val intent = Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        val pi = PendingIntent.getActivity(
            context, 1, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val n = Notification.Builder(context, channelFor(true, true))
            .setSmallIcon(R.drawable.ic_stat)
            .setContentTitle("upcroc: sign-in needed")
            .setContentText("Tap to reconnect to Upwork")
            .setContentIntent(pi)
            .setAutoCancel(true)
            .build()
        manager.notify(9_999, n)
    }

    /**
     * The watcher status. Sticky and silent by design: it should be there when
     * you look, and never interrupt you.
     *
     * The text deliberately has no per-second countdown. Re-posting a
     * notification whose text changes every second re-alerts on some Android
     * skins, and "next check in ~2m" is what you want from a glance. The live
     * countdown lives on the in-app ticker.
     */
    fun ongoing(t: Ticker): Notification {
        val state = if (t.inCheck) "checking" else if (t.watching) "watching" else "idle"
        val until = if (t.watching && !t.inCheck) {
            val m = t.bucketLeft / 60
            val s = t.bucketLeft % 60
            if (m > 0) "next check in ${m}m${s.toString().padStart(2, '0')}s"
            else "next check in ${s}s"
        } else {
            "not checking on a timer"
        }
        val text = "$until · $state · ${t.lastNew} new · ${t.lastNotified} notified"
        val intent = Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        val pi = PendingIntent.getActivity(
            context, 2, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return Notification.Builder(context, SERVICE)
            .setSmallIcon(R.drawable.ic_stat)
            .setContentTitle("upcroc")
            .setContentText(text)
            .setContentIntent(pi)
            .setOngoing(true)
            .build()
    }

    companion object {
        const val JOBS = "jobs"
        const val SERVICE = "service"
        const val ONGOING_ID = 1
        const val ONGOING_JOB_ID = 2
    }
}