package com.rk.file

import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import com.rk.App
import com.rk.utils.PathUtils.toPath
import java.io.File
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
    fun exists(): Boolean
    fun createNewFile(): Boolean
    fun getCanonicalPath(): String
    fun mkdir(): Boolean
    fun mkdirs(): Boolean
    fun writeText(text: String)
    fun getInputStream(): InputStream
    fun getOutPutStream(append: Boolean): OutputStream
    fun getAbsolutePath(): String
    fun length(): Long
    suspend fun calcSize(): Long
    fun delete(): Boolean
    fun toUri(): Uri
    fun getMimeType(context: Context): String?
    fun renameTo(string: String): Boolean
    fun hasChild(name: String): Boolean
    fun createChild(createFile: Boolean, name: String): FileObject?
    fun canWrite(): Boolean
    fun canRead(): Boolean
    fun canExecute(): Boolean
    fun getChildForName(name: String): FileObject
    fun readText(): String?
    fun readText(charset: Charset): String?
    suspend fun writeText(content: String, charset: Charset): Boolean
    fun isSymlink(): Boolean
}

fun FileObject.copyToTempDir() = run {
    val file = File(App.getTempDir(), getName()).createFileIfNot()

    getInputStream().use { input ->
        file.outputStream().use { output ->
            input.copyTo(output)
        }
    }

    file
}

fun Uri.toFileObject(expectedIsFile: Boolean): FileObject {
    // First, try to resolve to a real File (for direct access when possible)
    val file = File(this.toPath())

    // On Android 11+, force Uri if we lack full storage access (scoped storage rules)
    if (needsUriFallback()) {
        return UriWrapper(this,!expectedIsFile)
    }

    // If File access works and matches expectations (file vs. dir), use it
    if (file.exists() && file.canRead() && file.canWrite() &&
        expectedIsFile == file.isFile) {
        return FileWrapper(file)
    }

    // Fallback to Uri for safety/compatibility
    return UriWrapper(this,!expectedIsFile)
}


private fun needsUriFallback(): Boolean {
    return Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && !Environment.isExternalStorageManager()
}
