package app.upcroc.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        val started = runCatching {
            ContextCompat.startForegroundService(
                context,
                Intent(context, WatcherService::class.java).setAction(action),
            )
        }.isSuccess
        if (!started) {
            OneTimeCheckWorker.enqueue(context)
        }
    }
}
