package com.rk.xededitor.MainActivity.tabs.core

import android.view.View
import com.rk.filetree.interfaces.FileObject
import java.io.File

interface CoreFragment {
    fun getView(): View?
    fun onDestroy()
    fun onCreate()
    fun loadFile(file: FileObject)
    fun getFile(): FileObject?
    fun onClosed()
}