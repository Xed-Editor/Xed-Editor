package com.rk.file

import android.content.Context
import android.net.Uri
import android.webkit.MimeTypeMap
import androidx.documentfile.provider.DocumentFile
import com.rk.libcommons.application
import com.rk.libcommons.errorDialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import java.io.InputStream
import java.io.InputStreamReader
import java.io.OutputStream
import java.net.URLDecoder
import java.nio.charset.Charset
import java.util.Locale

class UriWrapper : FileObject {

    private val uri: String
    private val isTree: Boolean

    @Transient
    private var _file: DocumentFile? = null

    var file: DocumentFile
        get() {
            if (_file == null) {
                _file = Uri.parse(uri).getDocumentFile(isTree)!!
            }
            return _file!!
        }
        set(value) {
            _file = value
        }

    constructor(file: DocumentFile) {
        this.file = file
        this.uri = file.uri.toString()
        isTree = file.isDirectory
    }

    @Throws(IllegalArgumentException::class)
    constructor(uri: Uri, isTree: Boolean) : this(uri.getDocumentFile(isTree)!!)


    override suspend fun listFiles(): List<FileObject> = when {
        !file.isDirectory -> emptyList()
        !file.canRead() -> emptyList()
        else -> file.listFiles().map { UriWrapper(it) }
    }

    override suspend fun isDirectory(): Boolean = file.isDirectory
    override suspend fun isFile(): Boolean = file.isFile
    override suspend fun getName(): String = file.name ?: "Invalid"
    override suspend fun getParentFile(): FileObject? =
        file.parentFile?.let { UriWrapper(it) }

    override suspend fun exists(): Boolean = file.exists()

    fun isTermuxUri(): Boolean {
        return uri.startsWith("content://com.termux")
    }

    fun convertToTermuxFile(): File {
        if (isTermuxUri().not()) {
            throw IllegalStateException("this uri is not a termux uri")
        }

        val path = URLDecoder.decode(file.uri.toString(), "UTF-8")
            .removePrefix("content://com.termux.documents/tree//data/data/com.termux/files/home/document/")
        if (path.startsWith("/data").not()) {
            errorDialog("Converting termux uri into realpath failed: \nURI : ${file.uri}\n\nPATH : $path")
        }
        //dialog(title = "PATH", msg = path, onOk = {})
        return File(path)
    }

    override suspend fun createNewFile(): Boolean {
        if (exists()) return false

        val parent = file.parentFile ?: throw IOException("Parent directory doesn't exist")

        return parent.createFile(
            file.type ?: "application/octet-stream", file.name ?: "unnamed"
        ) != null
    }

    override suspend fun getCanonicalPath(): String {
        return getAbsolutePath()
    }

    @Throws(IOException::class)
    override suspend fun mkdir(): Boolean {
        if (exists()) return false

        val parent = file.parentFile ?: throw IOException("Parent directory doesn't exist")

        return parent.createDirectory(file.name ?: "unnamed") != null
    }

    override suspend fun mkdirs(): Boolean {
        if (exists()) return true

        val parent = file.parentFile ?: throw IOException("Cannot create parent directory")
        if (!parent.exists()) {
            UriWrapper(parent).mkdirs()
        }
        return mkdir()
    }

    override suspend fun writeText(text: String) {
        getOutPutStream(false).use { outputStream ->
            try {
                outputStream.write(text.toByteArray())
                outputStream.flush()
            } catch (e: IOException) {
                throw IOException("Failed to write to file: ${file.uri}", e)
            }
        }
    }

    @Throws(FileNotFoundException::class, SecurityException::class)
    override suspend fun getInputStream(): InputStream {
        return application!!.contentResolver?.openInputStream(file.uri)
            ?: throw IOException("Could not open input stream for: ${file.uri}")
    }

    override suspend fun getOutPutStream(append: Boolean): OutputStream {
        val mode = if (append) "wa" else "wt"
        return application!!.contentResolver?.openOutputStream(file.uri, mode)
            ?: throw IOException("Could not open input stream for: ${file.uri}")
    }

    override suspend fun getMimeType(context: Context): String? {
        val uri = toUri()
        val extension = MimeTypeMap.getFileExtensionFromUrl(uri.toString())
        return if (extension != null) {
            MimeTypeMap.getSingleton()
                .getMimeTypeFromExtension(extension.lowercase(Locale.getDefault()))
        } else {
            context.contentResolver.getType(uri)
        }
    }

    override suspend fun renameTo(string: String): Boolean {
        return file.renameTo(string)
    }

    override suspend fun hasChild(name: String): Boolean {
        if (isDirectory()) {
            for (child in listFiles()) {
                if (child.getName() == name) {
                    return true
                }
            }
        }
        return false
    }

    override suspend fun createChild(createFile: Boolean, name: String): FileObject? {
        return if (createFile) {
            file.createFile("application/octet-stream", name)?.let { UriWrapper(it) }
        } else {
            file.createDirectory(name)?.let { UriWrapper(it) }
        }
    }


    override fun getAbsolutePath(): String = toString()

    override suspend fun length(): Long {
        return file.length()
    }

    override suspend fun delete(): Boolean {
        fun deleteFolder(documentFile: DocumentFile): Boolean {
            if (!documentFile.isDirectory) {
                return documentFile.delete()
            }
            documentFile.listFiles().forEach { child ->
                deleteFolder(child)
            }
            return documentFile.delete()
        }


        return deleteFolder(file)
    }

    override fun toUri(): Uri {
        return file.uri
    }

    override suspend fun canWrite(): Boolean {
        return file.canWrite()
    }

    override suspend fun canRead(): Boolean {
        return file.canRead()
    }

    override suspend fun getChildForName(name: String): FileObject {
        if (!file.isDirectory) {
            throw IllegalStateException("Cannot get child for a non-directory file")
        }

        val child = file.listFiles().find { it.name == name || it.name == name.removePrefix("/") }
            ?: throw FileNotFoundException("Child with name $name not found")

        return UriWrapper(child)
    }

    override suspend fun readText(): String? {
        val uri: Uri = file.uri
        return application!!.contentResolver.openInputStream(uri)?.use { inputStream ->
            BufferedReader(InputStreamReader(inputStream)).use { reader ->
                reader.readText()
            }
        }
    }

    override suspend fun readText(charset: Charset): String? {
        val uri: Uri = file.uri
        return application!!.contentResolver.openInputStream(uri)?.use { inputStream ->
            BufferedReader(InputStreamReader(inputStream, charset)).use { reader ->
                reader.readText()
            }
        }
    }

    override suspend fun writeText(
        content: String,
        charset: Charset
    ): Boolean {
        withContext(Dispatchers.IO) {
            getOutPutStream(false).use {
                it.write(content.toByteArray(charset))
                it.flush()
            }
        }
        return true
    }

    override suspend fun isSymlink(): Boolean {
        return false
    }

    override fun equals(other: Any?): Boolean {
        if (other !is UriWrapper) {
            return false
        }
        return other.uri == uri
    }

    override fun hashCode(): Int {
        return file.uri.hashCode()
    }

    override fun toString(): String {
        return file.uri.toString()
    }

    companion object {
        fun Uri.getDocumentFile(isTree: Boolean): DocumentFile? {
            return if (isTree) {
                DocumentFile.fromTreeUri(application!!, this)
            } else {
                DocumentFile.fromSingleUri(application!!, this)
            }
        }
    }

}
