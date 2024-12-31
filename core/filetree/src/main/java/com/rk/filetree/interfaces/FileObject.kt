package com.rk.filetree.interfaces

import android.content.Context
import android.net.Uri
import androidx.core.net.toUri
import com.rk.filetree.provider.FileWrapper
import com.rk.filetree.provider.UriWrapper
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.io.Serializable

interface FileObject : Serializable {
    fun listFiles(): List<FileObject>
    fun isDirectory(): Boolean
    fun isFile(): Boolean
    fun getName(): String
    fun getParentFile(): FileObject?
    fun exists():Boolean
    fun createNewFile():Boolean
    fun mkdir():Boolean
    fun mkdirs():Boolean
    fun writeText(text:String)
    fun getInputStream():InputStream
    fun getOutPutStream(append:Boolean):OutputStream
    fun getAbsolutePath():String
    fun length():Long
    fun delete():Boolean
    fun toUri():Uri
}
