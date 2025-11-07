package com.rk.file

import android.content.Context
import android.net.Uri
import android.webkit.MimeTypeMap
import androidx.core.net.toUri
import com.rk.utils.toast
import com.rk.resources.strings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.nio.charset.Charset
import java.nio.file.Files
import java.util.Locale


class FileWrapper(var file: File) : FileObject {
    override suspend fun listFiles(): List<FileObject> = withContext(Dispatchers.IO) {
        val list = file.listFiles()
        if (list.isNullOrEmpty()) {
            return@withContext emptyList()
        }
        return@withContext list.map { f -> FileWrapper(f) }
    }

    override fun isDirectory(): Boolean {
        return file.isDirectory
    }

    override suspend fun getCanonicalPath(): String = withContext(Dispatchers.IO) {
        return@withContext file.canonicalPath
    }

    override suspend fun writeText(
        content: String,
        charset: Charset
    ): Boolean = withContext(Dispatchers.IO) {
        if (canWrite().not()) {
            toast(strings.permission_denied)
            return@withContext false
        }
        withContext(Dispatchers.IO) {
            file.writeText(content,charset)
        }

        return@withContext true
    }

    override fun isFile(): Boolean {
        return file.isFile
    }

    override fun getName(): String {
        return file.name
    }

    override suspend fun getParentFile(): FileObject? = withContext(Dispatchers.IO) {
        return@withContext file.parentFile?.let { FileWrapper(it) }
    }

    override suspend fun exists(): Boolean = withContext(Dispatchers.IO) {
        return@withContext file.exists()
    }

    override suspend fun createNewFile(): Boolean = withContext(Dispatchers.IO) {
        return@withContext file.createNewFile()
    }

    override suspend fun mkdir(): Boolean = withContext(Dispatchers.IO) {
        return@withContext file.mkdir()
    }

    override suspend fun mkdirs(): Boolean = withContext(Dispatchers.IO) {
        return@withContext file.mkdirs()
    }

    override suspend fun writeText(text: String) = withContext(Dispatchers.IO) {
        return@withContext file.writeText(text)
    }

    override suspend fun getInputStream(): InputStream = withContext(Dispatchers.IO) {
        return@withContext FileInputStream(file)
    }

    override suspend fun getOutPutStream(append: Boolean): OutputStream = withContext(Dispatchers.IO) {
        return@withContext if (append) {
            FileOutputStream(file, true)
        } else {
            FileOutputStream(file, false).also { it.channel.truncate(0) }
        }
    }


    override fun getAbsolutePath(): String {
        return file.absolutePath
    }

    override suspend fun length(): Long = withContext(Dispatchers.IO) {
        return@withContext file.length()
    }

    override suspend fun delete(): Boolean = withContext(Dispatchers.IO) {
        return@withContext if (isDirectory()) {
            file.deleteRecursively()
        } else {
            file.delete()
        }
    }

    override suspend fun toUri(): Uri = withContext(Dispatchers.IO) {
        return@withContext file.toUri()
    }

    override suspend fun getMimeType(context: Context): String? = withContext(Dispatchers.IO) {
        val uri: Uri = Uri.fromFile(file)
        val extension = MimeTypeMap.getFileExtensionFromUrl(uri.toString())
        return@withContext if (extension != null) {
            MimeTypeMap.getSingleton()
                .getMimeTypeFromExtension(extension.lowercase(Locale.getDefault()))
        } else {
            context.contentResolver.getType(uri)
        }
    }

    override suspend fun renameTo(string: String): Boolean = withContext(Dispatchers.IO) {
        val newFile = File(file.parentFile, string)
        return@withContext file.renameTo(newFile).also { this@FileWrapper.file = newFile }
    }

    override suspend fun hasChild(name: String): Boolean = withContext(Dispatchers.IO) {
        return@withContext File(file, name).exists()
    }

    override suspend fun createChild(createFile: Boolean, name: String): FileObject {
        withContext(Dispatchers.IO){
            if (createFile) {
                File(file, name).apply {
                    createNewFile()
                    return@withContext FileWrapper(this)
                }
            } else {
                File(file, name).apply {
                    mkdirs()
                    return@withContext FileWrapper(this)
                }
            }
        }
        throw IllegalStateException()
    }

    override fun canWrite(): Boolean {
        return file.canWrite()
    }

    override fun canRead(): Boolean {
        return file.canRead()
    }

    override suspend fun getChildForName(name: String): FileObject {
        return FileWrapper(File(file, name))
    }

    override suspend fun readText(): String {
        return file.readText()
    }

    override suspend fun readText(charset: Charset): String {
        return file.readText(charset = charset)
    }

    override fun isSymlink(): Boolean {
        return Files.isSymbolicLink(file.toPath())
    }

    override fun hashCode(): Int {
        return file.hashCode()
    }

    override fun toString(): String {
        return getAbsolutePath()
    }

    override fun equals(other: Any?): Boolean {
        if (other !is FileWrapper) {
            return false
        }
        return other.getAbsolutePath() == getAbsolutePath()
    }
}
