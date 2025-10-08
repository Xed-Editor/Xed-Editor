package com.rk.file

import android.content.Context
import android.net.Uri
import com.rk.App
import com.rk.libcommons.PathUtils
import com.rk.libcommons.PathUtils.toPath
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.io.Serializable
import java.nio.charset.Charset

// why tf i didnt make them suspended by default?

interface FileObject : Serializable {
    suspend fun listFiles(): List<FileObject>
    suspend fun isDirectory(): Boolean
    suspend fun isFile(): Boolean
    suspend fun getName(): String
    suspend fun getParentFile(): FileObject?
    suspend fun exists(): Boolean
    suspend fun createNewFile(): Boolean
    suspend fun getCanonicalPath(): String
    suspend fun mkdir(): Boolean
    suspend fun mkdirs(): Boolean
    suspend fun writeText(text: String)
    suspend fun getInputStream(): InputStream
    suspend fun getOutPutStream(append: Boolean): OutputStream
    fun getAbsolutePath(): String
    suspend fun length(): Long
    suspend fun delete(): Boolean
    fun toUri(): Uri
    suspend fun getMimeType(context: Context): String?
    suspend fun renameTo(string: String): Boolean
    suspend fun hasChild(name: String): Boolean
    suspend fun createChild(createFile: Boolean, name: String): FileObject?
    suspend fun canWrite(): Boolean
    suspend fun canRead(): Boolean
    suspend fun getChildForName(name: String): FileObject
    suspend fun readText(): String?
    suspend fun readText(charset: Charset): String?
    suspend fun writeText(content: String, charset: Charset): Boolean
    suspend fun isSymlink(): Boolean
}

suspend fun FileObject.copyToTempDir() = withContext(Dispatchers.IO) {
    val file = File(App.getTempDir(), getName()).createFileIfNot()

    getInputStream().use { input ->
        file.outputStream().use { output ->
            input.copyTo(output)
        }
    }

    file
}

fun Uri.toFileObject(isFile: Boolean): FileObject{
    val file = File(this.toPath())
    var shouldUseUri = true
    if (file.exists() && file.canRead() && file.canWrite()){
        if (isFile == file.isFile){
             shouldUseUri = false
        }
    }
    return if (shouldUseUri){
        UriWrapper(this,isFile.not())
    }else{
        FileWrapper(file)
    }
}