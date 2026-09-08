package app.yomu.netbar.netspeed.service

import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.os.IBinder
import androidx.core.content.ContextCompat
import app.yomu.netbar.netspeed.NetSpeedConfiguration
import app.yomu.netbar.netspeed.NetSpeedPreferences
import app.yomu.netbar.util.BroadcastHelper
import app.yomu.netbar.util.Intent

/** Same-process controller: binds to [NetSpeedService] via [NetSpeedService.LocalBinder]. */
class NetSpeedServiceController(context: Context) : ServiceConnection {

    private val appContext = context.applicationContext

    private var binder: NetSpeedService.LocalBinder? = null

    private val broadcastHelper = BroadcastHelper(NetSpeedService.ACTION_CLOSE)

    fun startService(bind: Boolean = false) {
        val intent = NetSpeedService.createIntent(appContext)
        ContextCompat.startForegroundService(appContext, intent)
        if (bind) {
            bindService()
        }
    }

    fun bindService() {
        val intent = NetSpeedService.createIntent(appContext)
        appContext.bindService(intent, this, Context.BIND_AUTO_CREATE)
        broadcastHelper.register(appContext) { _, _ -> unbindService() }
    }

    fun stopService() {
        val intent = Intent<NetSpeedService>(appContext)
        unbindService()
        appContext.stopService(intent)
    }

    fun unbindService() {
        broadcastHelper.unregister(appContext)
        if (binder == null) {
            return
        }
        appContext.unbindService(this)
        binder = null
    }

    override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
        binder = service as? NetSpeedService.LocalBinder
    }

    override fun onServiceDisconnected(name: ComponentName?) {
        binder = null
    }

    fun updateConfiguration(configuration: NetSpeedConfiguration) {
        val local = binder
        if (local != null) {
            local.updateConfiguration(configuration)
            return
        }
        // Not bound: only restart FGS when the indicator is meant to be on
        if (!NetSpeedPreferences.status) return
        val intent = NetSpeedService.createIntent(appContext)
        intent.putExtra(NetSpeedService.EXTRA_CONFIGURATION, configuration)
        ContextCompat.startForegroundService(appContext, intent)
    }
}
