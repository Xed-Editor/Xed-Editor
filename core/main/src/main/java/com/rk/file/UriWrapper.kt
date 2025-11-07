package com.rk.file

import android.content.Context
import android.net.Uri
import android.webkit.MimeTypeMap
import androidx.documentfile.provider.DocumentFile
import com.rk.utils.application
import com.rk.utils.errorDialog
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


    override suspend fun listFiles(): List<FileObject> = withContext(Dispatchers.IO) {
        return@withContext when {
            !file.isDirectory -> emptyList()
            !file.canRead() -> emptyList()
            else -> file.listFiles().map { UriWrapper(it) }
        }
    }

    override fun isDirectory(): Boolean = file.isDirectory
    override fun isFile(): Boolean = file.isFile
    override fun getName(): String = file.name ?: "Invalid"

    override suspend fun getParentFile(): FileObject? = file.parentFile?.let { UriWrapper(it) }

    override suspend fun exists(): Boolean = file.exists()

    fun isTermuxUri(): Boolean {
        return getAbsolutePath().startsWith("content://com.termux")
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

    override suspend fun createNewFile(): Boolean = withContext(Dispatchers.IO) {
        if (exists()) return@withContext false

        val parent = file.parentFile ?: throw IOException("Parent directory doesn't exist")

        return@withContext parent.createFile(
            file.type ?: "application/octet-stream", file.name ?: "unnamed"
        ) != null
    }

    override suspend fun getCanonicalPath(): String = withContext(Dispatchers.IO) {
        return@withContext getAbsolutePath()
    }

    @Throws(IOException::class)
    override suspend fun mkdir(): Boolean = withContext(Dispatchers.IO) {
        if (exists()) return@withContext false

        val parent = file.parentFile ?: throw IOException("Parent directory doesn't exist")

        return@withContext parent.createDirectory(file.name ?: "unnamed") != null
    }

    override suspend fun mkdirs(): Boolean = withContext(Dispatchers.IO) {
        if (exists()) return@withContext true

        val parent = file.parentFile ?: throw IOException("Cannot create parent directory")
        if (!parent.exists()) {
            UriWrapper(parent).mkdirs()
        }
        return@withContext mkdir()
    }

    override suspend fun writeText(text: String) = withContext(Dispatchers.IO) {
        return@withContext getOutPutStream(false).use { outputStream ->
            try {
                outputStream.write(text.toByteArray())
                outputStream.flush()
            } catch (e: IOException) {
                throw IOException("Failed to write to file: ${file.uri}", e)
            }
        }
    }

    @Throws(FileNotFoundException::class, SecurityException::class)
    override suspend fun getInputStream(): InputStream = withContext(Dispatchers.IO) {
        return@withContext application!!.contentResolver?.openInputStream(file.uri)
            ?: throw IOException("Could not open input stream for: ${file.uri}")
    }

    override suspend fun getOutPutStream(append: Boolean): OutputStream = withContext(Dispatchers.IO) {
        val mode = if (append) "wa" else "wt"
        return@withContext application!!.contentResolver?.openOutputStream(file.uri, mode)
            ?: throw IOException("Could not open input stream for: ${file.uri}")
    }

    override suspend fun getMimeType(context: Context): String? = withContext(Dispatchers.IO) {
        val uri = toUri()
        val extension = MimeTypeMap.getFileExtensionFromUrl(uri.toString())
        return@withContext if (extension != null) {
            MimeTypeMap.getSingleton()
                .getMimeTypeFromExtension(extension.lowercase(Locale.getDefault()))
        } else {
            context.contentResolver.getType(uri)
        }
    }

    override suspend fun renameTo(string: String): Boolean = withContext(Dispatchers.IO) {
        return@withContext file.renameTo(string)
    }

    override suspend fun hasChild(name: String): Boolean = withContext(Dispatchers.IO) {
        if (isDirectory()) {
            for (child in listFiles()) {
                if (child.getName() == name) {
                    return@withContext true
                }
            }
        }
        return@withContext false
    }

    override suspend fun createChild(createFile: Boolean, name: String): FileObject? = withContext(Dispatchers.IO) {
        return@withContext if (createFile) {
            file.createFile("application/octet-stream", name)?.let { UriWrapper(it) }
        } else {
            file.createDirectory(name)?.let { UriWrapper(it) }
        }
    }

    override fun getAbsolutePath(): String = toString()

    override suspend fun length(): Long = withContext(Dispatchers.IO) {
        return@withContext file.length()
    }

    override suspend fun calcSize(): Long {
        return if (isFile()) length() else folderSize(this)
    }

    private fun folderSize(folder: FileObject): Long {
        var length: Long = 0
        for (file in folder.listFiles()) {
            length += if (file.isFile()) {
                file.length()
            } else {
                folderSize(file)
            }
        }
        return length
    }

    override suspend fun delete(): Boolean = withContext(Dispatchers.IO) {
        fun deleteFolder(documentFile: DocumentFile): Boolean {
            if (!documentFile.isDirectory) {
                return documentFile.delete()
            }
            documentFile.listFiles().forEach { child ->
                deleteFolder(child)
            }
            return documentFile.delete()
        }

        return@withContext deleteFolder(file)
    }

    override suspend fun toUri(): Uri = withContext(Dispatchers.IO) {
        return@withContext file.uri
    }

    override fun canWrite(): Boolean {
        return file.canWrite()
    }

    override fun canRead(): Boolean {
        return file.canRead()
    }

    override fun canExecute(): Boolean {
        return false
    }

    override suspend fun getChildForName(name: String): FileObject = withContext(Dispatchers.IO) {
        if (!file.isDirectory) {
            throw IllegalStateException("Cannot get child for a non-directory file")
        }

        val child = file.listFiles().find { it.name == name || it.name == name.removePrefix("/") }
            ?: throw FileNotFoundException("Child with name $name not found")

        return@withContext UriWrapper(child)
    }

    override suspend fun readText(): String? = withContext(Dispatchers.IO) {
        val uri: Uri = file.uri
        return@withContext application!!.contentResolver.openInputStream(uri)?.use { inputStream ->
            BufferedReader(InputStreamReader(inputStream)).use { reader ->
                reader.readText()
            }
        }
    }

    override suspend fun readText(charset: Charset): String? = withContext(Dispatchers.IO) {
        val uri: Uri = file.uri
        return@withContext application!!.contentResolver.openInputStream(uri)?.use { inputStream ->
            BufferedReader(InputStreamReader(inputStream, charset)).use { reader ->
                reader.readText()
            }
        }
    }

    override suspend fun writeText(
        content: String,
        charset: Charset
    ): Boolean = withContext(Dispatchers.IO) {
        withContext(Dispatchers.IO) {
            getOutPutStream(false).use {
                it.write(content.toByteArray(charset))
                it.flush()
            }
        }
        return@withContext true
    }

    override fun isSymlink(): Boolean {
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
