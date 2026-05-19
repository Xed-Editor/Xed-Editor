package com.rk.file.external

import android.content.Context
import android.net.Uri
import android.util.Log
import android.webkit.MimeTypeMap
import com.rk.file.FileObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.schmizz.sshj.DefaultConfig
import net.schmizz.sshj.SSHClient
import net.schmizz.sshj.transport.compression.NoneCompression
import net.schmizz.sshj.sftp.FileAttributes
import net.schmizz.sshj.sftp.SFTPClient
import net.schmizz.sshj.transport.verification.PromiscuousVerifier
import net.schmizz.sshj.xfer.FilePermission
import java.io.FileNotFoundException
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.nio.charset.Charset
import java.util.EnumSet
import java.util.Locale

class SFTPFileObject(
    @Transient
    private val connection: SFTPConnection,
    private val absolutePath: String,
    private val isRoot: Boolean = false,
) : FileObject {
    @Transient
    private var attrs: FileAttributes? = null

    @Transient
    private var attrsFetched: Boolean = false

    @Transient
    private var childrenCache: MutableList<FileObject> = mutableListOf()

    @Transient
    private var childrenFetched: Boolean = false

    private val remotePath: String

    init {
        var pathForSftp = ""
        try {
            val uri = Uri.parse(absolutePath)
            if (uri.scheme == "sftp") {
                pathForSftp = uri.path?.trimStart('/') ?: ""
            } else {
                // Better fallback parsing
                val pathStart = absolutePath.indexOf("/", absolutePath.indexOf("//") + 2)
                pathForSftp = if (pathStart != -1) absolutePath.substring(pathStart).trimStart('/') else ""
            }
        } catch (e: Exception) {
            Log.e("SFTPFileObject", "Error parsing absolutePath: '$absolutePath'", e)
            pathForSftp = absolutePath.substringAfterLast("/", "")
        }

        this.remotePath = pathForSftp.ifEmpty { if (isRoot) "/" else "" }

        Log.d(
            "SFTPFileObject",
            "CONSTRUCTOR: input absolutePath='$absolutePath', calculated remotePath='${this.remotePath}', isRoot=$isRoot"
        )
    }

    suspend fun connect() {
        connection.connect()
        prefetchAttributes()
    }

    companion object {
        fun createSessionInternal(
            hostname: String,
            port: Int,
            username: String,
            password: String?
        ): SSHClient? {
            return try {
                SFTPConnection.initializeBouncyCastle()
                val config = DefaultConfig()
                config.compressionFactories = listOf(NoneCompression.Factory())
                val client = SSHClient(config)
                client.connectTimeout = 10000
                client.timeout = 10000
                client.addHostKeyVerifier(PromiscuousVerifier())
                client.connect(hostname, port)
                password?.let { client.authPassword(username, it) }
                client
            } catch (e: Exception) {
                Log.e("SFTPFileObject", "createSession: ${e.message}")
                null
            }

        }

        fun createSftpClientInternal(client: SSHClient): SFTPClient? {
            return try {
                client.newSFTPClient()
            } catch (e: IOException) {
                Log.e("SftpFileObject", "SFTPClient creation failed: ${e.message}", e)
                client.disconnect()
                null
            }
        }
    }

    private fun normalizeAbs(path: String): String {
        return if (path.startsWith("/")) path else "/$path"
    }

    private suspend fun <T> withSftpClient(block: suspend (SFTPClient) -> T): T {
        return connection.withSftpClient { block(it) }
    }

    suspend fun prefetchAttributes() {
        if (attrsFetched) return

        attrs = try {
            withSftpClient { sftp ->
                // Ensure we are synchronized even for attributes
                sftp.lstat(if (remotePath.isEmpty() && isRoot) "." else normalizeAbs(remotePath))
            }
        } catch (e: Exception) {
            Log.e("SftpFileObject", "lstat failed for ${normalizeAbs(remotePath)}: ${e.message}", e)
            null
        }
        attrsFetched = true
    }

    override fun getName(): String {
        if (isRoot) {
            return "${connection.username}@${connection.hostname}"
        }
        return remotePath.substringAfterLast('/', if (remotePath.contains('/')) "" else remotePath)
    }

    override fun getExtension(): String {
        return getName().substringAfterLast('.', "")
    }

    override fun getAbsolutePath(): String {
        return absolutePath
    }

    override suspend fun getParentFile(): FileObject? {
        if (isRoot || remotePath.isEmpty() || remotePath == "/") {
            return null
        }
        val parentPathString = remotePath.substringBeforeLast('/', "")
        val parentAbsolutePath = "sftp://${connection.username}@${connection.hostname}:${connection.port}/${
            parentPathString.removePrefix("/")
        }"
        return SFTPFileObject(connection, parentAbsolutePath)
    }

    override fun isFile(): Boolean {
        if (!attrsFetched) {
            Log.w("SFTPFileObject", "isFile() called without prefetching attributes for $remotePath")
        }
        return attrs?.type == net.schmizz.sshj.sftp.FileMode.Type.REGULAR
    }

    override fun isDirectory(): Boolean {
        if (!attrsFetched) {
            Log.w("SFTPFileObject", "isDirectory() called without prefetching attributes for $remotePath")
        }
        return attrs?.type == net.schmizz.sshj.sftp.FileMode.Type.DIRECTORY
    }

    override suspend fun listFiles(): List<FileObject> {
        if (!childrenFetched) {
            fetchChildren()
        }
        return childrenCache
    }

    suspend fun fetchChildren(): List<FileObject> {
        if (childrenFetched) return childrenCache

        val result = withContext(Dispatchers.IO) {
            val files = mutableListOf<SFTPFileObject>()
            if (isDirectory()) {
                try {
                    val entries = withSftpClient { it.ls(normalizeAbs(remotePath)) }
                    for (entry in entries) {
                        val entryName = entry.name
                        if (entryName == "." || entryName == "..") continue

                        val absRemotePath = normalizeAbs(remotePath)
                        val childPath =
                            if (absRemotePath.endsWith("/")) "$absRemotePath$entryName"
                            else "$absRemotePath/$entryName"
                        val childAbsolutePath =
                            "sftp://${connection.username}@${connection.hostname}:${connection.port}$childPath"

                        files.add(SFTPFileObject(connection, childAbsolutePath))
                    }
                } catch (e: Exception) {
                    Log.e("SFTPFileObject", "ls failed for $remotePath: ${e.message}")
                }
            }
            files
        }

        // Limit parallel pre-fetching to avoid SSH channel limits or congestion
        val chunked = result.chunked(5)
        for (chunk in chunked) {
            coroutineScope {
                chunk.forEach { child ->
                    launch { child.prefetchAttributes() }
                }
            }
        }

        childrenCache.clear()
        childrenCache.addAll(result)
        childrenFetched = true
        return childrenCache
    }
//    FIX: INPUT STREAM and OUTPUT STREAMS
    override suspend fun getInputStream(): InputStream {
        if (!exists()) throw FileNotFoundException("File does not exist: $remotePath")
        if (!isFile()) throw IOException("Not a file: $remotePath")

        return withContext(Dispatchers.IO) {
            val client = createSessionInternal(connection.hostname, connection.port, connection.username, connection.password)
                ?: throw IOException("Failed to connect for stream")
            val sftp = createSftpClientInternal(client)
                ?: throw IOException("Failed to open SFTP for stream")
            
            try {
                val file = sftp.open(normalizeAbs(remotePath))
                val stream = file.RemoteFileInputStream()
                
                object : java.io.FilterInputStream(stream) {
                    private var closed = false
                    override fun close() {
                        if (closed) return
                        closed = true
                        try {
                            super.close()
                        } finally {
                            try { sftp.close() } catch (e: Exception) { }
                            try { client.disconnect() } catch (e: Exception) { }
                        }
                    }
                }
            } catch (e: Exception) {
                try { sftp.close() } catch (ex: Exception) { }
                try { client.disconnect() } catch (ex: Exception) { }
                throw e
            }
        }
    }

    override suspend fun <R> useInputStream(block: suspend (InputStream) -> R): R {
        return getInputStream().use { block(it) }
    }

    override suspend fun getOutPutStream(append: Boolean): OutputStream {
        val mode = if (append) EnumSet.of(net.schmizz.sshj.sftp.OpenMode.APPEND) else EnumSet.of(
            net.schmizz.sshj.sftp.OpenMode.WRITE, net.schmizz.sshj.sftp.OpenMode.CREAT, net.schmizz.sshj.sftp.OpenMode.TRUNC
        )

        return withContext(Dispatchers.IO) {
            val client = createSessionInternal(connection.hostname, connection.port, connection.username, connection.password)
                ?: throw IOException("Failed to connect for stream")
            val sftp = createSftpClientInternal(client)
                ?: throw IOException("Failed to open SFTP for stream")
            
            try {
                val file = sftp.open(normalizeAbs(remotePath), mode)
                val stream = file.RemoteFileOutputStream()
                
                object : java.io.FilterOutputStream(stream) {
                    private var closed = false
                    override fun close() {
                        if (closed) return
                        closed = true
                        try {
                            super.close()
                        } finally {
                            try { sftp.close() } catch (e: Exception) { }
                            try { client.disconnect() } catch (e: Exception) { }
                        }
                    }
                }
            } catch (e: Exception) {
                try { sftp.close() } catch (ex: Exception) { }
                try { client.disconnect() } catch (ex: Exception) { }
                throw e
            }
        }
    }

    override suspend fun length(): Long {
        if (!attrsFetched) prefetchAttributes()
        return attrs?.size ?: 0L
    }
    suspend fun calcSize(): Long = withContext(Dispatchers.IO) {
        return@withContext if (isFile()) length() else getFolderSize(this@SFTPFileObject)
    }

    private suspend fun getFolderSize(folder: FileObject): Long {
        var length: Long = 0
        for (file in folder.listFiles()) {
            length += if (file.isFile()) {
                file.length()
            } else {
                getFolderSize(file)
            }
        }
        return length
    }

    override suspend fun lastModified(): Long {
        if (!attrsFetched) prefetchAttributes()
        return attrs?.mtime?.times(1000L) ?: 0L
    }

    override suspend fun exists(): Boolean {
        if (!attrsFetched) prefetchAttributes()
        return attrs != null
    }

    override suspend fun createNewFile(): Boolean {
        if (exists()) return false

        return try {
            withSftpClient { sftp ->
                sftp.open(normalizeAbs(remotePath), EnumSet.of(net.schmizz.sshj.sftp.OpenMode.CREAT)).close()
                true
            }
        } catch (e: Exception) {
            Log.e("SFTPFileObject", "createNewFile failed: ${e.message}")
            false
        }
    }

    override suspend fun getCanonicalPath(): String = absolutePath

    override suspend fun mkdir(): Boolean {
        if (exists()) return false
        return try {
            withSftpClient { sftp ->
                sftp.mkdir(normalizeAbs(remotePath))
                true
            }
        } catch (e: Exception) {
            Log.e("SFTPFileObject", "mkdir failed: ${e.message}")
            false
        }
    }

    override suspend fun mkdirs(): Boolean {
        if (exists()) return isDirectory()
        
        val parts = remotePath.split("/").filter { it.isNotEmpty() }
        var currentPath = if (remotePath.startsWith("/")) "/" else ""
        
        return try {
            withSftpClient { sftp ->
                for (part in parts) {
                    currentPath = if (currentPath.endsWith("/")) "$currentPath$part" else "$currentPath/$part"
                    try {
                        sftp.lstat(currentPath)
                    } catch (_: IOException) {
                        sftp.mkdir(currentPath)
                    }
                }
                true
            }
        } catch (e: Exception) {
            Log.e("SFTPFileObject", "mkdirs failed: ${e.message}")
            false
        }
    }

    override suspend fun delete(): Boolean {
        if (!exists()) return false
        return try {
            withSftpClient { sftp ->
                if (isDirectory()) {
                    deleteRecursive(sftp, remotePath)
                    sftp.rmdir(remotePath)
                } else {
                    sftp.rm(remotePath)
                }
                true
            }
        } catch (e: Exception) {
            Log.e("SFTPFileObject", "delete failed: ${e.message}")
            false
        }
    }

    private fun deleteRecursive(sftp: SFTPClient, path: String) {
        val entries = sftp.ls(path)
        for (entry in entries) {
            if (entry.name == "." || entry.name == "..") continue
            val fullPath = if (path.endsWith("/")) "$path${entry.name}" else "$path/${entry.name}"
            if (entry.isDirectory) {
                deleteRecursive(sftp, fullPath)
                sftp.rmdir(fullPath)
            } else {
                sftp.rm(fullPath)
            }
        }
    }

    override suspend fun toUri(): Uri {
        return Uri.parse("sftp://${connection.username}@${connection.hostname}:${connection.port}/${remotePath.removePrefix("/")}")
    }

    override suspend fun getMimeType(context: Context): String? {
        val extension = getExtension()
        return if (extension.isNotEmpty()) {
            MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension.lowercase(Locale.ROOT))
        } else {
            if (isDirectory()) "inode/directory" else null
        }
    }

    override suspend fun renameTo(string: String): Boolean {
        if (!exists()) return false
        val parentPath = remotePath.substringBeforeLast('/', "")
        val newPath = if (parentPath.isEmpty()) string else "$parentPath/$string"

        return try {
            withSftpClient { sftp ->
                sftp.rename(normalizeAbs(remotePath), normalizeAbs(newPath))
                true
            }
        } catch (e: Exception) {
            Log.e("SFTPFileObject", "renameTo failed: ${e.message}")
            false
        }
    }

    override suspend fun hasChild(name: String): Boolean {
        if (!isDirectory()) return false
        return listFiles().any { it.getName() == name }
    }

    override suspend fun createChild(createFile: Boolean, name: String): FileObject? {
        if (!isDirectory()) return null

        val absRemotePath = normalizeAbs(remotePath)
        val childPath = if (absRemotePath.endsWith("/")) "$absRemotePath$name" else "$absRemotePath/$name"
        val childAbsolutePath = "sftp://${connection.username}@${connection.hostname}:${connection.port}$childPath"
        val childFile = SFTPFileObject(connection, childAbsolutePath)

        return try {
            if (createFile) {
                if (childFile.createNewFile()) childFile else null
            } else {
                if (childFile.mkdir()) childFile else null
            }
        } catch (e: Exception) {
            Log.e("SFTPFileObject", "createChild failed: ${e.message}")
            null
        }
    }

    override fun canWrite(): Boolean {
        if (!attrsFetched) Log.w("SFTPFileObject", "canWrite() called without attributes")
        val perms = attrs?.mode?.permissions ?: return false
        return perms.contains(FilePermission.USR_W) || perms.contains(FilePermission.GRP_W) || perms.contains(FilePermission.OTH_W)
    }

    override fun canRead(): Boolean {
        if (!attrsFetched) Log.w("SFTPFileObject", "canRead() called without attributes")
        val perms = attrs?.mode?.permissions ?: return false
        return perms.contains(FilePermission.USR_R) || perms.contains(FilePermission.GRP_R) || perms.contains(FilePermission.OTH_R)
    }

    override fun canExecute(): Boolean {
        if (!attrsFetched) Log.w("SFTPFileObject", "canExecute() called without attributes")
        return (attrs?.mode?.mask?.and(0b111)) != 0
    }

    override suspend fun getChildForName(name: String): FileObject {
        val absRemotePath = normalizeAbs(remotePath)
        val childPath = if (absRemotePath.endsWith("/")) "$absRemotePath$name" else "$absRemotePath/$name"
        val childAbsolutePath = "sftp://${connection.username}@${connection.hostname}:${connection.port}$childPath"
        return SFTPFileObject(connection, childAbsolutePath)
    }

    override suspend fun readText(): String? = readText(Charsets.UTF_8)

    override suspend fun readText(charset: Charset): String? {
        if (!isFile()) return null
        return try {
            useInputStream { inputStream ->
                inputStream.reader(charset).use { it.readText() }
            }
        } catch (e: Exception) {
            Log.e("SFTPFileObject", "readText failed: ${e.message}")
            null
        }
    }

    override suspend fun writeText(text: String) {
        writeText(text, Charsets.UTF_8)
    }

    override suspend fun writeText(content: String, charset: Charset): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                getOutPutStream(append = false).use { outputStream ->
                    outputStream.writer(charset).use { it.write(content) }
                }
                true
            } catch (e: Exception) {
                Log.e("SFTPFileObject", "writeText failed: ${e.message}")
                false
            }
        }
    }

    override fun isSymlink(): Boolean {
        if (!attrsFetched) Log.w("SFTPFileObject", "isSymlink() called without attributes")
        return attrs?.type == net.schmizz.sshj.sftp.FileMode.Type.SYMLINK
    }

    suspend fun disconnect() {
        connection.disconnect()
    }
}
