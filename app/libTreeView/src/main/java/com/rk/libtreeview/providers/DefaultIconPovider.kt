package com.rk.libtreeview.providers

import android.content.Context
import android.graphics.drawable.Drawable
import androidx.core.content.res.ResourcesCompat
import com.rk.libtreeview.R
import com.rk.libtreeview.interfaces.FileIconProvider
import com.rk.libtreeview.interfaces.FileObject

class DefaultIconPovider(context: Context) : FileIconProvider {
    private val icFile = ResourcesCompat.getDrawable(context.resources, R.drawable.outline_insert_drive_file_24, context.theme)
    private val icFolder = ResourcesCompat.getDrawable(context.resources, R.drawable.outline_folder_24, context.theme)

    override fun getIconForFile(fileObject: FileObject): Drawable? {
        return icFile
    }

    override fun getIconForFolder(fileObject: FileObject): Drawable? {
        return icFolder
    }

}