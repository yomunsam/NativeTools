package app.yomu.netbar.other

import android.Manifest
import android.annotation.SuppressLint
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.activityViewModels
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.SwitchPreferenceCompat
import app.yomu.netbar.R
import app.yomu.netbar.main.MainViewModel
import app.yomu.netbar.netspeed.service.NetSpeedNotificationHelper
import app.yomu.netbar.main.applyBottomBarsInsets
import app.yomu.netbar.ui.NightModeDropDownPreference
import app.yomu.netbar.util.*
import com.google.firebase.analytics.FirebaseAnalytics

class OtherFragment : PreferenceFragmentCompat() {

    private val activityResultLauncherCompat =
        ActivityResultLauncherCompat(this, ActivityResultContracts.StartActivityForResult())

    // TEMP_NOTIFY_PERM
    private val permissionLauncherCompat =
        ActivityResultLauncherCompat(this, ActivityResultContracts.RequestPermission())

    private val mainViewModel by activityViewModels<MainViewModel>()

    private lateinit var preferenceIgnoreBatteryOptimize: SwitchPreferenceCompat
    // TEMP_NOTIFY_PERM
    private lateinit var preferenceNotificationPermission: Preference

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        if (UI.isWideSize()) {
            applyBottomBarsInsets(listView)
        }
    }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        preferenceManager.preferenceDataStore = DataStorePreference(requireContext())
        addPreferencesFromResource(R.xml.preference_other)
        initOtherPreferenceGroup()
    }

    private fun initOtherPreferenceGroup() {
        requirePreference<Preference>(OtherPreferences.KEY_ABOUT).summary =
            requireContext().getVersionSummary()

        requirePreference<NightModeDropDownPreference>(OtherPreferences.KEY_NIGHT_MODE_TOGGLE).let {
            it.onPreferenceChangeListener<String> { _, mode ->
                val decorView = requireActivity().window.decorView
                mainViewModel.setCircularReveal(decorView, it.pressedPoint)

                setNightMode(mode.toInt())
                return@onPreferenceChangeListener true
            }
        }

        preferenceIgnoreBatteryOptimize =
            requirePreference<SwitchPreferenceCompat>(OtherPreferences.KEY_IGNORE_BATTERY_OPTIMIZE)
                .apply {
                    onPreferenceChangeListener<Boolean> { _, ignoreBatteryOptimization ->
                        if (ignoreBatteryOptimization) {
                            @SuppressLint("BatteryLife")
                            val intent =
                                Intent(
                                    Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                                    "package:${requireContext().packageName}"
                                )
                            activityResultLauncherCompat.launch(intent) { _ ->
                                checkIgnoreBatteryOptimize()
                            }
                        } else {
                            val intent =
                                Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                            startActivity(intent)
                            toast(getString(R.string.toast_open_battery_optimization))
                        }
                        return@onPreferenceChangeListener true
                    }
                }

        // TEMP_NOTIFY_PERM: discoverable notification permission / settings entry (near keepalive)
        preferenceNotificationPermission =
            requirePreference<Preference>(OtherPreferences.KEY_NOTIFICATION_PERMISSION).also {
                it.onPreferenceClickListener { onNotificationPermissionPreferenceClick() }
            }

        requirePreference<Preference>(OtherPreferences.KEY_FEEDBACK).onPreferenceClickListener {
            requireContext().browse(R.string.url_github_issues)
            event(FirebaseAnalytics.Event.SELECT_ITEM) {
                param(FirebaseAnalytics.Param.ITEM_NAME, "问题反馈")
            }
        }
        requirePreference<Preference>(OtherPreferences.KEY_RATE).onPreferenceClickListener {
            requireContext().market(requireContext().packageName)
            event(FirebaseAnalytics.Event.SELECT_ITEM) {
                param(FirebaseAnalytics.Param.ITEM_NAME, "去评分")
            }
        }
        requirePreference<Preference>(OtherPreferences.KEY_SHARE).onPreferenceClickListener {
            Logic.shareApp(requireContext())
        }
    }

    private fun checkIgnoreBatteryOptimize() {
        preferenceIgnoreBatteryOptimize.isChecked = requireContext().isIgnoringBatteryOptimizations
    }

    /** TEMP_NOTIFY_PERM */
    private fun onNotificationPermissionPreferenceClick() {
        val context = requireContext()
        if (Build.VERSION.SDK_INT >= 33 &&
            !checkPermissions(Manifest.permission.POST_NOTIFICATIONS)
        ) {
            permissionLauncherCompat.launch(Manifest.permission.POST_NOTIFICATIONS) { granted ->
                refreshNotificationPermissionPreference()
                if (!granted) {
                    NetSpeedNotificationHelper.goNotificationSetting(context)
                }
            }
            return
        }
        NetSpeedNotificationHelper.goNotificationSetting(context)
    }

    /** TEMP_NOTIFY_PERM */
    private fun refreshNotificationPermissionPreference() {
        if (!::preferenceNotificationPermission.isInitialized) return
        val ok = NetSpeedNotificationHelper.canPostNotifications(requireContext())
        preferenceNotificationPermission.summary =
            if (ok) {
                getString(R.string.summary_notification_permission_granted)
            } else {
                getString(R.string.summary_notification_permission_denied)
            }
    }

    override fun onStart() {
        super.onStart()
        checkIgnoreBatteryOptimize()
        refreshNotificationPermissionPreference()
    }
}
