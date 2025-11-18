package com.rk.tabs

import com.rk.file.FileObject
import com.rk.file.FileType

object TabRegistry {
    fun getTab(file: FileObject): Tab? {
        val ext = file.getName().substringAfterLast('.', "")
        val type = FileType.fromExtension(ext)

        return when (type) {
            FileType.IMAGE -> ImageTab(file)

            // Open code editor
            else -> null
        }
    }
}