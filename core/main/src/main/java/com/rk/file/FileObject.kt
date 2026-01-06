package com.rk.file

import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import com.rk.utils.PathUtils.toPath
import com.rk.utils.getTempDir
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.io.Serializable
import java.net.URL
import java.nio.charset.Charset

interface FileObject : Serializable {
    suspend fun listFiles(): List<FileObject>

    fun isDirectory(): Boolean

    fun isFile(): Boolean

    fun getName(): String

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

    suspend fun calcSize(): Long

    suspend fun delete(): Boolean

    suspend fun toUri(): Uri

    suspend fun getMimeType(context: Context): String?

    suspend fun renameTo(string: String): Boolean

    suspend fun hasChild(name: String): Boolean

    suspend fun createChild(createFile: Boolean, name: String): FileObject?

    fun canWrite(): Boolean

    fun canRead(): Boolean

    fun canExecute(): Boolean

    suspend fun getChildForName(name: String): FileObject

    suspend fun readText(): String?

    suspend fun readText(charset: Charset): String?

    suspend fun writeText(content: String, charset: Charset): Boolean

    fun isSymlink(): Boolean
}

suspend fun FileObject.copyToTempDir() = run {
    val file = File(getTempDir(), getName()).createFileIfNot()

    getInputStream().use { input -> file.outputStream().use { output -> input.copyTo(output) } }

    file
}

suspend fun Uri.toFileObject(expectedIsFile: Boolean): FileObject? {

    if (toString().startsWith("content://telephony")) {
        return null
    }

    if (this.toString().startsWith("http")) {
        return NetWrapper(URL(toString()))
    }

    // First, try to resolve to a real File (for direct access when possible)
    val file = File(this.toPath())

    // On Android 11+, force Uri if we lack full storage access (scoped storage rules)
    if (needsUriFallback()) {
        return UriWrapper(this, !expectedIsFile)
    }

    // If File access works and matches expectations (file vs. dir), use it
    if (file.exists() && file.canRead() && file.canWrite() && expectedIsFile == file.isFile) {
        return FileWrapper(file)
    }

    // Fallback to Uri for safety/compatibility
    return UriWrapper(this, !expectedIsFile)
}

private suspend fun needsUriFallback(): Boolean {
    return Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && !Environment.isExternalStorageManager()
}
