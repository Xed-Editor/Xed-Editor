package com.rk.filetree.provider

import com.rk.filetree.interfaces.FileObject
import java.io.File


//wrapper for java.io.File
class file(val file: File) : FileObject {

    override fun listFiles(): List<FileObject> {
        val list = file.listFiles()
        if (list.isNullOrEmpty()){
            return emptyList()
        }

        return list.map { f -> file(f) }
    }

    fun getNativeFile():File{
        return file
    }

    override fun isDirectory(): Boolean {
        return file.isDirectory
    }

    override fun isFile(): Boolean {
       return file.isFile
    }

    override fun getName(): String {
       return file.name
    }

    override fun getParentFile(): FileObject? {
        return file.parentFile?.let { file(it) }
    }

    override fun getAbsolutePath(): String {
        return file.absolutePath
    }
}