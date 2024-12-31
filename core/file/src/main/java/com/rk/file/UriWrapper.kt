package com.rk.file

import android.app.Application
import android.content.Context
import android.net.Uri
import android.webkit.MimeTypeMap
import androidx.documentfile.provider.DocumentFile
import java.io.FileNotFoundException
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.Locale

class UriWrapper(val context: Application, val file: DocumentFile) : FileObject {

    @Throws(IllegalArgumentException::class)
    constructor(context: Application, uri: Uri) : this(
        context, when {
            uri.toString().contains("tree/") -> DocumentFile.fromTreeUri(context, uri)
            else -> DocumentFile.fromSingleUri(context, uri)
        } ?: throw IllegalArgumentException("Invalid Uri or missing permission: $uri")
    )

    override fun listFiles(): List<FileObject> = when {
        !file.isDirectory -> emptyList()
        !file.canRead() -> throw SecurityException("No read permission for directory: ${file.uri}")
        else -> file.listFiles().map { UriWrapper(context, it) }
    }

    override fun isDirectory(): Boolean = file.isDirectory
    override fun isFile(): Boolean = file.isFile
    override fun getName(): String = file.name.orEmpty()
    override fun getParentFile(): FileObject? =
        file.parentFile?.let { UriWrapper(context, it) }

    override fun exists(): Boolean = file.exists()

    @Throws(IOException::class)
    override fun createNewFile(): Boolean {
        if (exists()) return false

        val parent = file.parentFile ?: throw IOException("Parent directory doesn't exist")
        if (!parent.canWrite()) {
            throw SecurityException("No write permission for parent directory: ${parent.uri}")
        }

        return parent.createFile(
            file.type ?: "application/octet-stream", file.name ?: "unnamed"
        ) != null
    }

    @Throws(IOException::class)
    override fun mkdir(): Boolean {
        if (exists()) return false

        val parent = file.parentFile ?: throw IOException("Parent directory doesn't exist")
        if (!parent.canWrite()) {
            throw SecurityException("No write permission for parent directory: ${parent.uri}")
        }

        return parent.createDirectory(file.name ?: "unnamed") != null
    }

    @Throws(IOException::class)
    override fun mkdirs(): Boolean {
        if (exists()) return true

        val parent = file.parentFile ?: throw IOException("Cannot create parent directory")
        if (!parent.exists()) {
            UriWrapper(context, parent).mkdirs()
        }
        return mkdir()
    }

    @Throws(IOException::class)
    override fun writeText(text: String) {
        if (!file.canWrite()) {
            throw SecurityException("No write permission for file: ${file.uri}")
        }

        context.contentResolver?.openOutputStream(file.uri)?.use { outputStream ->
            try {
                outputStream.write(text.toByteArray())
                outputStream.flush()
            } catch (e: IOException) {
                throw IOException("Failed to write to file: ${file.uri}", e)
            }
        } ?: throw IOException("Could not open output stream for: ${file.uri}")
    }

    @Throws(FileNotFoundException::class, SecurityException::class)
    override fun getInputStream(): InputStream {
        if (!file.canRead()) {
            throw SecurityException("No read permission for file: ${file.uri}")
        }

        return context.contentResolver?.openInputStream(file.uri)
            ?: throw FileNotFoundException("Could not open input stream for: ${file.uri}")
    }

    override fun getOutPutStream(append: Boolean): OutputStream {
        if (!file.canRead()) {
            throw SecurityException("No read permission for file: ${file.uri}")
        }
        val mode = if (append) "wa" else "w"
        return context.contentResolver?.openOutputStream(file.uri, mode)
            ?: throw FileNotFoundException("Could not open input stream for: ${file.uri}")

    }

    override fun getMimeType(context: Context): String? {
        val uri = toUri()
        val extension = MimeTypeMap.getFileExtensionFromUrl(uri.toString())
        return if (extension != null) {
            MimeTypeMap.getSingleton()
                .getMimeTypeFromExtension(extension.lowercase(Locale.getDefault()))
        } else {
            context.contentResolver.getType(uri)
        }
    }

    override fun renameTo(string: String): Boolean {
        return file.renameTo(string)
    }

    override fun hasChild(name: String): Boolean {
        if (isDirectory()) {
            for (child in listFiles()) {
                if (child.getName() == name) {
                    return true
                }
            }
        }
        return false
    }

    override fun createChild(createFile: Boolean, name: String):FileObject? {
        return if (createFile){
            file.createFile("application/octet-stream",name)?.let { UriWrapper(context, it) }
        }else{
            file.createDirectory(name)?.let { UriWrapper(context,it) }
        }
    }


    override fun getAbsolutePath(): String = toString()

    override fun length(): Long {
        return file.length()
    }

    override fun delete(): Boolean {
        return file.delete()
    }

    override fun toUri(): Uri {
        return file.uri
    }

    override fun canWrite(): Boolean {
        return file.canWrite()
    }

    override fun canRead(): Boolean {
        return file.canRead()
    }

    override fun equals(other: Any?): Boolean {
        if (other !is UriWrapper) {
            return false
        }
        return other.file.uri.toString() == file.uri.toString()
    }

    override fun hashCode(): Int {
        return file.uri.hashCode()
    }

    override fun toString(): String {
        return file.uri.toString()
    }

}