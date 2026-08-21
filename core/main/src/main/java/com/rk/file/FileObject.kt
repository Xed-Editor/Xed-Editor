package com.rk.file

import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import com.rk.utils.PathUtils.toPath
import com.rk.utils.getTempDir
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.io.Serializable
import java.net.URL
import java.nio.charset.Charset

interface FileObject : Serializable {

    /**
     * Lists the files and directories contained within this [FileObject].
     *
     * @return A list of child [FileObject]s. If an I/O error occurs, an empty list is returned.
     * @throws UnsupportedOperationException If this object does not support listing.
     */
    suspend fun listFiles(): List<FileObject>

    /**
     * Checks whether this object represents a directory.
     *
     * @return `true` if this object is a directory, otherwise `false`.
     */
    fun isDirectory(): Boolean

    /**
     * Checks whether this object represents a file.
     *
     * @return `true` if this object is a file, otherwise `false`.
     */
    fun isFile(): Boolean

    /**
     * Gets the name of this file or directory.
     *
     * @return The file or directory name without its parent path.
     */
    fun getName(): String

    /**
     * Gets the file extension of this object.
     *
     * @return The extension without the leading dot, or an empty string if no extension exists.
     */
    fun getExtension(): String

    /**
     * Gets the parent directory of this object.
     *
     * @return The parent [FileObject], or `null` if this object has no parent.
     */
    suspend fun getParentFile(): FileObject?

    /**
     * Checks whether this file or directory exists.
     *
     * @return `true` if this object exists, otherwise `false`.
     */
    suspend fun exists(): Boolean

    /**
     * Creates a new empty file represented by this object.
     *
     * @return `true` if a new file was created, or `false` if the file already exists.
     * @throws IOException If the file cannot be created.
     */
    suspend fun createNewFile(): Boolean

    /**
     * Gets the canonical path of this object.
     *
     * @return The canonical path.
     * @throws IOException If the canonical path cannot be resolved.
     */
    suspend fun getCanonicalPath(): String

    /**
     * Creates a new directory.
     *
     * @return `true` if the directory was created, or `false` if it already exists.
     * @throws IOException If the directory cannot be created.
     */
    suspend fun mkdir(): Boolean

    /**
     * Creates this directory and any missing parent directories.
     *
     * @return `true` if the directories were created or already exist.
     * @throws IOException If the directories cannot be created.
     */
    suspend fun mkdirs(): Boolean

    /**
     * Writes UTF-8 encoded text content to this object.
     *
     * @param text The text content to write.
     * @throws IOException If the content cannot be written.
     */
    suspend fun writeText(text: String)

    /**
     * Opens an input stream for reading this object.
     *
     * @return An [InputStream] for reading the contents.
     * @throws IOException If the stream cannot be opened.
     */
    suspend fun getInputStream(): InputStream

    /**
     * Opens an input stream, executes the provided block, and automatically closes the stream afterwards.
     *
     * This method should be preferred over [getInputStream] when possible because it prevents resources from remaining
     * open across suspension points.
     *
     * @param block The operation to execute using the opened stream.
     * @return The result returned by [block].
     * @throws IOException If the stream cannot be opened or an I/O error occurs.
     */
    suspend fun <R> useInputStream(block: suspend (InputStream) -> R): R

    /**
     * Opens an output stream for writing to this object.
     *
     * @param append Whether new data should be appended to the existing contents.
     * @return An [OutputStream] for writing.
     * @throws IOException If the stream cannot be opened.
     */
    suspend fun getOutputStream(append: Boolean): OutputStream

    /**
     * Gets the absolute path of this object.
     *
     * @return The absolute path.
     */
    fun getAbsolutePath(): String

    /**
     * Gets the size of this object in bytes.
     *
     * @return The size in bytes or `0` if the size cannot be determined.
     * @throws IOException If the size cannot be determined.
     */
    suspend fun length(): Long

    /**
     * Deletes this object.
     *
     * @return `true` if the object was deleted, or `false` if it could not be deleted.
     * @throws IOException If an I/O error occurs.
     */
    suspend fun delete(): Boolean

    /**
     * Converts this object into a [Uri].
     *
     * @return A [Uri] representing this object.
     * @throws IOException If this object cannot be converted to a URI.
     */
    suspend fun toUri(): Uri

    /**
     * Gets the MIME type of this object.
     *
     * @param context The Android context used for resolving MIME types.
     * @return The MIME type, or `null` if it cannot be determined.
     */
    suspend fun getMimeType(context: Context): String?

    /**
     * Renames this object.
     *
     * @param string The new name.
     * @return `true` if the rename succeeded, otherwise `false`.
     * @throws IOException If the rename operation fails.
     */
    suspend fun renameTo(string: String): Boolean

    /**
     * Checks whether this directory contains a child with the given name.
     *
     * @param name The child name to search for.
     * @return `true` if a child with this name exists, otherwise `false`.
     */
    suspend fun hasChild(name: String): Boolean

    /**
     * Creates a child object inside this directory.
     *
     * This method creates only a single child with the given name. It does not support creating nested paths. Use
     * [resolveOrCreateDirectory] when creating or resolving a directory structure with multiple path segments.
     *
     * @param createFile `true` to create a file, `false` to create a directory.
     * @param name The name of the child to create.
     * @return The created [FileObject], or `null` if creation failed.
     * @throws IOException If an I/O error occurs.
     */
    suspend fun createChild(createFile: Boolean, name: String): FileObject?

    /**
     * Checks whether this object can be written to.
     *
     * @return `true` if this object is writable, otherwise `false`.
     */
    fun canWrite(): Boolean

    /**
     * Checks whether this object can be read.
     *
     * @return `true` if this object is readable, otherwise `false`.
     */
    fun canRead(): Boolean

    /**
     * Checks whether this object can be executed.
     *
     * @return `true` if this object is executable, otherwise `false`.
     */
    fun canExecute(): Boolean

    /**
     * Gets the last modification time of this object.
     *
     * @return The last modification timestamp in milliseconds since the Unix epoch, or `null` if it is unavailable.
     */
    suspend fun lastModified(): Long?

    /**
     * Gets a child object with the given name.
     *
     * This method resolves only a single child of the current object. It does not support resolving nested paths. Use
     * [resolve] when resolving a path with multiple segments.
     *
     * @param name The child name to look up.
     * @return The child [FileObject], or `null` if no child with this name exists.
     */
    suspend fun getChild(name: String): FileObject?

    /**
     * Reads the text content of this object using UTF-8 encoding.
     *
     * @return The text content.
     * @throws IOException If the content cannot be read.
     */
    suspend fun readText(): String

    /**
     * Reads the text content of this object using the specified character encoding.
     *
     * @param charset The character encoding to use.
     * @return The text content.
     * @throws IOException If the content cannot be read.
     */
    suspend fun readText(charset: Charset): String

    /**
     * Writes text content using the specified character encoding.
     *
     * @param content The text content to write.
     * @param charset The character encoding to use.
     * @return `true` if the content was written successfully, otherwise `false`.
     * @throws IOException If an I/O error occurs.
     */
    suspend fun writeText(content: String, charset: Charset): Boolean

    /**
     * Checks whether this object represents a symbolic link.
     *
     * @return `true` if this object is a symbolic link, otherwise `false`.
     */
    fun isSymlink(): Boolean
}

/**
 * Resolves a relative directory path against this [FileObject], creating missing directories.
 *
 * @param path The relative path to resolve (e.g. "a/b/c").
 * @return The resolved [FileObject].
 * @throws IOException If a directory cannot be created.
 */
suspend fun FileObject.resolveOrCreateDirectory(path: String): FileObject {
    var current = this

    for (segment in path.split('/').filter { it.isNotEmpty() }) {
        current =
            current.getChild(segment)
                ?: current.createChild(createFile = false, name = segment)
                ?: throw IOException("Failed to create directory '$segment'")
    }

    return current
}

/**
 * Resolves a relative path against this [FileObject].
 *
 * @param path The relative path to resolve (e.g. "a/b/c").
 * @return The resolved [FileObject], or `null` if any segment does not exist.
 */
suspend fun FileObject.resolve(path: String): FileObject? {
    var current = this

    for (segment in path.split('/').filter { it.isNotEmpty() }) {
        current = current.getChild(segment) ?: return null
    }

    return current
}

suspend fun FileObject.copyToTempDir() = run {
    val file = File(getTempDir(), getName()).createFileIfNot()

    getInputStream().use { input -> file.outputStream().use { output -> input.copyTo(output) } }

    file
}

fun Uri.toFileObject(expectedIsFile: Boolean): FileObject {
    if (this.toString().startsWith("http")) {
        return NetWrapper(URL(toString()))
    }

    // On Android 11+, force Uri if we lack full storage access (scoped storage rules)
    if (needsUriFallback()) {
        return UriWrapper(this, !expectedIsFile)
    }

    // Try to resolve to a real File (for direct access when possible)
    val file = File(this.toPath())

    // If File access works and matches expectations (file vs. dir), use it
    if (file.exists() && file.canRead() && file.canWrite() && expectedIsFile == file.isFile) {
        return FileWrapper(file)
    }

    // Fallback to Uri for safety/compatibility
    return UriWrapper(this, !expectedIsFile)
}

private fun needsUriFallback(): Boolean {
    return Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && !Environment.isExternalStorageManager()
}
