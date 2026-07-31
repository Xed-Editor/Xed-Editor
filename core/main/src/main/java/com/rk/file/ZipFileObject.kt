package com.rk.file

import android.content.Context
import android.net.Uri
import android.webkit.MimeTypeMap
import com.rk.resources.getString
import com.rk.resources.strings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileNotFoundException
import java.io.InputStream
import java.io.OutputStream
import java.nio.charset.Charset
import java.util.zip.ZipFile

data class ZipEntryMetadata(
    val size: Long,
    val compressedSize: Long,
    val comment: String?,
    val crc: Long,
    val compressionMethod: Int,
    val lastModified: Long?,
)

enum class ZipCompressionMethod(val id: Int, val label: String) {
    STORED(0, "Stored"),
    SHRUNK(1, "Shrunk"),
    REDUCED_1(2, "Reduced (1)"),
    REDUCED_2(3, "Reduced (2)"),
    REDUCED_3(4, "Reduced (3)"),
    REDUCED_4(5, "Reduced (4)"),
    IMPLODED(6, "Imploded"),
    TOKENIZING(7, "Tokenizing"),
    DEFLATED(8, "Deflated"),
    UNKNOWN(-1, strings.unknown.getString());

    companion object {
        fun fromId(id: Int): ZipCompressionMethod {
            return entries.find { it.id == id } ?: UNKNOWN
        }
    }
}

class ZipFileObject(
    val zipFileObject: FileObject,
    val entryPath: String, // e.g. "" for root, "dir/", "dir/file.txt"
) : FileObject {

    override suspend fun listFiles(): List<FileObject> =
        withContext(Dispatchers.IO) {
            val zipFile = File(zipFileObject.getAbsolutePath())
            if (!zipFile.exists()) return@withContext emptyList()

            val children = mutableSetOf<String>()
            runCatching {
                ZipFile(zipFile).use { zip ->
                    zip.entries().asSequence().forEach { entry ->
                        val name = entry.name
                        if (name.startsWith(entryPath) && name != entryPath) {
                            val relative = name.substring(entryPath.length)

                            val directChild = relative.substringBefore("/")
                            val isFolder = relative.contains("/")
                            children.add(entryPath + directChild + if (isFolder) "/" else "")
                        }
                    }
                }
            }
            children.map { ZipFileObject(zipFileObject, it) }
        }

    override fun isDirectory(): Boolean = entryPath.isEmpty() || entryPath.endsWith("/")

    override fun isFile(): Boolean = !isDirectory()

    override fun getName(): String = entryPath.trimEnd('/').substringAfterLast('/').ifEmpty { zipFileObject.getName() }

    override fun getExtension(): String = getName().substringAfterLast('.', "")

    override suspend fun getParentFile(): FileObject? {
        if (entryPath.isEmpty()) return zipFileObject.getParentFile()
        val parentPath =
            entryPath.trimEnd('/').substringBeforeLast('/', "").let {
                if (it.isEmpty()) "" else "$it/"
            }
        return ZipFileObject(zipFileObject, parentPath)
    }

    override suspend fun exists(): Boolean =
        withContext(Dispatchers.IO) {
            if (entryPath.isEmpty()) return@withContext zipFileObject.exists()
            val zipFile = File(zipFileObject.getAbsolutePath())
            runCatching {
                    ZipFile(zipFile).use { zip ->
                        zip.getEntry(entryPath) != null ||
                            zip.entries().asSequence().any { it.name.startsWith(entryPath) }
                    }
                }
                .getOrDefault(false)
        }

    override suspend fun getCanonicalPath(): String = "${zipFileObject.getCanonicalPath()}!/$entryPath"

    override fun getAbsolutePath(): String = "${zipFileObject.getAbsolutePath()}!/$entryPath"

    override suspend fun getInputStream(): InputStream =
        withContext(Dispatchers.IO) {
            val zipFile = File(zipFileObject.getAbsolutePath())
            val zip = ZipFile(zipFile)
            val entry = zip.getEntry(entryPath)

            if (entry == null) {
                zip.close()
                throw FileNotFoundException("Entry not found")
            }

            return@withContext object : InputStream() {
                private val stream = zip.getInputStream(entry)

                override fun read() = stream.read()

                override fun read(b: ByteArray?) = stream.read(b)

                override fun read(b: ByteArray?, off: Int, len: Int) = stream.read(b, off, len)

                override fun available(): Int = stream.available()

                override fun skip(n: Long): Long = stream.skip(n)

                override fun close() {
                    stream.close()
                    zip.close()
                }
            }
        }

    override suspend fun <R> useInputStream(block: suspend (InputStream) -> R): R =
        withContext(Dispatchers.IO) {
            val zipFile = File(zipFileObject.getAbsolutePath())
            ZipFile(zipFile).use { zip ->
                val entry = zip.getEntry(entryPath) ?: throw FileNotFoundException("Entry not found")
                zip.getInputStream(entry).use { block(it) }
            }
        }

    override suspend fun createNewFile(): Boolean = false

    override suspend fun mkdir(): Boolean = false

    override suspend fun mkdirs(): Boolean = false

    override suspend fun writeText(text: String) {}

    override suspend fun writeText(content: String, charset: Charset): Boolean = false

    override suspend fun getOutputStream(append: Boolean): OutputStream = throw UnsupportedOperationException()

    override suspend fun delete(): Boolean = false

    override suspend fun renameTo(string: String): Boolean = false

    override suspend fun createChild(createFile: Boolean, name: String): FileObject? = null

    override fun canWrite(): Boolean = false

    override fun canRead(): Boolean = true

    override fun canExecute(): Boolean = false

    private suspend fun getZipEntryMetadata(): ZipEntryMetadata? =
        withContext(Dispatchers.IO) {
            val zipFile = File(zipFileObject.getAbsolutePath())

            runCatching {
                ZipFile(zipFile).use { zip ->
                    val entry = zip.getEntry(entryPath) ?: return@use null

                    ZipEntryMetadata(
                        size = entry.size,
                        compressedSize = entry.compressedSize,
                        comment = entry.comment,
                        crc = entry.crc,
                        compressionMethod = entry.method,
                        lastModified = entry.lastModifiedTime?.toMillis(),
                    )
                }
            }
                .getOrNull()
        }

    override suspend fun lastModified(): Long? = getZipEntryMetadata()?.lastModified

    override suspend fun length(): Long = getZipEntryMetadata()?.size ?: 0L

    suspend fun getComment(): String? = getZipEntryMetadata()?.comment

    suspend fun getCompressedSize(): Long = getZipEntryMetadata()?.compressedSize ?: 0L

    suspend fun getCrc(): Long = getZipEntryMetadata()?.crc ?: 0L

    suspend fun getCompressionMethod(): ZipCompressionMethod {
        val method = getZipEntryMetadata()?.compressionMethod ?: -1
        return ZipCompressionMethod.fromId(method)
    }

    override suspend fun toUri(): Uri = Uri.parse("zip://${getAbsolutePath()}")

    override suspend fun getMimeType(context: Context): String? =
        if (isDirectory()) null else MimeTypeMap.getSingleton().getMimeTypeFromExtension(getExtension())

    override suspend fun hasChild(name: String): Boolean = listFiles().any { it.getName() == name }

    override suspend fun getChild(name: String): FileObject? {
        if (!isDirectory()) {
            throw IllegalStateException("Cannot get child of non-directory: $entryPath")
        }

        val childEntryPath = if (entryPath.isEmpty()) name else "$entryPath$name"
        return ZipFileObject(zipFileObject, childEntryPath).takeIf { it.exists() }
    }

    override suspend fun readText(): String = getInputStream().bufferedReader().use { it.readText() }

    override suspend fun readText(charset: Charset): String = getInputStream().reader(charset).use { it.readText() }

    override fun isSymlink(): Boolean = false

    override fun equals(other: Any?): Boolean {
        if (other !is ZipFileObject) return false
        return other.zipFileObject == zipFileObject && other.entryPath == entryPath
    }

    override fun hashCode(): Int = zipFileObject.hashCode() * 31 + entryPath.hashCode()
}
