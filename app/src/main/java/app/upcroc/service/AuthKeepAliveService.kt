package app.upcroc.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import app.upcroc.R
import app.upcroc.notify.NotificationChannels

/**
 * Keeps the app process alive while the Custom Tab is in front. Without this, some
 * OEMs (including TECNO) reclaim the process and the loopback listener dies before
 * Upwork redirects back to it.
 */
class AuthKeepAliveService : Service() {
    override fun onCreate() {
        super.onCreate()
        NotificationChannels.ensureAuth(this)
        val notification = NotificationCompat.Builder(this, NotificationChannels.AUTH)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Signing in to Upwork")
            .setContentText("Complete the browser sign-in, then return to Upcroc.")
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setSilent(true)
            .setShowWhen(false)
            .build()
        when {
            Build.VERSION.SDK_INT >= 34 -> startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
            )
            Build.VERSION.SDK_INT >= 29 -> startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
            )
            else -> startForeground(NOTIFICATION_ID, notification)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
        }
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        runCatching { stopForeground(STOP_FOREGROUND_REMOVE) }
        super.onDestroy()
    }

    companion object {
        private const val ACTION_STOP = "app.upcroc.action.AUTH_KEEPALIVE_STOP"
        private const val NOTIFICATION_ID = 2000

        fun start(context: Context) {
            runCatching {
                ContextCompat.startForegroundService(
                    context,
                    Intent(context, AuthKeepAliveService::class.java),
                )
            }
        }

        fun stop(context: Context) {
            runCatching {
                context.startService(
                    Intent(context, AuthKeepAliveService::class.java).setAction(ACTION_STOP)
                )
                context.stopService(Intent(context, AuthKeepAliveService::class.java))
            }
        }
    }
}
