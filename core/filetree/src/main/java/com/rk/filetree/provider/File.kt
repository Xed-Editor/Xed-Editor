package com.rk.filetree.provider

import com.rk.filetree.interfaces.FileObject
import java.io.File

class FileWrapper(val file: File) : FileObject {
    inline override fun listFiles(): List<FileObject> {
        val list = file.listFiles()
        if (list.isNullOrEmpty()) {
            return emptyList()
        }
        return list.map { f -> FileWrapper(f) }
    }

    inline override fun isDirectory(): Boolean {
        return file.isDirectory
    }

    inline override fun isFile(): Boolean {
        return file.isFile
    }

    inline override fun getName(): String {
        return file.name
    }

    inline override fun getParentFile(): FileObject? {
        return file.parentFile?.let { FileWrapper(it) }
    }

    inline override fun getAbsolutePath(): String {
        return file.absolutePath
    }
}
