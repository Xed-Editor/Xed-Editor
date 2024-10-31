package com.rk.xededitor.MainActivity.tabs.core

import android.view.View
import java.io.File

interface CoreFragment {
    fun getView(): View?
    fun onDestroy()
    fun onCreate()
    fun loadFile(file: File)
    fun getFile(): File?
}