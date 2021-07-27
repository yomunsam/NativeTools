package com.dede.nativetools.util

import android.content.Context
import android.content.DialogInterface
import android.util.DisplayMetrics
import android.util.TypedValue
import androidx.annotation.StringRes
import androidx.appcompat.app.AlertDialog
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import com.dede.nativetools.NativeToolsApp
import kotlin.math.roundToInt


private inline fun displayMetrics(): DisplayMetrics {
    return NativeToolsApp.getInstance().resources.displayMetrics
}

val Number.dp: Int
    get() = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP,
        this.toFloat(),
        displayMetrics()
    ).roundToInt()

val Number.dpf: Float
    get() = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP,
        this.toFloat(),
        displayMetrics()
    )

fun <T : Preference> PreferenceFragmentCompat.requirePreference(key: CharSequence): T {
    return findPreference(key) as? T
        ?: throw NullPointerException("Preference not found, key: $key")
}

private typealias DialogOnClick = (dialog: DialogInterface) -> Unit

class AlertBuilder(private val builder: AlertDialog.Builder) {

    var isCancelable: Boolean = false
        set(value) {
            builder.setCancelable(value)
        }

    fun positiveButton(@StringRes textId: Int, onClick: DialogOnClick? = null) {
        builder.setPositiveButton(textId) { dialog, _ ->
            onClick?.invoke(dialog)
        }
    }

    fun neutralButton(@StringRes textId: Int, onClick: DialogOnClick? = null) {
        builder.setNeutralButton(textId) { dialog, _ ->
            onClick?.invoke(dialog)
        }
    }

    fun negativeButton(@StringRes textId: Int, onClick: DialogOnClick? = null) {
        builder.setNegativeButton(textId) { dialog, _ ->
            onClick?.invoke(dialog)
        }
    }

}

fun Context.alert(
    @StringRes titleId: Int,
    @StringRes messageId: Int,
    init: (AlertBuilder.() -> Unit)? = null
) {
    val builder = AlertDialog.Builder(this)
        .setTitle(titleId)
        .setMessage(messageId)
    init?.invoke(AlertBuilder(builder))
    builder.show()
}