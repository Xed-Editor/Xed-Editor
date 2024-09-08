package com.rk.filetree.provider

import com.rk.filetree.interfaces.FileObject
import java.io.File


//wrapper for java.io.File
class file(val file: File) : FileObject {
    private val isfile = file.isFile
    private val isDir = file.isDirectory

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

    fun findGitRoot(): File? {
        var currentFile = this
        while (currentFile.getParentFile() != null) {
            val parentDir = currentFile.getParentFile()!!.getNativeFile()
            if (File(parentDir, ".git").exists()) {
                return currentFile.getParentFile()!!.getNativeFile()
            }
            currentFile = currentFile.getParentFile()!!
        }
        return null
    }


    override fun isDirectory(): Boolean {
        return isDir
    }

    override fun isFile(): Boolean {
       return isfile
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

    override fun createFromPath(path: String): FileObject {
        return file(File(path))
    }
}