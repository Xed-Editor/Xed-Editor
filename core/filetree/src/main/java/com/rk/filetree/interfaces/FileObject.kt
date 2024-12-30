package com.rk.filetree.interfaces

import java.io.Serializable

interface FileObject : Serializable {
    fun listFiles(): List<FileObject>

    fun isDirectory(): Boolean

    fun isFile(): Boolean

    fun getName(): String

    fun getParentFile(): FileObject?

    fun getAbsolutePath(): String
}
