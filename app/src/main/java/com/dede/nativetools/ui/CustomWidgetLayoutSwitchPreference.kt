package com.dede.nativetools.ui

import android.content.Context
import android.util.AttributeSet
import androidx.preference.PreferenceViewHolder
import androidx.preference.SwitchPreferenceCompat

/**
 * 自定义widgetLayout的SwitchPreference
 *
 * @author hsh
 * @since 2021/10/9 1:40 下午
 */
class CustomWidgetLayoutSwitchPreference : SwitchPreferenceCompat {

    var bindCustomWidget: ((holder: PreferenceViewHolder) -> Unit)? = null

    constructor(context: Context) : super(context)
    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs)
    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(
        context,
        attrs,
        defStyleAttr
    )

    override fun onBindViewHolder(holder: PreferenceViewHolder?) {
        super.onBindViewHolder(holder)
        bindCustomWidget?.invoke(holder ?: return)
    }
}