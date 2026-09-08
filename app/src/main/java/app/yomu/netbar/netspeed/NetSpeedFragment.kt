package app.yomu.netbar.netspeed

import android.Manifest
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.view.View
import android.widget.ImageView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.preference.*
import app.yomu.netbar.R
import app.yomu.netbar.main.applyBottomBarsInsets
import app.yomu.netbar.netspeed.service.NetSpeedNotificationHelper
import app.yomu.netbar.netspeed.service.NetSpeedService
import app.yomu.netbar.netspeed.service.NetSpeedServiceController
import app.yomu.netbar.other.OtherPreferences
import app.yomu.netbar.netspeed.utils.NetFormatter
import app.yomu.netbar.ui.CustomWidgetLayoutSwitchPreference
import app.yomu.netbar.util.*
import kotlinx.coroutines.flow.firstOrNull

/** 网速指示器设置页 */
class NetSpeedFragment :
    PreferenceFragmentCompat(),
    Preference.OnPreferenceChangeListener,
    Preference.SummaryProvider<EditTextPreference> {

    private val configuration = NetSpeedConfiguration()

    private val controller by later { NetSpeedServiceController(requireContext()) }

    private lateinit var usageSwitchPreference: SwitchPreferenceCompat
    private lateinit var statusSwitchPreference: SwitchPreferenceCompat
    private lateinit var thresholdEditTextPreference: EditTextPreference
    private lateinit var intervalPreference: DropDownPreference
    // TEMP_NOTIFY_PERM: discoverable notification permission entry
    private lateinit var notificationPermissionPreference: Preference

    private val activityResultLauncherCompat =
        ActivityResultLauncherCompat(this, ActivityResultContracts.StartActivityForResult())

    private val permissionLauncherCompat =
        ActivityResultLauncherCompat(this, ActivityResultContracts.RequestPermission())

    private val powerManager by later { requireContext().requireSystemService<PowerManager>() }
    private val broadcastHelper =
        BroadcastHelper(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED, NetSpeedService.ACTION_CLOSE)

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        preferenceManager.preferenceDataStore = DataStorePreference(requireContext())
        addPreferencesFromResource(R.xml.preference_net_speed)
        initGeneralPreferenceGroup()
        initNotificationPreferenceGroup()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        lifecycleScope.launchWhenCreated {
            val preferences = globalDataStore.data.firstOrNull() ?: return@launchWhenCreated
            configuration.updateFrom(preferences)

            val status = NetSpeedPreferences.status
            if (status) {
                // startService sets the switch after POST_NOTIFICATIONS is granted
                startService()
            } else {
                statusSwitchPreference.isChecked = false
            }
        }

        if (!Logic.checkAppOps(requireContext())) {
            usageSwitchPreference.isChecked = false
        }

        if (UI.isWideSize()) {
            applyBottomBarsInsets(listView)
        }

        broadcastHelper.register(requireContext()) { action, _ ->
            when (action) {
                PowerManager.ACTION_POWER_SAVE_MODE_CHANGED -> {
                    intervalPreference.isEnabled = !powerManager.isPowerSaveMode
                }
                NetSpeedService.ACTION_CLOSE -> {
                    statusSwitchPreference.isChecked = false
                }
            }
        }

        maybeShowSetupChecklist()
    }

    /** TEMP_SETUP: first-run checklist after privacy agreement */
    private fun maybeShowSetupChecklist() {
        if (!NetSpeedPreferences.privacyAgreed) return
        if (NetSpeedPreferences.setupChecklistDismissed) return
        // Avoid stacking over privacy dialog if still navigating
        if (findNavController().currentDestination?.id != R.id.netSpeed) return
        findNavController().navigate(R.id.action_netSpeed_to_dialogSetupChecklist)
    }

    private fun openSetupChecklist() {
        findNavController().navigate(R.id.action_netSpeed_to_dialogSetupChecklist)
    }

    private fun initGeneralPreferenceGroup() {
        statusSwitchPreference = requirePreference(NetSpeedPreferences.KEY_NET_SPEED_STATUS)
        statusSwitchPreference.onPreferenceChangeListener = this

        // TEMP_SETUP
        requirePreference<Preference>("setup_checklist").onPreferenceClickListener {
            openSetupChecklist()
        }

        thresholdEditTextPreference =
            requirePreference<EditTextPreference>(NetSpeedPreferences.KEY_NET_SPEED_HIDE_THRESHOLD)
                .also {
                    it.summaryProvider = this
                    it.onPreferenceChangeListener = this
                }

        bindPreferenceChangeListener(this, NetSpeedPreferences.KEY_NET_SPEED_MIN_UNIT)

        intervalPreference =
            requirePreference<DropDownPreference>(NetSpeedPreferences.KEY_NET_SPEED_INTERVAL).also {
                it.setSummaryProvider { _ ->
                    if (powerManager.isPowerSaveMode) {
                        getString(R.string.label_power_save_mode)
                    } else {
                        ListPreference.SimpleSummaryProvider.getInstance().provideSummary(it)
                    }
                }
                it.isEnabled = !powerManager.isPowerSaveMode
                it.onPreferenceChangeListener = this
            }
    }

    override fun provideSummary(preference: EditTextPreference): CharSequence {
        val bytes = preference.text?.toLongOrNull()
        return if (bytes != null) {
            if (bytes > 0) {
                val threshold =
                    NetFormatter.format(bytes, NetFormatter.FLAG_FULL, NetFormatter.ACCURACY_EXACT)
                        .splicing()
                getString(R.string.summary_net_speed_hide_threshold, threshold)
            } else {
                getString(R.string.summary_net_speed_unhide)
            }
        } else {
            getString(R.string.summary_threshold_error)
        }
    }

    private fun initNotificationPreferenceGroup() {
        // TEMP_NOTIFY_PERM: visible action to request / open notification settings
        notificationPermissionPreference =
            requirePreference<Preference>(OtherPreferences.KEY_NOTIFICATION_PERMISSION).also {
                it.onPreferenceClickListener {
                    onNotificationPermissionPreferenceClick()
                }
            }

        usageSwitchPreference =
            requirePreference<CustomWidgetLayoutSwitchPreference>(
                NetSpeedPreferences.KEY_NET_SPEED_USAGE
            )
                .also {
                    it.onPreferenceChangeListener = this
                    it.bindCustomWidget = { holder ->
                        val imageView = holder.findViewById(R.id.iv_preference_help) as ImageView
                        imageView.setImageResource(R.drawable.ic_round_settings)
                        imageView.setOnClickListener {
                            findNavController()
                                .navigate(R.id.action_netSpeed_to_netUsageConfigFragment)
                        }
                    }
                }

        requirePreference<CustomWidgetLayoutSwitchPreference>(
            NetSpeedPreferences.KEY_NET_SPEED_HIDE_LOCK_NOTIFICATION
        )
            .let {
                it.onPreferenceChangeListener = this
                it.bindCustomWidget = { holder ->
                    holder.findViewById(R.id.iv_preference_help)?.setOnClickListener {
                        requireContext().showHideLockNotificationDialog()
                    }
                }
            }
        bindPreferenceChangeListener(
            this,
            NetSpeedPreferences.KEY_NET_SPEED_NOTIFY_CLICKABLE,
            NetSpeedPreferences.KEY_NET_SPEED_QUICK_CLOSEABLE,
        )
    }

    private fun startService() {
        fun startServiceInternal(check: Boolean = true) {
            statusSwitchPreference.isChecked = true
            controller.startService(true)
            miuiNotificationAlert()
            if (check) {
                checkNotificationEnable()
            }
            refreshNotificationPermissionPreference()
            if (!NetSpeedPreferences.setupChecklistDismissed) {
                maybeShowSetupChecklist()
            }
        }

        // TEMP_NOTIFY_PERM: API 33+ must have POST_NOTIFICATIONS before FGS notification can show
        if (Build.VERSION.SDK_INT < 33 /* Build.VERSION_CODES.TIRAMISU */) {
            startServiceInternal()
            return
        }
        if (checkPermissions(Manifest.permission.POST_NOTIFICATIONS)) {
            startServiceInternal()
            return
        }
        permissionLauncherCompat.launch(Manifest.permission.POST_NOTIFICATIONS) { granted ->
            if (granted) {
                startServiceInternal()
            } else {
                statusSwitchPreference.isChecked = false
                // Permanently denied: system dialog will not show again — guide to settings
                val permanentlyDenied =
                    !shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS)
                if (permanentlyDenied) {
                    requireContext().showNotificationDisableDialog()
                } else {
                    toast(R.string.alert_msg_notification_disable)
                }
                refreshNotificationPermissionPreference()
            }
        }
    }

    /** TEMP_NOTIFY_PERM: preference click — request if possible, else open settings. */
    private fun onNotificationPermissionPreferenceClick() {
        val context = requireContext()
        if (Build.VERSION.SDK_INT >= 33 &&
            !checkPermissions(Manifest.permission.POST_NOTIFICATIONS)
        ) {
            permissionLauncherCompat.launch(Manifest.permission.POST_NOTIFICATIONS) { granted ->
                refreshNotificationPermissionPreference()
                if (!granted) {
                    NetSpeedNotificationHelper.goNotificationSetting(context)
                } else if (NetSpeedPreferences.status || statusSwitchPreference.isChecked) {
                    // Permission just granted while service was intended on
                    controller.startService(true)
                    miuiNotificationAlert()
                }
            }
            return
        }
        NetSpeedNotificationHelper.goNotificationSetting(context)
    }

    // TEMP_NOTIFY_PERM: update summary / visibility for the permission preference
    private fun refreshNotificationPermissionPreference() {
        if (!::notificationPermissionPreference.isInitialized) return
        val ok = NetSpeedNotificationHelper.canPostNotifications(requireContext())
        notificationPermissionPreference.summary =
            if (ok) {
                getString(R.string.summary_notification_permission_granted)
            } else {
                getString(R.string.summary_notification_permission_denied)
            }
    }

    override fun onPreferenceChange(preference: Preference, newValue: Any): Boolean {
        when (preference.key) {
            NetSpeedPreferences.KEY_NET_SPEED_STATUS -> {
                val status = newValue as Boolean
                if (status) {
                    // Defer switch persistence until permission is granted (see startService)
                    startService()
                    return false
                } else {
                    controller.stopService()
                }
            }
            NetSpeedPreferences.KEY_NET_SPEED_INTERVAL -> {
                configuration.interval = (newValue as String).toInt()
            }
            NetSpeedPreferences.KEY_NET_SPEED_HIDE_THRESHOLD -> {
                val strValue = (newValue as String)
                val hideThreshold = if (strValue.isEmpty()) 0 else strValue.toLongOrNull()
                if (hideThreshold == null) {
                    toast(R.string.summary_threshold_error)
                    return false
                }
                configuration.hideThreshold = hideThreshold

                val hideThresholdStr = hideThreshold.toString()
                if (hideThresholdStr != newValue) {
                    // Check input text, make sure it's all numbers.
                    // post set value, the value is saved only after method onPreferenceChange is
                    // called.
                    uiHandler.post { thresholdEditTextPreference.text = hideThresholdStr }
                }
            }
            NetSpeedPreferences.KEY_NET_SPEED_HIDE_LOCK_NOTIFICATION -> {
                configuration.hideLockNotification = newValue as Boolean
            }
            NetSpeedPreferences.KEY_NET_SPEED_USAGE -> {
                configuration.usage = newValue as Boolean
                checkOpsPermission()
            }
            NetSpeedPreferences.KEY_NET_SPEED_NOTIFY_CLICKABLE -> {
                configuration.notifyClickable = newValue as Boolean
            }
            NetSpeedPreferences.KEY_NET_SPEED_QUICK_CLOSEABLE -> {
                configuration.quickCloseable = newValue as Boolean
            }
            NetSpeedPreferences.KEY_NET_SPEED_MIN_UNIT -> {
                configuration.minUnit = (newValue as String).toInt()
            }
            else -> return true
        }
        controller.updateConfiguration(configuration)
        return true
    }

    override fun onStart() {
        super.onStart()
        refreshNotificationPermissionPreference()
        // TEMP_SETUP: after privacy dialog dismisses, surface checklist once
        maybeShowSetupChecklist()
    }

    override fun onDestroyView() {
        controller.unbindService()
        broadcastHelper.unregister(requireContext())
        super.onDestroyView()
    }

    private fun checkOpsPermission() {
        Logic.requestOpsPermission(
            requireContext(),
            activityResultLauncherCompat,
            { usageSwitchPreference.isChecked = true }
        ) { usageSwitchPreference.isChecked = false }
    }

    private fun checkNotificationEnable() {
        val context = requireContext()
        val areNotificationsEnabled = NetSpeedNotificationHelper.areNotificationEnabled(context)
        val dontAskNotify = NetSpeedPreferences.dontAskNotify
        if (dontAskNotify || areNotificationsEnabled) {
            return
        }
        context.showNotificationDisableDialog()
    }

    private fun miuiNotificationAlert() {
        if (!Logic.isXiaomi() || NetSpeedPreferences.miuiAlerted) {
            return
        }
        val context = requireContext()
        context.alert(android.R.string.dialog_alert_title, R.string.alert_msg_miui_notification) {
            positiveButton(R.string.settings) {
                val intent = android.content.Intent(Settings.ACTION_SETTINGS)
                intent.newTask().launchActivity(context)
                NetSpeedPreferences.miuiAlerted = true
            }
            neutralButton(R.string.i_know) { NetSpeedPreferences.miuiAlerted = true }
        }
    }
}
