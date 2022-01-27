package com.dede.nativetools.other

import android.annotation.SuppressLint
import android.os.Bundle
import android.provider.Settings
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.SwitchPreferenceCompat
import com.dede.nativetools.R
import com.dede.nativetools.main.MainViewModel
import com.dede.nativetools.main.applyBottomBarsInsets
import com.dede.nativetools.ui.NightModeDropDownPreference
import com.dede.nativetools.util.*

class OtherFragment : PreferenceFragmentCompat() {

    private val activityResultLauncherCompat =
        ActivityResultLauncherCompat(this, ActivityResultContracts.StartActivityForResult())

    private val mainViewModel by activityViewModels<MainViewModel>()

    private lateinit var preferenceIgnoreBatteryOptimize: SwitchPreferenceCompat

    private var changeNightModeRunnable: ChangeNightModeRunnable? = null

    private class ChangeNightModeRunnable(val mode: Int) : Runnable {
        override fun run() {
            setNightMode(mode)
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        if (UI.isWideSize()) {
            applyBottomBarsInsets(listView)
        }
    }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        addPreferencesFromResource(R.xml.other_preference)
        initOtherPreferenceGroup()
    }

    private fun initOtherPreferenceGroup() {
        requirePreference<Preference>(OtherPreferences.KEY_ABOUT).also {
            it.summary = requireContext().getVersionSummary()

            it.onPreferenceClickListener {
                findNavController().navigate(R.id.action_other_to_about)
            }
        }

        requirePreference<NightModeDropDownPreference>(OtherPreferences.KEY_NIGHT_MODE_TOGGLE).also {
            it.onNightModeSelected = { rect ->
                val decorView = requireActivity().window.decorView
                mainViewModel.setCircularReveal(decorView, rect)
            }
            it.onPreferenceChangeListener<String> { _, newValue ->
                removeDelay()
                val runnable = ChangeNightModeRunnable(newValue.toInt()).apply {
                    this@OtherFragment.changeNightModeRunnable = this
                }
                // Wait for Popup to dismiss
                uiHandler.postDelayed(runnable, 300)
            }
        }

        preferenceIgnoreBatteryOptimize =
            requirePreference<SwitchPreferenceCompat>(OtherPreferences.KEY_IGNORE_BATTERY_OPTIMIZE).apply {
                onPreferenceChangeListener<Boolean> { _, ignoreBatteryOptimization ->
                    if (ignoreBatteryOptimization) {
                        @SuppressLint("BatteryLife")
                        val intent = Intent(
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
                }
            }

        requirePreference<Preference>(OtherPreferences.KEY_FULL_NET_USAGE)
            .onPreferenceClickListener {
                findNavController().navigate(R.id.action_other_to_netUsageFragment)
            }

        requirePreference<Preference>(OtherPreferences.KEY_DONATE)
            .onPreferenceClickListener {
                findNavController().navigate(R.id.action_other_to_dialogDonate)
            }
        requirePreference<Preference>(OtherPreferences.KEY_RATE)
            .onPreferenceClickListener {
                requireContext().market(requireContext().packageName)
            }
        requirePreference<Preference>(OtherPreferences.KEY_BETA)
            .onPreferenceClickListener {
                requireContext().browse(R.string.url_pgyer)
            }
        requirePreference<Preference>(OtherPreferences.KEY_SHARE)
            .onPreferenceClickListener {
                requireContext().share(R.string.share_text)
            }
        requirePreference<Preference>(OtherPreferences.KEY_FEEDBACK)
            .onPreferenceClickListener {
                requireContext().emailTo(R.string.email)
            }
        requirePreference<Preference>(OtherPreferences.KEY_OPEN_SOURCE)
            .onPreferenceClickListener {
                findNavController().navigate(R.id.action_other_to_openSource)
            }
        requirePreference<Preference>(OtherPreferences.KEY_GITHUB)
            .onPreferenceClickListener {
                requireContext().browse(R.string.url_github)
            }
    }

    private fun checkIgnoreBatteryOptimize() {
        preferenceIgnoreBatteryOptimize.isChecked = requireContext().isIgnoringBatteryOptimizations
    }

    override fun onStart() {
        super.onStart()
        checkIgnoreBatteryOptimize()
    }

    private fun removeDelay() {
        val delayChangeNightMode = changeNightModeRunnable
        if (delayChangeNightMode != null) {
            uiHandler.removeCallbacks(delayChangeNightMode)
        }
    }

    override fun onDestroyView() {
        removeDelay()
        super.onDestroyView()
    }

}