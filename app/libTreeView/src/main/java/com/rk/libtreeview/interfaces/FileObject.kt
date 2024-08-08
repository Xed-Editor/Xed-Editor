package com.rk.libtreeview.interfaces

import java.io.File
import java.io.FileFilter
import java.io.FilenameFilter
interface FileObject {
    val name: String?
    val parent: String?
    val parentFileObject: FileObject?
    val absolutePath: String?
    fun exists(): Boolean
    val isDirectory: Boolean
    val isFile: Boolean
    fun listFiles(): Array<FileObject?>?
    fun listFiles(filter: FilenameFilter?): Array<FileObject?>?
    fun listFiles(filter: FileFilter?): Array<FileObject?>?
    fun mkdir(): Boolean
    fun mkdirs(): Boolean
    fun renameTo(dest: FileObject?): Boolean
    fun createNewFile(): Boolean
    fun delete(): Boolean
    override fun equals(obj: Any?): Boolean

}
