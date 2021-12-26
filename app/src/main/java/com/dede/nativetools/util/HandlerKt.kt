@file:JvmName("HandlerKt")

package com.dede.nativetools.util

import android.os.Build
import android.os.Handler
import android.os.Looper

val uiHandler by lazy { Handler(Looper.getMainLooper()) }

fun Handler.singlePost(r: Runnable, delayMillis: Long = 0) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        if (this.hasCallbacks(r)) {
            this.removeCallbacks(r)
        }
    } else {
        this.removeCallbacks(r)
    }
    this.postDelayed(r, delayMillis)
}