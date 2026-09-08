package app.yomu.netbar.netspeed.service

import android.app.PendingIntent
import android.graphics.drawable.Icon
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import androidx.annotation.RequiresApi
import app.yomu.netbar.R
import app.yomu.netbar.main.MainActivity
import app.yomu.netbar.netspeed.NetSpeedConfiguration
import app.yomu.netbar.netspeed.NetSpeedPreferences
import app.yomu.netbar.netspeed.utils.NetFormatter
import app.yomu.netbar.netspeed.utils.NetSpeedCompute
import app.yomu.netbar.netspeed.utils.NetTextIconFactory
import app.yomu.netbar.util.Intent
import app.yomu.netbar.util.globalDataStore
import app.yomu.netbar.util.newTask
import app.yomu.netbar.util.splicing
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch

@RequiresApi(Build.VERSION_CODES.N)
class NetTileService : TileService() {

    private val configuration = NetSpeedConfiguration()

    private val netSpeedCompute = NetSpeedCompute { rxSpeed, txSpeed -> update(rxSpeed, txSpeed) }
    private val lifecycleJob = Job()
    private val coroutineScope = CoroutineScope(Dispatchers.IO + lifecycleJob)

    override fun onStartListening() {
        coroutineScope.launch {
            val preferences = globalDataStore.data.firstOrNull() ?: return@launch
            configuration.updateFrom(preferences)
        }
        refreshTileState()
        if (NetSpeedPreferences.status) {
            netSpeedCompute.start()
        } else {
            netSpeedCompute.stop()
            updateInactiveTile()
        }
    }

    override fun onStopListening() {
        netSpeedCompute.stop()
    }

    /** Open MainActivity and collapse QS; PendingIntent overload on API 34+. */
    private fun openMainAndCollapse() {
        val intent = Intent<MainActivity>(baseContext).newTask()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            val pending = PendingIntent.getActivity(this, 0, intent, flags)
            startActivityAndCollapse(pending)
        } else {
            @Suppress("DEPRECATION")
            startActivityAndCollapse(intent)
        }
    }

    /** Tap toggles the indicator; long-press QS tile preferences still open the app. */
    override fun onClick() {
        val action = Runnable {
            try {
                NetSpeedService.toggle(applicationContext)
                refreshTileState()
                if (NetSpeedPreferences.status) {
                    netSpeedCompute.start()
                } else {
                    netSpeedCompute.stop()
                    updateInactiveTile()
                }
            } catch (_: Throwable) {
                openMainAndCollapse()
            }
        }
        if (isLocked) {
            unlockAndRun(action)
        } else {
            action.run()
        }
    }

    private fun refreshTileState() {
        val qsTile = qsTile ?: return
        qsTile.state =
            if (NetSpeedPreferences.status) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            qsTile.subtitle =
                if (NetSpeedPreferences.status) {
                    getString(R.string.label_net_speed)
                } else {
                    getString(R.string.switch_off)
                }
        }
        if (!NetSpeedPreferences.status) {
            qsTile.label = getString(R.string.label_net_speed_service)
            qsTile.icon = Icon.createWithResource(this, R.drawable.ic_launcher_icon)
        }
        qsTile.updateTile()
    }

    private fun updateInactiveTile() {
        val qsTile = qsTile ?: return
        qsTile.state = Tile.STATE_INACTIVE
        qsTile.label = getString(R.string.label_net_speed_service)
        qsTile.icon = Icon.createWithResource(this, R.drawable.ic_launcher_icon)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            qsTile.subtitle = getString(R.string.switch_off)
        }
        qsTile.updateTile()
    }

    private fun update(rxSpeed: Long, txSpeed: Long) {
        if (!NetSpeedPreferences.status) {
            updateInactiveTile()
            return
        }
        val qsTile = qsTile ?: return
        val downloadSpeedStr =
            NetFormatter.format(rxSpeed, NetFormatter.FLAG_FULL, NetFormatter.ACCURACY_EXACT)
                .splicing()
        val uploadSpeedStr =
            NetFormatter.format(txSpeed, NetFormatter.FLAG_FULL, NetFormatter.ACCURACY_EXACT)
                .splicing()

        qsTile
            .apply {
                state = Tile.STATE_ACTIVE
                val bitmap = NetTextIconFactory.create(rxSpeed, txSpeed, configuration)
                icon = Icon.createWithBitmap(bitmap)
                label = getString(R.string.tile_net_speed_label, uploadSpeedStr, downloadSpeedStr)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    subtitle = getString(R.string.label_net_speed)
                }
            }
            .updateTile()
    }

    override fun onDestroy() {
        lifecycleJob.cancel()
        netSpeedCompute.destroy()
        super.onDestroy()
    }
}
