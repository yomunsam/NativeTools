package com.dede.nativetools.ui

import android.content.Context
import android.graphics.Rect
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Spinner
import android.widget.TextView
import androidx.appcompat.widget.AppCompatSpinner
import androidx.appcompat.widget.ListPopupWindow
import androidx.preference.DropDownPreference
import androidx.preference.PreferenceViewHolder
import com.dede.nativetools.util.declaredField
import com.dede.nativetools.util.getRectOnFullWindow

abstract class FreestyleDropDownPreference(context: Context, attrs: AttributeSet?) :
    DropDownPreference(context, attrs) {

    abstract fun freestyle(position: Int, textView: TextView)

    override fun createAdapter(): ArrayAdapter<*> {
        return object : ArrayAdapter<Any>(context, android.R.layout.simple_spinner_dropdown_item) {

            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                val itemView = super.getView(position, convertView, parent)
                val textView = itemView.findViewById<TextView>(android.R.id.text1)
                setItemStyle(position, textView)
                return itemView
            }

            override fun getDropDownView(
                position: Int,
                convertView: View?,
                parent: ViewGroup,
            ): View {
                val itemView = super.getDropDownView(position, convertView, parent)
                val textView = itemView.findViewById<TextView>(android.R.id.text1)
                setItemStyle(position, textView)
                return itemView
            }
        }
    }

    private fun setItemStyle(position: Int, textView: TextView) {
        if (position < 0 || position >= entryValues.size) {
            return
        }
        freestyle(position, textView)
    }

    override fun onBindViewHolder(view: PreferenceViewHolder) {
        super.onBindViewHolder(view)
        val summaryTextView = view.findViewById(android.R.id.summary) as? TextView
        val spinner = view.findViewById(androidx.preference.R.id.spinner) as? Spinner
        if (summaryTextView != null && spinner != null) {
            setItemStyle(spinner.selectedItemPosition, summaryTextView)
        }
    }
}

/**
 * 自定义字体样式
 */
class TypefaceDropDownPreference(context: Context, attrs: AttributeSet?) :
    FreestyleDropDownPreference(context, attrs) {

    override fun freestyle(position: Int, textView: TextView) {
        val style = entryValues[position].toString().toIntOrNull() ?: Typeface.NORMAL
        textView.typeface = Typeface.create(Typeface.DEFAULT, style)
    }
}

class NightModeDropDownPreference(context: Context, attrs: AttributeSet?) :
    DropDownPreference(context, attrs) {

    private val selectedViewRect = Rect()

    var onNightModeSelected: ((selectedViewRect: Rect) -> Unit)? = null

    override fun onBindViewHolder(view: PreferenceViewHolder) {
        super.onBindViewHolder(view)
        val spinner =
            view.findViewById(androidx.preference.R.id.spinner) as? AppCompatSpinner ?: return
        spinner.runCatching {
            val fieldPopup = AppCompatSpinner::class.java.declaredField("mPopup")
            val listPopupWindow = fieldPopup.get(this) as ListPopupWindow
            val fieldItemClickListener =
                ListPopupWindow::class.java.declaredField("mItemClickListener")
            val oldOnItemClickListener =
                fieldItemClickListener.get(listPopupWindow) as AdapterView.OnItemClickListener
            listPopupWindow.setOnItemClickListener { parent, view, position, id ->
                view.getRectOnFullWindow(selectedViewRect)
                if (!selectedViewRect.isEmpty) {
                    onNightModeSelected?.invoke(Rect(selectedViewRect))
                }
                selectedViewRect.setEmpty()

                oldOnItemClickListener.onItemClick(parent, view, position, id)
            }
        }.onFailure(Throwable::printStackTrace)
    }
}
