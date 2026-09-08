@file:JvmName("GooglePlayKt")

package app.yomu.netbar.util

import android.content.Context
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability

fun Context.isGooglePlayServicesAvailable(): Boolean {
    return try {
        GoogleApiAvailability.getInstance()
            .isGooglePlayServicesAvailable(this) == ConnectionResult.SUCCESS
    } catch (_: Exception) {
        false
    }
}
