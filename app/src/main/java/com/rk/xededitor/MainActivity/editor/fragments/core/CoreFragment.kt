package com.rk.xededitor.MainActivity.editor

import android.view.View

abstract class CoreFragment() {
    abstract fun getView(): View?
    abstract fun onDestroy()
    abstract fun onCreate()
}