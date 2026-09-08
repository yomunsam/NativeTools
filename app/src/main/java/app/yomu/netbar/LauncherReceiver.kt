package app.yomu.netbar

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import app.yomu.netbar.netspeed.NetSpeedPreferences
import app.yomu.netbar.netspeed.service.NetSpeedService

class LauncherReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        Log.i("LauncherReceiver", "onReceive: $action")

        when (action) {
            Intent.ACTION_BOOT_COMPLETED -> {
                if (NetSpeedPreferences.autoStart) {
                    launchIfAllowed(context)
                }
            }
            Intent.ACTION_MY_PACKAGE_REPLACED -> {
                launchIfAllowed(context)
            }
        }
    }

    private fun launchIfAllowed(context: Context) {
        if (!canPostNotifications(context)) {
            Log.w("LauncherReceiver", "skip startForegroundService: notifications not allowed")
            return
        }
        NetSpeedService.launchForeground(context)
    }

    private fun canPostNotifications(context: Context): Boolean {
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) {
            return false
        }
        if (Build.VERSION.SDK_INT >= 33) {
            return ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        }
        return true
    }
}
