package app.yomu.netbar.netusage

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.View
import androidx.lifecycle.lifecycleScope
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.SwitchPreferenceCompat
import app.yomu.netbar.R
import app.yomu.netbar.netspeed.NetSpeedConfiguration
import app.yomu.netbar.netspeed.NetSpeedPreferences
import app.yomu.netbar.netspeed.service.NetSpeedServiceController
import app.yomu.netbar.util.*
import kotlinx.coroutines.flow.firstOrNull

/**
 * 网络使用情况配置（Wi‑Fi / 移动开关）。
 *
 * TEMP_OEM_IMSI: per-SIM IMSI hand-entry UI removed. Device-wide mobile buckets via
 * NetworkStatsManager (subscriberId=null) cover normal dual-SIM cases without IMSI.
 */
class NetUsageConfigFragment : PreferenceFragmentCompat() {

    private val controller by later { NetSpeedServiceController(requireContext()) }
    private val configuration = NetSpeedConfiguration()

    private lateinit var usageAccessPreference: Preference

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        preferenceManager.preferenceDataStore = DataStorePreference(requireContext())
        addPreferencesFromResource(R.xml.preference_net_usage_config)

        requirePreference<SwitchPreferenceCompat>(NetUsageConfigs.KEY_NET_USAGE_WIFI)
            .onPreferenceChangeListener<Boolean> { _, newValue ->
                configuration.enableWifiUsage = newValue
                controller.updateConfiguration(configuration)
                true
            }
        requirePreference<SwitchPreferenceCompat>(NetUsageConfigs.KEY_NET_USAGE_MOBILE)
            .onPreferenceChangeListener<Boolean> { _, newValue ->
                configuration.enableMobileUsage = newValue
                controller.updateConfiguration(configuration)
                true
            }

        usageAccessPreference =
            requirePreference<Preference>(NetUsageConfigs.KEY_USAGE_ACCESS).also {
                it.onPreferenceClickListener { openUsageAccessSettings() }
            }

        if (NetSpeedPreferences.status) {
            controller.bindService()
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        lifecycleScope.launchWhenCreated {
            val preferences = globalDataStore.data.firstOrNull() ?: return@launchWhenCreated
            configuration.updateFrom(preferences)
            // TEMP_OEM_IMSI: do not push legacy IMSI set into the service config.
            configuration.updateImsi(null)
            refreshUsageAccessSummary()
        }
    }

    override fun onStart() {
        super.onStart()
        refreshUsageAccessSummary()
    }

    override fun onDestroyView() {
        controller.unbindService()
        super.onDestroyView()
    }

    private fun refreshUsageAccessSummary() {
        if (!::usageAccessPreference.isInitialized) return
        val granted = Logic.checkAppOps(requireContext())
        usageAccessPreference.summary =
            if (granted) {
                getString(R.string.summary_usage_access_granted)
            } else {
                getString(R.string.summary_usage_access_denied)
            }
    }

    private fun openUsageAccessSettings() {
        val context = requireContext()
        val intent =
            Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS, "package:${context.packageName}")
        if (!intent.queryImplicitActivity(context)) {
            intent.data = null
        }
        intent.launchActivity(context)
    }
}
