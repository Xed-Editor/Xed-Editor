package com.rk.tabs.base

import com.rk.file.FileObject
import com.rk.file.FileType
import com.rk.tabs.image.ImageTab

object TabRegistry {
    fun getTab(file: FileObject): Tab? {
        val ext = file.getName().substringAfterLast('.', "")
        val type = FileType.Companion.fromExtension(ext)

        return when (type) {
            FileType.IMAGE -> ImageTab(file)

            // Open code editor
            else -> null
        }
    }
}
