package app.yomu.netbar.netspeed.stats

import android.net.TrafficStats
import android.os.Build
import androidx.annotation.RequiresApi
import app.yomu.netbar.netspeed.stats.NetStats.Companion.isSupported

class Android31NetStats : NetStats {

    override fun supported(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return false
        return NetStats.rxBytesForWifiIfaces().isSupported
    }

    @RequiresApi(Build.VERSION_CODES.S)
    override fun getRxBytes(): Long {
        return NetStats.addIfSupported(
            TrafficStats.getMobileRxBytes(),
            NetStats.rxBytesForWifiIfaces()
        )
    }

    @RequiresApi(Build.VERSION_CODES.S)
    override fun getTxBytes(): Long {
        return NetStats.addIfSupported(
            TrafficStats.getMobileTxBytes(),
            NetStats.txBytesForWifiIfaces()
        )
    }
}
