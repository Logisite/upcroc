package app.upcroc.permissions

import android.Manifest
import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat

object Permissions {
    fun notificationsEnabled(context: Context): Boolean =
        NotificationManagerCompat.from(context).areNotificationsEnabled()

    fun notificationPermissionGranted(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < 33) return true
        return ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
    }

    fun exactAlarmsGranted(context: Context): Boolean =
        if (Build.VERSION.SDK_INT >= 31) {
            context.getSystemService(AlarmManager::class.java).canScheduleExactAlarms()
        } else {
            true
        }

    fun batteryExempt(context: Context): Boolean =
        context.getSystemService(PowerManager::class.java)
            .isIgnoringBatteryOptimizations(context.packageName)

    fun deviceIdle(context: Context): Boolean =
        if (Build.VERSION.SDK_INT >= 23) {
            context.getSystemService(PowerManager::class.java).isDeviceIdleMode
        } else {
            false
        }

    fun openNotificationSettings(context: Context) {
        runCatching {
            context.startActivity(
                Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                    .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
    }

    fun openExactAlarmSettings(context: Context) {
        if (Build.VERSION.SDK_INT < 31) return
        runCatching {
            context.startActivity(
                Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:${context.packageName}"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }.onFailure { openAppDetails(context) }
    }

    fun openBatterySettings(context: Context) {
        runCatching {
            context.startActivity(
                Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${context.packageName}"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }.onFailure { openAppDetails(context) }
    }

    fun openAppDetails(context: Context) {
        runCatching {
            context.startActivity(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
    }

    fun oemName(): String = Build.MANUFACTURER.replaceFirstChar { it.uppercase() }

    fun oemInstructions(): String {
        val maker = Build.MANUFACTURER.lowercase()
        return when {
            maker.contains("samsung") ->
                "Samsung: Settings → Battery → Background usage limits. Remove Upcroc from “Sleeping apps” and “Deep sleeping apps”, and set App battery usage to “Unrestricted”. Also turn off “Put unused apps to sleep” if you can."
            maker.contains("xiaomi") || maker.contains("redmi") || maker.contains("poco") ->
                "Xiaomi/Redmi: Settings → Apps → Manage apps → Upcroc. Enable Autostart, set Battery saver to “No restrictions”, and lock Upcroc in the recent-apps screen."
            maker.contains("oneplus") || maker.contains("oppo") || maker.contains("realme") ->
                "OnePlus/Oppo/Realme: Settings → Battery → App battery management → Upcroc → Allow background activity. Enable Auto-launch and disable “Optimize app”."
            maker.contains("huawei") || maker.contains("honor") ->
                "Huawei/Honor: Settings → Battery → App launch → Upcroc. Turn off “Manage automatically” and enable Auto-launch, Secondary launch, and Run in background."
            maker.contains("vivo") || maker.contains("iqoo") ->
                "Vivo/iQOO: Settings → Battery → Background power consumption management → Upcroc → Allow. Enable Autostart and high background power usage."
            maker.contains("tecno") || maker.contains("infinix") || maker.contains("itel") ->
                "TECNO/Infinix/itel (HiOS/XOS): Settings → Apps → Upcroc → Battery → Unrestricted. Open Phone Master / Power Marathon and allow Upcroc to auto-start, then lock Upcroc in the recent-apps screen so the cleaner does not remove it."
            maker.contains("google") || maker.contains("pixel") ->
                "Pixel: Settings → Apps → Upcroc → Battery → Unrestricted. Exact alarms and battery exemption are the main requirements."
            else ->
                "Open Android Settings → Apps → Upcroc → Battery and choose “Unrestricted”. If your phone has an Autostart or Startup manager, allow Upcroc there too."
        }
    }
}
