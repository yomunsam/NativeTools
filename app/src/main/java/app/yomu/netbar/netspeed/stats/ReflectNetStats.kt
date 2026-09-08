package app.yomu.netbar.netspeed.stats

import android.net.TrafficStats
import app.yomu.netbar.netspeed.stats.NetStats.Companion.isSupported
import app.yomu.netbar.util.invokeWithReturn
import app.yomu.netbar.util.method
import java.lang.reflect.Method

class ReflectNetStats : NetStats {

    private var methodGetRxBytes: Method? = null
    private var methodGetTxBytes: Method? = null

    init {
        try {
            methodGetRxBytes = TrafficStats::class.java.method("getRxBytes", String::class.java)
            methodGetTxBytes = TrafficStats::class.java.method("getTxBytes", String::class.java)
        } catch (e: Exception) {}
    }

    override fun supported(): Boolean {
        return methodGetRxBytes != null &&
            methodGetTxBytes != null &&
            sumIfaceBytes(methodGetRxBytes).isSupported
    }

    override fun getRxBytes(): Long {
        return NetStats.addIfSupported(
            TrafficStats.getMobileRxBytes(),
            sumIfaceBytes(methodGetRxBytes)
        )
    }

    override fun getTxBytes(): Long {
        return NetStats.addIfSupported(
            TrafficStats.getMobileTxBytes(),
            sumIfaceBytes(methodGetTxBytes)
        )
    }

    private fun sumIfaceBytes(method: Method?): Long {
        if (method == null) return NetStats.UNSUPPORTED
        var total = 0L
        var any = false
        for (iface in NetStats.wifiIfaceCandidates()) {
            val v = getIFaceBytes(method, iface)
            if (v.isSupported) {
                total += v
                any = true
            }
        }
        return if (any) total else NetStats.UNSUPPORTED
    }

    private fun getIFaceBytes(method: Method?, iface: String): Long {
        if (method == null) return NetStats.UNSUPPORTED
        return try {
            method.invokeWithReturn(null, iface)
        } catch (e: Exception) {
            NetStats.UNSUPPORTED
        }
    }
}
