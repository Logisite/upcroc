package app.upcroc.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import app.upcroc.UpcrocApp

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        val logger = (context.applicationContext as? UpcrocApp)?.container?.logger
        logger?.info("alarm: $action received")
        val started = runCatching {
            ContextCompat.startForegroundService(
                context,
                Intent(context, WatcherService::class.java).setAction(action),
            )
        }.isSuccess
        if (!started) {
            logger?.warn("alarm: foreground service start refused; using worker")
            OneTimeCheckWorker.enqueue(context)
        }
    }
}
