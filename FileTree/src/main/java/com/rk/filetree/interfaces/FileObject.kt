package com.rk.filetree.interfaces

interface FileObject {
    fun listFiles() : List<FileObject>
    fun isDirectory():Boolean
    fun isFile():Boolean
    fun getName():String
    fun getParentFile(): FileObject?
    fun getAbsolutePath():String
}