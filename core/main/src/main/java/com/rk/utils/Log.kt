package com.rk.utils

import android.util.Log
import com.rk.settings.debugOptions.LogCollector

private const val TAG = "Xed-Editor"

fun Any.logDebug(msg: String) {
    Log.d(this::class.java.simpleName, msg)
    LogCollector.reportDebug(msg)
}

fun logDebug(msg: String) {
    Log.d(TAG, msg)
    LogCollector.reportDebug(msg)
}

fun Any.logInfo(msg: String) {
    Log.i(this::class.java.simpleName, msg)
    LogCollector.reportInfo(msg)
}

fun logInfo(msg: String) {
    Log.i(TAG, msg)
    LogCollector.reportInfo(msg)
}

fun Any.logWarn(msg: String) {
    Log.w(this::class.java.simpleName, msg)
    LogCollector.reportWarn(msg)
}

fun logWarn(msg: String) {
    Log.w(TAG, msg)
    LogCollector.reportWarn(msg)
}

fun Any.logError(msg: String) {
    Log.e(this::class.java.simpleName, msg)
    LogCollector.reportError(msg)
}

fun logError(msg: String) {
    Log.e(TAG, msg)
    LogCollector.reportError(msg)
}

fun Any.logError(throwable: Throwable, msg: String? = null) {
    Log.e(this::class.java.simpleName, msg ?: "", throwable)

    if (msg == null) {
        LogCollector.reportError(throwable.stackTraceToString())
    } else {
        LogCollector.reportError("$msg: \n${throwable.stackTraceToString()}")
    }
}

fun logError(throwable: Throwable, msg: String? = null) {
    Log.e(TAG, msg ?: "", throwable)

    if (msg == null) {
        LogCollector.reportError(throwable.stackTraceToString())
    } else {
        LogCollector.reportError("$msg: \n${throwable.stackTraceToString()}")
    }
}
