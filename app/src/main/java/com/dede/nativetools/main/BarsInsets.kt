package com.dede.nativetools.main

import android.view.View
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.recyclerview.widget.RecyclerView
import com.dede.nativetools.R
import com.dede.nativetools.ui.SafeDistance

typealias OnWindowInsetsListener = (insets: WindowInsetsCompat) -> Unit

fun WindowInsetsCompat.systemBar(): Insets =
    this.getInsets(WindowInsetsCompat.Type.systemBars())

fun View.onWindowInsetsApply(listener: OnWindowInsetsListener) {
    ViewCompat.setOnApplyWindowInsetsListener(this) { _, insets: WindowInsetsCompat ->
        listener.invoke(insets)
        return@setOnApplyWindowInsetsListener insets
    }
}

fun applyBottomBarsInsets(recyclerView: RecyclerView) {
    fun isAdded() = (recyclerView.getTag(R.id.tag_recycler_view) as? Boolean) ?: false
    if (isAdded()) return
    recyclerView.onWindowInsetsApply {
        if (isAdded()) return@onWindowInsetsApply

        val systemBar = it.systemBar()
        val itemDecoration = SafeDistance(bottom = systemBar.bottom)
        recyclerView.addItemDecoration(itemDecoration)
        recyclerView.setTag(R.id.tag_recycler_view, true)
        recyclerView.requestLayout()
    }
}

fun applyBarsInsets(
    root: View,
    left: View? = null,
    top: View? = null,
    right: View? = null,
    bottom: View? = null,
    listener: OnWindowInsetsListener? = null
) {
    if (left == null && top == null && right == null && bottom == null && listener == null) {
        return
    }
    root.onWindowInsetsApply { insets ->
        val systemBar = insets.systemBar()
        left?.updatePadding(left = systemBar.left)
        top?.updatePadding(top = systemBar.top)
        right?.updatePadding(right = systemBar.right)
        bottom?.updatePadding(bottom = systemBar.bottom)
        root.requestLayout()
        listener?.invoke(insets)
    }
}
