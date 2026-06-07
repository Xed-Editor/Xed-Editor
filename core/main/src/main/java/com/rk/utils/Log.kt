package com.rk.utils

import android.util.Log
import com.rk.resources.getString
import com.rk.resources.strings
import com.rk.settings.debugOptions.LogCollector

fun Any.logDebug(msg: String) {
    Log.d(this::class.java.simpleName, msg)
    LogCollector.reportDebug(msg)
}

fun Any.logInfo(msg: String) {
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

fun Any.logError(throwable: Throwable, msg: String = strings.unknown_error.getString()) {
    Log.e(this::class.java.simpleName, msg, throwable)
    LogCollector.reportError("$msg: \n${throwable.stackTraceToString()}")
}
