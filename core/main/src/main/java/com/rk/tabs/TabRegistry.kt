package com.rk.tabs

import com.rk.file.FileObject
import com.rk.file.FileType

object TabRegistry {
    suspend fun getTab(file: FileObject, callback: suspend (Tab?) -> Unit) {
        val ext = file.getName().substringAfterLast('.', "")
        val type = FileType.fromExtension(ext)

        when (type) {
            FileType.IMAGE -> callback(ImageTab(file))

            // Open code editor
            else -> callback(null)
        }
    }
}