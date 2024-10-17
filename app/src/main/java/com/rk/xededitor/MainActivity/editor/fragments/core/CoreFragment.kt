package com.rk.xededitor.MainActivity.editor.fragments.core

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import java.io.File

interface CoreFragment {
    fun getView(): View?
    fun onDestroy()
    fun onCreate()
    fun loadFile(file: File)
    fun getFile(): File?
}