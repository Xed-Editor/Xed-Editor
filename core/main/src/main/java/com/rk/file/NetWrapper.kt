package com.rk.file

import android.content.Context
import android.net.Uri
import android.webkit.MimeTypeMap
import java.io.InputStream
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.Charset

class NetWrapper(private val url: URL) : FileObject {
    private fun openConnection(): HttpURLConnection {
        return (url.openConnection() as HttpURLConnection).apply {
            connectTimeout = 10_000
            readTimeout = 10_000
            requestMethod = "GET"
        }
    }

    override suspend fun listFiles(): List<FileObject> = emptyList()

    override fun isDirectory(): Boolean = false

    override fun isFile(): Boolean = true

    override fun getName(): String {
        return url.path.substringAfterLast('/', "")
    }

    override suspend fun getParentFile(): FileObject? {
        val path = url.path
        val parent = path.substringBeforeLast('/', "")
        if (parent.isEmpty()) return null
        return NetWrapper(URL(url.protocol, url.host, url.port, parent))
    }

    override suspend fun exists(): Boolean {
        return try {
            openConnection().run {
                connect()
                responseCode in 200..299
            }
        } catch (e: Exception) {
            false
        }
    }

    override suspend fun createNewFile(): Boolean = false

    override suspend fun getCanonicalPath(): String = url.toString()

    override suspend fun mkdir(): Boolean = false

    override suspend fun mkdirs(): Boolean = false

    override suspend fun writeText(text: String) {
        throw UnsupportedOperationException("URL is read-only")
    }

    override suspend fun getInputStream(): InputStream {
        return url.openStream()
    }

    override suspend fun getOutPutStream(append: Boolean): OutputStream {
        throw UnsupportedOperationException("URL is read-only")
    }

    override fun getAbsolutePath(): String = url.toString()

    override suspend fun length(): Long {
        return try {
            openConnection().contentLengthLong
        } catch (e: Exception) {
            -1L
        }
    }

    override suspend fun calcSize(): Long = length()

    override suspend fun delete(): Boolean = false

    override suspend fun toUri(): Uri = Uri.parse(url.toString())

    override suspend fun getMimeType(context: Context): String? {
        val ext = MimeTypeMap.getFileExtensionFromUrl(url.toString())
        return if (ext.isNotEmpty()) {
            MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext)
        } else {
            null
        }
    }

    override suspend fun renameTo(string: String): Boolean = false

    override suspend fun hasChild(name: String): Boolean = false

    override suspend fun createChild(createFile: Boolean, name: String): FileObject? = null

    override fun canWrite(): Boolean = false

    override fun canRead(): Boolean = true

    override fun canExecute(): Boolean = false

    override fun lastModified(): Long = -1L

    override suspend fun getChildForName(name: String): FileObject {
        throw UnsupportedOperationException("URL is not a directory")
    }

    override suspend fun readText(): String? {
        return getInputStream().bufferedReader().use { it.readText() }
    }

    override suspend fun readText(charset: Charset): String? {
        return getInputStream().reader(charset).use { it.readText() }
    }

    override suspend fun writeText(content: String, charset: Charset): Boolean = false

    override fun isSymlink(): Boolean = false
}
