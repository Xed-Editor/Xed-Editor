package com.rk.xededitor.MainActivity.tabs.core

import android.view.View
import com.rk.file.FileObject

abstract class CoreFragment {
    private var file: FileObject? = null
    abstract fun getView(): View?
    abstract fun onDestroy()
    abstract fun onCreate()
    abstract fun onClosed()

    open fun loadFile(file: FileObject){
        this.file = file
    }
    open fun getFile(): FileObject?{
        return file
    }
}