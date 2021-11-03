package com.dede.nativetools.netspeed

import android.graphics.drawable.Icon
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import androidx.annotation.RequiresApi
import com.dede.nativetools.R
import com.dede.nativetools.netspeed.utils.NetFormatter
import com.dede.nativetools.ui.MainActivity
import com.dede.nativetools.util.Intent
import com.dede.nativetools.util.newTask
import com.dede.nativetools.util.splicing

@RequiresApi(Build.VERSION_CODES.N)
class NetTileService : TileService() {

    private val configuration = NetSpeedConfiguration.initialize()

    private val netSpeedCompute = NetSpeedCompute { rxSpeed, txSpeed ->
        update(rxSpeed, txSpeed)
    }

    override fun onStartListening() {
        // 获取最新的配置
        configuration.reinitialize().also {
            netSpeedCompute.interval = it.interval
        }
        netSpeedCompute.start()
    }

    override fun onStopListening() {
        netSpeedCompute.stop()
    }

    private fun startMain() {
        val intent = Intent<MainActivity>(baseContext)
            .newTask()
        startActivityAndCollapse(intent)
    }

    override fun onClick() {
        if (isLocked) {
            unlockAndRun { startMain() }
        } else {
            startMain()
        }
    }

    private fun update(rxSpeed: Long, txSpeed: Long) {
        val downloadSpeedStr =
            NetFormatter.formatBytes(rxSpeed, NetFormatter.FLAG_FULL, NetFormatter.ACCURACY_EXACT)
                .splicing()
        val uploadSpeedStr =
            NetFormatter.formatBytes(txSpeed, NetFormatter.FLAG_FULL, NetFormatter.ACCURACY_EXACT)
                .splicing()

        qsTile.apply {
            state = Tile.STATE_ACTIVE
            val bitmap = NetTextIconFactory.createIconBitmap(
                rxSpeed,
                txSpeed,
                configuration
            )
            configuration.cachedBitmap = bitmap
            icon = Icon.createWithBitmap(bitmap)
            label = getString(R.string.tile_net_speed_label, downloadSpeedStr, uploadSpeedStr)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                subtitle = getString(R.string.label_net_speed)
            }
        }.updateTile()
    }

    override fun onDestroy() {
        netSpeedCompute.stop()
        super.onDestroy()
    }
}