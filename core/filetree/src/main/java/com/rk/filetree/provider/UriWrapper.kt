package com.rk.filetree.provider

import android.content.Context
import android.net.Uri
import androidx.core.net.toUri
import androidx.documentfile.provider.DocumentFile
import com.rk.filetree.interfaces.FileObject
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.URI

/**
 * A wrapper class that implements FileObject interface for Android's DocumentFile API.
 * Provides file system operations using Storage Access Framework.
 *
 * @property context Android context required for content resolver operations
 * @property file DocumentFile instance being wrapped
 */
class UriWrapper(val context: Context, val file: DocumentFile) : FileObject {

    /**
     * Creates a UriWrapper from a Uri, automatically determining if it's a single file or tree Uri
     * @throws IllegalArgumentException if the Uri is invalid or permission is not granted
     */

    @Throws(IllegalArgumentException::class)
    constructor(context: Context, uri: Uri) : this(
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

    inline override fun isDirectory(): Boolean = file.isDirectory
    inline override fun isFile(): Boolean = file.isFile
    inline override fun getName(): String = file.name.orEmpty()
    inline override fun getParentFile(): FileObject? =
        file.parentFile?.let { UriWrapper(context, it) }

    inline override fun exists(): Boolean = file.exists()

    /**
     * Creates a new file in the parent directory
     * @return true if file was created successfully, false if it already exists
     * @throws IOException if creation fails or parent directory doesn't exist
     */
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

    /**
     * Creates a new directory
     * @return true if directory was created successfully, false if it already exists
     * @throws IOException if creation fails or parent directory doesn't exist
     */
    @Throws(IOException::class)
    override fun mkdir(): Boolean {
        if (exists()) return false

        val parent = file.parentFile ?: throw IOException("Parent directory doesn't exist")
        if (!parent.canWrite()) {
            throw SecurityException("No write permission for parent directory: ${parent.uri}")
        }

        return parent.createDirectory(file.name ?: "unnamed") != null
    }

    /**
     * Creates directory and any necessary parent directories
     * @return true if directory was created successfully or already exists
     * @throws IOException if creation fails
     */
    @Throws(IOException::class)
    override fun mkdirs(): Boolean {
        if (exists()) return true

        val parent = file.parentFile ?: throw IOException("Cannot create parent directory")
        if (!parent.exists()) {
            UriWrapper(context, parent).mkdirs()
        }
        return mkdir()
    }

    /**
     * Writes text content to the file
     * @throws IOException if writing fails
     */
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

    /**
     * Opens an input stream to read the file
     * @throws FileNotFoundException if the file doesn't exist or can't be opened
     * @throws SecurityException if read permission is not granted
     */
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
        return context.contentResolver?.openOutputStream(file.uri,mode)
            ?: throw FileNotFoundException("Could not open input stream for: ${file.uri}")

    }


    inline override fun getAbsolutePath(): String = toString()

    inline override fun length(): Long {
        return file.length()
    }

    inline override fun delete(): Boolean {
        return file.delete()
    }
    inline override fun toUri(): Uri {
        return file.uri
    }

    override fun equals(other: Any?): Boolean {
        if (other !is UriWrapper){return false}
        return other.file.uri.toString() == file.uri.toString()
    }

    override fun hashCode(): Int {
        return file.uri.hashCode()
    }

    override fun toString(): String {
        return file.uri.toString()
    }

}