package app.upcroc.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import app.upcroc.UpcrocApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        if (action != Intent.ACTION_BOOT_COMPLETED &&
            action != Intent.ACTION_MY_PACKAGE_REPLACED &&
            action != Intent.ACTION_USER_UNLOCKED
        ) {
            return
        }
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val container = (context.applicationContext as UpcrocApp).container
                val config = container.settings.configOnce()
                val wasRunning = container.settings.runningFlow.first()
                val shouldStart = when (action) {
                    Intent.ACTION_USER_UNLOCKED -> config.advanced.alwaysOn
                    Intent.ACTION_MY_PACKAGE_REPLACED -> wasRunning || config.advanced.alwaysOn
                    else -> config.advanced.alwaysOn || (config.advanced.resumeOnBoot && wasRunning)
                }
                if (shouldStart && container.auth.hasToken()) {
                    container.settings.setRunning(true)
                    ContextCompat.startForegroundService(
                        context,
                        Intent(context, WatcherService::class.java).setAction(WatcherService.ACTION_START),
                    )
                } else if (action == Intent.ACTION_BOOT_COMPLETED || !container.auth.hasToken()) {
                    // Default is manual start: a reboot leaves the watcher off until Start is pressed.
                    // Without a stored session there is nothing to check anyway.
                    container.settings.setRunning(false)
                }
            } finally {
                pending.finish()
            }
        }
    }
}
