package app.yomu.netbar.netusage.utils

import android.app.usage.NetworkStats
import android.app.usage.NetworkStatsManager
import android.content.Context
import androidx.annotation.WorkerThread
import app.yomu.netbar.util.Logic
import app.yomu.netbar.util.requireSystemService
import app.yomu.netbar.util.toZeroH
import java.lang.ref.WeakReference
import java.util.*
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.*

/**
 * 网络使用状态工具 — NetworkStatsManager device buckets.
 *
 * Public NetworkStatsManager still keys buckets by legacy network-type ints
 * (historically ConnectivityManager.TYPE_MOBILE=0 / TYPE_WIFI=1). We keep local
 * constants with those values so we do not call the deprecated ConnectivityManager
 * fields. NetworkTemplate is @SystemApi / not in the public SDK, so querySummaryForDevice
 * with a null subscriberId is the supported path for aggregated Wi‑Fi / mobile usage
 * without IMSI hand-entry.
 *
 * Requires PACKAGE_USAGE_STATS (usage access). Missing permission → 0 / diagnosis -1.
 */
object NetUsageUtils {

    /**
     * NetworkStatsManager network type for Wi‑Fi.
     * Value matches historical ConnectivityManager.TYPE_WIFI (1).
     */
    const val TYPE_WIFI = 1

    /**
     * NetworkStatsManager network type for mobile (cellular).
     * Value matches historical ConnectivityManager.TYPE_MOBILE (0).
     */
    const val TYPE_MOBILE = 0

    const val RANGE_TYPE_TODAY = 0
    const val RANGE_TYPE_MONTH = 1

    private class NetworkUsageJob<T : Comparable<T>>(data: T) : CompletionHandler {
        private val dataRef = AtomicReference<T>(data)
        val data: T
            get() = dataRef.get()

        private var job: Job? = null

        private val coroutineScope = CoroutineScope(Dispatchers.IO)

        fun execute(block: suspend CoroutineScope.() -> T) {
            var job = this.job
            if (job != null && !job.isCompleted && !job.isCancelled) {
                return
            }
            job = coroutineScope.launch { dataRef.set(block()) }
            job.invokeOnCompletion(this)
            this.job = job
        }

        override fun invoke(cause: Throwable?) {
            cause?.printStackTrace()
        }
    }

    private data class JobKey(val type: Int, val rangeType: Int)

    private val jobsMap = HashMap<JobKey, NetworkUsageJob<Long>>()

    private fun getJob(type: Int, rangeType: Int): NetworkUsageJob<Long> {
        val key = JobKey(type, rangeType)
        return jobsMap.getOrPut(key) { NetworkUsageJob(0) }
    }

    /**
     * 快速获取网络流量使用情况，可能数据不是最新的。
     *
     * Mobile / Wi‑Fi buckets use a null subscriberId (device-wide aggregate).
     * No IMSI is required. Returns 0 when usage access is missing.
     *
     * @param context 上下文
     * @param type [TYPE_WIFI] or [TYPE_MOBILE]
     * @param rangeType [RANGE_TYPE_TODAY] or [RANGE_TYPE_MONTH]
     */
    fun getNetUsageBytes(context: Context, type: Int, rangeType: Int): Long {
        if (!Logic.checkAppOps(context)) {
            return 0L
        }
        val weakRefContext = WeakReference(context)
        val job = getJob(type, rangeType)
        job.execute {
            val ctx = weakRefContext.get() ?: return@execute 0
            if (!Logic.checkAppOps(ctx)) {
                return@execute 0
            }
            val start = Calendar.getInstance().toZeroH()
            if (rangeType == RANGE_TYPE_MONTH) {
                start.set(Calendar.DAY_OF_MONTH, 1)
            }
            getNetUsageBytesInternal(ctx, type, start)
        }
        return job.data
    }

    @WorkerThread
    fun networkUsageDiagnosis(context: Context): Long {
        if (!Logic.checkAppOps(context)) {
            return -1
        }
        val start = Calendar.getInstance().toZeroH()
        start.set(Calendar.DAY_OF_MONTH, 1)
        return runBlocking {
            val wifiUsage = getNetUsageBytesInternal(context, TYPE_WIFI, start)
            val mobileUsage = getNetUsageBytesInternal(context, TYPE_MOBILE, start)
            wifiUsage + mobileUsage
        }
    }

    private suspend fun getNetUsageBytesInternal(
        context: Context,
        type: Int,
        start: Calendar,
    ): Long {
        val networkStatsManager = context.requireSystemService<NetworkStatsManager>()
        val startTime = start.timeInMillis
        val endTime = System.currentTimeMillis()
        val usageBytes =
            withContext(Dispatchers.IO) {
                networkStatsManager.queryNetUsageBytes(type, startTime, endTime)
            }
        return usageBytes shr 12 shl 12 // tolerance 4096
    }

    @WorkerThread
    private fun NetworkStatsManager.queryNetUsageBytes(
        networkType: Int,
        startTime: Long,
        endTime: Long,
    ): Long {
        val bucket = this.queryNetUsageBucket(networkType, startTime, endTime) ?: return 0L
        return bucket.rxBytes + bucket.txBytes
    }

    @WorkerThread
    fun NetworkStatsManager.queryNetUsageBucket(
        networkType: Int,
        startTime: Long,
        endTime: Long,
    ): NetworkStats.Bucket? {
        // subscriberId = null → combined / device-wide stats (no IMSI).
        return this.runCatching { querySummaryForDevice(networkType, null, startTime, endTime) }
            .getOrNull()
    }
}
