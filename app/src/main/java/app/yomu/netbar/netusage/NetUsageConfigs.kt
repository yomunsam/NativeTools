package app.yomu.netbar.netusage

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.google.firebase.crashlytics.ktx.crashlytics
import com.google.firebase.ktx.Firebase

/**
 * Preference keys for net-usage toggles, plus legacy IMSI storage.
 *
 * TEMP_OEM_IMSI: IMSI list APIs remain for encrypted prefs already written on device /
 * rare OEM debugging. The live query path no longer needs subscriberId / IMSI hand-entry.
 */
class NetUsageConfigs(context: Context) {

    companion object {
        private const val PREF_NAME = "sim_card_config"

        // TEMP_OEM_IMSI: keys kept so old prefs / XML references do not break if re-enabled.
        const val KEY_ADD_IMSI_CONFIG = "key_add_imsi_config"
        const val KEY_IMSI_CONFIG_GROUP = "key_imsi_config_group"
        const val KEY_NET_USAGE_WIFI = "key_net_usage_wifi"
        const val KEY_NET_USAGE_MOBILE = "key_net_usage_mobile"
        const val KEY_USAGE_ACCESS = "key_usage_access"

        private const val KEY_ENABLED_IMSI = "key_enable_imsi"
        private const val KEY_ALL_IMSI = "key_all_imsi"
    }

    private val sharedPreferences: SharedPreferences =
        try {
            EncryptedSharedPreferences(
                context,
                PREF_NAME,
                MasterKey(context),
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        } catch (e: Throwable) {
            Firebase.crashlytics.recordException(e)
            context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        }

    // TEMP_OEM_IMSI: retained storage only; UI and NetworkStats queries do not use these.
    private val allIMSI: LinkedHashSet<String> = LinkedHashSet()
    private val enabledIMSI: LinkedHashSet<String> = LinkedHashSet()

    init {
        sharedPreferences.getStringSet(KEY_ALL_IMSI, null)?.let { allIMSI.addAll(it) }
        sharedPreferences.getStringSet(KEY_ENABLED_IMSI, null)?.let { enabledIMSI.addAll(it) }
    }

    /** TEMP_OEM_IMSI */
    fun getEnabledIMSI(): Set<String> {
        return LinkedHashSet(enabledIMSI)
    }

    /** TEMP_OEM_IMSI */
    fun getAllIMSI(): Set<String> {
        return LinkedHashSet(allIMSI)
    }

    /** TEMP_OEM_IMSI */
    fun deleteIMSI(imsi: String) {
        allIMSI.remove(imsi)
        enabledIMSI.remove(imsi)
        save()
    }

    /** TEMP_OEM_IMSI */
    fun addIMSI(imsi: String): Boolean {
        val r = allIMSI.add(imsi)
        save()
        return r
    }

    /** TEMP_OEM_IMSI */
    fun setIMSIEnabled(imsi: String, enabled: Boolean) {
        allIMSI.add(imsi)
        if (enabled) {
            enabledIMSI.add(imsi)
        } else {
            enabledIMSI.remove(imsi)
        }
        save()
    }

    /** TEMP_OEM_IMSI */
    fun isEnabled(imsi: String): Boolean {
        return enabledIMSI.contains(imsi)
    }

    private fun save() {
        sharedPreferences
            .edit()
            .putStringSet(KEY_ALL_IMSI, allIMSI)
            .putStringSet(KEY_ENABLED_IMSI, enabledIMSI)
            .apply()
    }
}
