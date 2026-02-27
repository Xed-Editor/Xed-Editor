package com.rk.tabs.base

import com.rk.file.BuiltinFileType
import com.rk.file.FileObject
import com.rk.file.FileTypeManager
import com.rk.tabs.image.ImageTab

object TabRegistry {
    fun getTab(file: FileObject): Tab? {
        val ext = file.getExtension()
        val type = FileTypeManager.fromExtension(ext)

        return when (type) {
            BuiltinFileType.IMAGE -> ImageTab(file)

            // Open code editor
            else -> null
        }
    }
}
