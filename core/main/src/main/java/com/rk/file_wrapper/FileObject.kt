package com.rk.file_wrapper

import android.content.Context
import android.net.Uri
import java.io.InputStream
import java.io.OutputStream
import java.io.Serializable
import java.nio.charset.Charset

interface FileObject : Serializable {
    fun listFiles(): List<FileObject>
    fun isDirectory(): Boolean
    fun isFile(): Boolean
    fun getName(): String
    fun getParentFile(): FileObject?
    fun exists():Boolean
    fun createNewFile():Boolean
    fun getCanonicalPath(): String
    fun mkdir():Boolean
    fun mkdirs():Boolean
    fun writeText(text:String)
    fun getInputStream():InputStream
    fun getOutPutStream(append:Boolean):OutputStream
    fun getAbsolutePath():String
    fun length():Long
    fun delete():Boolean
    fun toUri():Uri
    fun getMimeType(context:Context):String?
    fun renameTo(string: String):Boolean
    fun hasChild(name:String):Boolean
    fun createChild(createFile:Boolean,name: String):FileObject?
    fun canWrite():Boolean
    fun canRead():Boolean
    fun getChildForName(name: String):FileObject
    fun readText():String?
    fun readText(charset:Charset):String?
    suspend fun writeText(content:String,charset:Charset): Boolean
    fun isSymlink():Boolean
}
