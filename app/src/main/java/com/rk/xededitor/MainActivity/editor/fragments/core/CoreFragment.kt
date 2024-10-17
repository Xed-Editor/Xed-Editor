package com.rk.xededitor.MainActivity.editor.fragments.core

import android.content.Context
import android.view.View
import com.rk.xededitor.MainActivity.editor.TabFragment
import java.io.File

interface CoreFragment {
     fun getView(): View?
     fun onDestroy()
     fun onCreate()
     fun loadFile(file: File)
     fun getFile():File?
}