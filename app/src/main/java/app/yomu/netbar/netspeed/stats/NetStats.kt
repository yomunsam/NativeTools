package app.yomu.netbar.netspeed.stats

import android.net.TrafficStats
import android.util.Log
import java.net.NetworkInterface
import java.util.Collections
import com.google.firebase.perf.metrics.AddTrace

interface NetStats {

    companion object {

        const val UNSUPPORTED = TrafficStats.UNSUPPORTED.toLong()

        const val WLAN_IFACE = "wlan0"
        // const val MOBILE_IFACE = "rmnet_data0"

        val Long.isSupported: Boolean
            get() = this != UNSUPPORTED

        fun addIfSupported(vararg stats: Long): Long {
            var allStat: Long = 0
            for (stat in stats) {
                allStat += if (stat.isSupported) stat else 0
            }
            return allStat
        }


        /** Prefer live wlan* ifaces; fall back to common names when enumeration fails. */
        fun wifiIfaceCandidates(): List<String> {
            val found = linkedSetOf<String>()
            try {
                val en = NetworkInterface.getNetworkInterfaces()
                if (en != null) {
                    for (nif in Collections.list(en)) {
                        val name = nif.name ?: continue
                        if (name.startsWith("wlan")) {
                            found.add(name)
                        }
                    }
                }
            } catch (_: Exception) {
            }
            if (found.isEmpty()) {
                found.add(WLAN_IFACE)
                found.add("wlan1")
            }
            return found.toList()
        }

        fun rxBytesForWifiIfaces(): Long {
            var total = 0L
            var any = false
            for (iface in wifiIfaceCandidates()) {
                val v = TrafficStats.getRxBytes(iface)
                if (v.isSupported) {
                    total += v
                    any = true
                }
            }
            return if (any) total else UNSUPPORTED
        }

        fun txBytesForWifiIfaces(): Long {
            var total = 0L
            var any = false
            for (iface in wifiIfaceCandidates()) {
                val v = TrafficStats.getTxBytes(iface)
                if (v.isSupported) {
                    total += v
                    any = true
                }
            }
            return if (any) total else UNSUPPORTED
        }

        private var netStats: NetStats? = null

        @AddTrace(name = "创建NetStats")
        fun getInstance(): NetStats {
            if (netStats != null) {
                return netStats!!
            }
            val allNetBytesClass =
                arrayOf(
                    AndroidTPB3NetStats::class.java,
                    Android31NetStats::class.java,
                    ReflectNetStats::class.java,
                    ExcludeLoNetStats::class.java,
                    NormalNetStats::class.java
                )
            for (clazz in allNetBytesClass) {
                val instance = create(clazz)
                if (instance == null || !instance.supported()) continue

                netStats = instance
                break
            }
            Log.i("NetStats", "INetStats: $netStats")
            return checkNotNull(netStats) { "Not found supported INetStats" }
        }

        private fun create(clazz: Class<out NetStats>): NetStats? {
            return try {
                @Suppress("DEPRECATION")
                clazz.newInstance()
            } catch (e: Exception) {
                // Construction can fail on older API levels (e.g. Android31NetStats).
                // Return null so the caller continues to the next implementation.
                e.printStackTrace()
                null
            }
        }
    }

    /** NetStats是否支持 */
    fun supported(): Boolean

    /** 下载的字节 */
    fun getRxBytes(): Long

    /** 长传的字节 */
    fun getTxBytes(): Long

    val name: String
        get() = this.javaClass.simpleName
}
