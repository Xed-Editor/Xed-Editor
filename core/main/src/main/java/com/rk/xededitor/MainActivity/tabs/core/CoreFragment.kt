package com.rk.xededitor.MainActivity.tabs.core

import android.view.View
import com.rk.file.FileObject

interface CoreFragment {
    fun getView(): View?
    fun onDestroy()
    fun onCreate()
    fun loadFile(file: com.rk.file.FileObject)
    fun getFile(): com.rk.file.FileObject?
    fun onClosed()
}