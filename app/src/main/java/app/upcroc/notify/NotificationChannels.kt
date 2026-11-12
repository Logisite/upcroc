package app.upcroc.notify

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.media.AudioAttributes
import android.net.Uri
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat
import app.upcroc.R

object NotificationChannels {
    const val STATUS = "upcroc_status_v1"
    const val SILENT = "upcroc_jobs_silent_v1"
    const val AUTH = "upcroc_auth_v1"
    private const val JOB_PREFIX = "upcroc_jobs_"

    fun ensureAuth(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        if (manager.getNotificationChannel(AUTH) == null) {
            val channel = NotificationChannel(AUTH, "Sign-in", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Keeps Upcroc alive while you complete Upwork sign-in."
                setSound(null, null)
                enableVibration(false)
                setShowBadge(false)
            }
            manager.createNotificationChannel(channel)
        }
    }
    fun ensureStatus(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        if (manager.getNotificationChannel(STATUS) == null) {
            val channel = NotificationChannel(STATUS, "Watcher status", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Ongoing Upcroc watcher status. Silent."
                setShowBadge(false)
                setSound(null, null)
                enableVibration(false)
            }
            manager.createNotificationChannel(channel)
        }
    }

    fun jobChannel(context: Context, soundUri: String?, vibration: Boolean, quiet: Boolean): String {
        val manager = context.getSystemService(NotificationManager::class.java)
        if (quiet) {
            if (manager.getNotificationChannel(SILENT) == null) {
                val channel = NotificationChannel(SILENT, "Job alerts (quiet hours)", NotificationManager.IMPORTANCE_LOW).apply {
                    description = "New Upwork jobs during quiet hours. No sound or vibration."
                    setSound(null, null)
                    enableVibration(false)
                    setShowBadge(true)
                }
                manager.createNotificationChannel(channel)
            }
            return SILENT
        }

        val uri = soundUri?.takeIf { it.isNotBlank() }?.let(Uri::parse) ?: Settings.System.DEFAULT_NOTIFICATION_URI
        val id = JOB_PREFIX + (uri.toString() + vibration).hashCode().toUInt().toString(16)
        if (manager.getNotificationChannel(id) == null) {
            val channel = NotificationChannel(id, "Job alerts", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "New Upwork job posts matching your searches."
                setSound(
                    uri,
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build(),
                )
                enableVibration(vibration)
                if (vibration) vibrationPattern = longArrayOf(0, 260, 160, 260)
                enableLights(true)
                lightColor = 0xFF4F8DFD.toInt()
                setShowBadge(true)
                lockscreenVisibility = android.app.Notification.VISIBILITY_PRIVATE
            }
            manager.createNotificationChannel(channel)
        }
        manager.notificationChannels
            .filter { it.id.startsWith(JOB_PREFIX) && it.id != id }
            .forEach { manager.deleteNotificationChannel(it.id) }
        return id
    }

    fun canNotify(context: Context): Boolean = NotificationManagerCompat.from(context).areNotificationsEnabled()

    fun icon(): Int = R.drawable.ic_notification
}
