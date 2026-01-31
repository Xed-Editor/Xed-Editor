package com.rk.utils

import android.util.Log
import com.rk.settings.debugOptions.LogCollector

fun Any.debug(msg: String) {
    Log.d(this::class.java.simpleName, msg)
    LogCollector.reportDebug(msg)
}

fun Any.info(msg: String) {
    Log.i(this::class.java.simpleName, msg)
    LogCollector.reportInfo(msg)
}

fun Any.logWarn(msg: String) {
    Log.w(this::class.java.simpleName, msg)
    LogCollector.reportWarn(msg)
}

fun Any.logError(msg: String) {
    Log.e(this::class.java.simpleName, msg)
    LogCollector.reportError(msg)
}
