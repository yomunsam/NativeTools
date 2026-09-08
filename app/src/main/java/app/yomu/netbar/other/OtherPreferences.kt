package app.yomu.netbar.other

import androidx.appcompat.app.AppCompatDelegate
import androidx.datastore.preferences.core.stringPreferencesKey
import app.yomu.netbar.util.get
import app.yomu.netbar.util.globalDataStore

object OtherPreferences {

    const val KEY_NIGHT_MODE_TOGGLE = "night_mode_toggle"
    const val KEY_IGNORE_BATTERY_OPTIMIZE = "ignore_battery_optimize"
    // TEMP_NOTIFY_PERM: notification permission / settings preference key
    const val KEY_NOTIFICATION_PERMISSION = "notification_permission"

    const val KEY_ABOUT = "about"
    const val KEY_FEEDBACK = "feedback"
    const val KEY_RATE = "rate"
    const val KEY_SHARE = "share"

    private const val DEFAULT_NIGHT_MODE = AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM

    val nightMode: Int
        get() =
            globalDataStore
                .get(stringPreferencesKey(KEY_NIGHT_MODE_TOGGLE), DEFAULT_NIGHT_MODE.toString())
                .toIntOrNull()
                ?: DEFAULT_NIGHT_MODE
}
