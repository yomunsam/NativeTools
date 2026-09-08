package app.yomu.netbar.main

import android.Manifest
import android.annotation.SuppressLint
import android.app.Dialog
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.LayoutInflater
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.DialogFragment
import app.yomu.netbar.R
import app.yomu.netbar.netspeed.NetSpeedPreferences
import app.yomu.netbar.netspeed.service.NetSpeedNotificationHelper
import app.yomu.netbar.util.*
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder

/**
 * TEMP_SETUP: lightweight first-launch / first-enable checklist.
 * 3 steps — notification permission, battery optimization ignore, optional usage access.
 */
class SetupChecklistDialogFragment : DialogFragment() {

    private val activityResultLauncherCompat =
        ActivityResultLauncherCompat(this, ActivityResultContracts.StartActivityForResult())

    private val permissionLauncherCompat =
        ActivityResultLauncherCompat(this, ActivityResultContracts.RequestPermission())

    private var tvNotificationStatus: TextView? = null
    private var tvBatteryStatus: TextView? = null
    private var tvUsageStatus: TextView? = null

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val view =
            LayoutInflater.from(requireContext()).inflate(R.layout.dialog_setup_checklist, null)

        tvNotificationStatus = view.findViewById(R.id.tv_setup_notification_status)
        tvBatteryStatus = view.findViewById(R.id.tv_setup_battery_status)
        tvUsageStatus = view.findViewById(R.id.tv_setup_usage_status)

        view.findViewById<MaterialButton>(R.id.btn_setup_notification).setOnClickListener {
            requestNotificationPermission()
        }
        view.findViewById<MaterialButton>(R.id.btn_setup_battery).setOnClickListener {
            requestBatteryOptimization()
        }
        view.findViewById<MaterialButton>(R.id.btn_setup_usage).setOnClickListener {
            requestUsageAccess()
        }
        view.findViewById<MaterialButton>(R.id.btn_setup_later).setOnClickListener {
            NetSpeedPreferences.setupChecklistDismissed = true
            dismissAllowingStateLoss()
        }
        view.findViewById<MaterialButton>(R.id.btn_setup_done).setOnClickListener {
            NetSpeedPreferences.setupChecklistDismissed = true
            dismissAllowingStateLoss()
        }

        refreshStatuses()

        return MaterialAlertDialogBuilder(requireContext()).setView(view).create()
    }

    override fun onResume() {
        super.onResume()
        refreshStatuses()
    }

    private fun refreshStatuses() {
        val context = context ?: return
        val notifyOk = NetSpeedNotificationHelper.canPostNotifications(context)
        tvNotificationStatus?.text =
            getString(
                if (notifyOk) R.string.setup_status_done else R.string.setup_status_todo
            )

        val batteryOk = context.isIgnoringBatteryOptimizations
        tvBatteryStatus?.text =
            getString(
                if (batteryOk) R.string.setup_status_done else R.string.setup_status_todo
            )

        val usageOk = Logic.checkAppOps(context)
        tvUsageStatus?.text =
            getString(
                if (usageOk) R.string.setup_status_done
                else R.string.setup_status_optional_todo
            )
    }

    private fun requestNotificationPermission() {
        val context = requireContext()
        if (Build.VERSION.SDK_INT >= 33 &&
            !checkPermissions(Manifest.permission.POST_NOTIFICATIONS)
        ) {
            permissionLauncherCompat.launch(Manifest.permission.POST_NOTIFICATIONS) { granted ->
                refreshStatuses()
                if (!granted) {
                    NetSpeedNotificationHelper.goNotificationSetting(context)
                }
            }
            return
        }
        NetSpeedNotificationHelper.goNotificationSetting(context)
    }

    private fun requestBatteryOptimization() {
        val context = requireContext()
        if (context.isIgnoringBatteryOptimizations) {
            refreshStatuses()
            return
        }
        @SuppressLint("BatteryLife")
        val intent =
            Intent(
                Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                "package:${context.packageName}"
            )
        activityResultLauncherCompat.launch(intent) { refreshStatuses() }
    }

    private fun requestUsageAccess() {
        val context = requireContext()
        Logic.requestOpsPermission(
            context,
            activityResultLauncherCompat,
            granted = { refreshStatuses() },
            denied = { refreshStatuses() },
        )
    }

    companion object {
        const val TAG = "SetupChecklist"
    }
}
