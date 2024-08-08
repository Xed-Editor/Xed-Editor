package com.rk.libtreeview.interfaces

import android.graphics.drawable.Drawable

interface FileIconProvider {
    fun getIconForFile(fileObject: FileObject) : Drawable?
    fun getIconForFolder(fileObject: FileObject) : Drawable?
}