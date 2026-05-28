package com.rk

import android.util.Log

object XedLog {
    fun d(tag: String, msg: String) = Log.d("XED_$tag", msg)
    fun i(tag: String, msg: String) = Log.i("XED_$tag", msg)
    fun w(tag: String, msg: String) = Log.w("XED_$tag", msg)
    fun e(tag: String, msg: String, throwable: Throwable? = null) = Log.e("XED_$tag", msg, throwable)
}
