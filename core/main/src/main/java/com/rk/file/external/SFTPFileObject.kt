package com.rk.file.external

import android.content.Context
import android.net.Uri
import android.util.Log
import android.webkit.MimeTypeMap
import com.rk.file.FileObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import net.schmizz.sshj.SSHClient
import net.schmizz.sshj.sftp.FileAttributes
import net.schmizz.sshj.sftp.RemoteFile
import net.schmizz.sshj.sftp.RemoteResourceInfo
import net.schmizz.sshj.sftp.SFTPClient
import net.schmizz.sshj.transport.verification.PromiscuousVerifier
import net.schmizz.sshj.userauth.keyprovider.KeyProviderUtil
import net.schmizz.sshj.xfer.FilePermission
import org.bouncycastle.jce.provider.BouncyCastleProvider
import java.io.FileNotFoundException
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.nio.charset.Charset
import java.security.Security
import java.util.Arrays
import java.util.EnumSet
import java.util.Locale

class SFTPFileObject(
    private val hostname: String,
    private val port: Int,
    private val username: String,
    private val password: String,
    @Transient
    private var sshClient: SSHClient?,
    @Transient
    private var sftpClient: SFTPClient?,
    private val absolutePath: String,
    private val isRoot: Boolean = false,
) : FileObject {
    //    variables, I love them, being at the top 🙃
    @Transient
    private var attrs: FileAttributes? = null

    @Transient
    private var attrsFetched: Boolean? = false

    @Transient
    private val childrenCache: MutableList<FileObject>? = mutableListOf<FileObject>()

    @Transient
    private var childrenFetched: Boolean? = false

    @Transient
    private val sftpLock = Mutex()

    private val remotePath: String

    init {
        var pathForSftp = ""
        try {
            val uri = Uri.parse(absolutePath) // "sftp://user@host:port/path/to/file"
            if (uri.scheme == "sftp") {
                pathForSftp =
                    uri.path?.trimStart('/') ?: "" // Gives "path/to/file" or "" if no path
            } else {
                // Fallback or alternative parsing if not a standard URI format
                // This is closer to your original logic, but be careful
                pathForSftp = absolutePath.substringAfterLast("sftp", "")
                    .substringAfterLast("@", "")
                    .substringAfterLast(":", "") // Handle port if present
                    .trimStart('/')
            }
        } catch (e: Exception) {
            Log.e("SFTPFileObject", "Error parsing absolutePath: '$absolutePath'", e)
            pathForSftp = absolutePath.substringAfterLast("/", "")
            if (pathForSftp.isEmpty() && absolutePath.endsWith("/")) { // A guess for root in fallback
                pathForSftp = "/"
            }
        }
//        Ik, the whole remotePath is a mess, currently. But it will be better later.
//        OR, It's keep it as is. If it works fine without huge errors/issues.

        this.remotePath = pathForSftp.ifEmpty {
            // If after all logic pathForSftp is still empty, and this object isRoot,
            // it's problematic. What should root be? Usually "/", or "."
            // This is a safety net.
            if (isRoot) "/" else ""
        }

        Log.d(
            "SFTPFileObject",
            "CONSTRUCTOR: input absolutePath='$absolutePath', calculated remotePath='${this.remotePath}', isRoot=$isRoot"
        )
    }

    suspend fun connect() {
        if (sshClient?.isConnected == true) {
            return
        }
        val client = createSessionInternal(this.hostname, this.port, this.username, this.password)
        if (client == null || !client.isConnected) {
            Log.e("SFTP_CONNECT", "Failed to create or connect session to ${this.hostname}")
            return
        }

        val sftp = createSftpClientInternal(client)

        if (sftp == null) {
            Log.e("SFTP_CONNECT", "Failed to create or connect SFTP channel to ${this.hostname}")
            client.disconnect() // Clean up session
            return
        }

        this.sshClient = client
        this.sftpClient = sftp

        prefetchAttributes()
        fetchChildren()


        // ?: Path is serializable & will last within the process of it.
        // OR maybe I should query it again, If the server has changed it. IDK (think, YOU TOO!).
    }

    companion object {
        fun createSessionInternal(
            hostname: String,
            port: Int,
            username: String,
            password: String?
        ): SSHClient? {
            return try {
                Security.removeProvider("BC")
                Security.insertProviderAt(BouncyCastleProvider(), 0)
                Log.i("SFTP_CONNECT","Security Providers for X25519 " + Security.getProviders()
                    .contentToString()
                )
                val client = SSHClient()
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
        // Always use absolute paths for SFTP
        return if (path.startsWith("/")) path else "/$path"
    }

    suspend fun safeLstat(path: String): FileAttributes? {
        val abs = normalizeAbs(path)
        return withSftpChannel { ch ->
            ch.lstat(abs)
        }
    }

    suspend fun safeLs(path: String): List<RemoteResourceInfo> {
        val abs = normalizeAbs(path)
        return withSftpChannel { ch ->
            ch.ls(abs)
        } ?: emptyList()
    }

    private suspend fun <T> withSftpChannel(block: (SFTPClient) -> T): T? {
        return withContext(Dispatchers.IO) {
            sftpLock.withLock {
                val client = sshClient
                if (client == null || !client.isConnected) {
                    Log.e("SFTP", "Session not connected")
                    return@withLock null
                }

                var sftp: SFTPClient? = null
                try {
                    sftp = createSftpClientInternal(client)
                    if (sftp == null) {
                        Log.e("SFTP", "Failed to open SFTP channel")
                        return@withLock null
                    }
                    block(sftp)
                } catch (e: Exception) {
                    Log.e("SFTP", "SFTP op failed: ${e.message}", e)
                    null
                } finally {
                    try {
                        sftp?.close()
                    } catch (_: Exception) {
                    }
                }
            }
        }
    }

    /**
     * Asynchronously fetches and caches the file attributes from the SFTP server.
     * This MUST be called from a background thread before accessing synchronous
     * methods like isFile(), isDirectory(), getName() if they need network data.
     */
    suspend fun prefetchAttributes() {
        if (attrsFetched == true) return // Don't fetch again if we already tried

        attrs = withContext(Dispatchers.IO) {
            try {
                Log.d("SftpFileObject", "Prefetching attributes for: '$remotePath'")
                if (remotePath.isNotEmpty()) {
                    sftpClient?.lstat(remotePath)
                } else {
                    // Handle case where remotePath might be empty for root, JSch might expect "."
                    // Or if your logic now ensures root is "/", this branch may not be needed.
                    // Let's assume remotePath is now always a valid path like "/" or "file.txt"
                    // If remotePath can be empty, use "."
                    // That's all, Buy a better Chair & touch Grass 🙃
                    sftpClient?.lstat(if (remotePath.isEmpty() && isRoot) "." else remotePath)
                }
            } catch (e: Exception) {
                Log.e("SftpFileObject", "lstat failed for $remotePath: ${e.message}", e)
                null
            }
        }
        attrsFetched = true
    }

    override fun getName(): String {
        if (isRoot) {
            return "$username@$hostname"
        }
        return remotePath.substringAfterLast('/', if (remotePath.contains('/')) "" else remotePath)
    }

    override fun getAbsolutePath(): String {
        return absolutePath.also { println(it) }
    }

    override suspend fun getParentFile(): FileObject? {
        if (isRoot || remotePath.isEmpty() || remotePath == "/") {
            return null // Root has no parent in this context
        }
        val parentPathString = remotePath.substringBeforeLast('/', "")
        val parentAbsolutePath = "sftp://$username@$hostname:$port/${
            parentPathString.removePrefix("/")
        }"
        return SFTPFileObject(
            hostname,
            port,
            username,
            password,
            sshClient,
            sftpClient,
            parentAbsolutePath
        )
    }

    private fun getSftpAttrs(): FileAttributes? {
        Log.d("SftpFileObject", "Attempting lstat for path: '$remotePath'")
        return try {
            sftpClient?.lstat(remotePath)
        } catch (e: IOException) {
            Log.e("SftpFileObject", "lstat failed for $remotePath: ${e.message}", e)
            null
        }
    }

    override fun isFile(): Boolean {
        Log.d("SftpFileObject", "Checking if path is file: '$remotePath'")
        if (attrsFetched?.not() == true) {
            Log.e(
                "SFTPFileObject",
                "isFile() called without prefetching attributes first! This can lead to incorrect results."
            )
        }

        return attrs?.type == net.schmizz.sshj.sftp.FileMode.Type.REGULAR
    }

    override fun isDirectory(): Boolean {
        if (attrsFetched?.not() == true) {
            Log.e("SFTPFileObject", "isDirectory() called without prefetching attributes first!")
        }

        return attrs?.type == net.schmizz.sshj.sftp.FileMode.Type.DIRECTORY
    }

    override suspend fun listFiles(): List<FileObject> {
        Log.d(
            "SftpFileObject",
            "Listing files for path: '$remotePath', childrenFetched=$childrenFetched (using cache/pre-fetched? $childrenFetched), cached children size: ${childrenCache?.size}"
        )

        if(childrenFetched?.not() == true or this.isDirectory()) {
            Log.i(
                "SftpFileObject",
                "fetching children as children are not fetched for path: '$remotePath' currentState -> childrenFetched=$childrenFetched (using cache/pre-fetched? $childrenFetched), cached children size: ${childrenCache?.size}"
                )

            this.fetchChildren().also {
                Log.i("SftpFileObject", "fetched children for path: '$remotePath' currentState -> childrenFetched=$childrenFetched (using cache/pre-fetched? $childrenFetched), cached children size: ${childrenCache?.size}")
            }
        }

        if (childrenFetched?.not() == true) {
            Log.w(
                "SFTPFileObject",
                "listFiles() called without pre-fetching children. Returning empty list."
            )
        }
        return childrenCache ?: emptyList()
    }

    suspend fun fetchChildren(): List<FileObject> {
        if (childrenFetched == true) return childrenCache ?: emptyList()

        val result = withContext(Dispatchers.IO) {
            val files = mutableListOf<SFTPFileObject>()
            if (isDirectory()) { // Uses the cached 'attrs'
                try {
                    val entries = sftpClient?.ls(remotePath)
                    if(entries == null) {
                        Log.e("SFTPFileObject", "ls failed for $remotePath");
                        return@withContext emptyList()
                    }
                    for (entry in entries) {
                        val entryName = entry.name
                        if (entryName == "." || entryName == "..") continue

                        val childPath =
                            if (remotePath.endsWith("/")) remotePath + entryName else "$remotePath/$entryName"
                        val childAbsolutePath =
                            "sftp://$username@$hostname:$port${
                                if (childPath.startsWith(
                                        "/"
                                    )
                                ) childPath else "/$childPath"
                            }"

                        files.add(
                            SFTPFileObject(
                                hostname,
                                port,
                                username,
                                password,
                                sshClient,
                                sftpClient,
                                childAbsolutePath
                            )
                        )
                    }
                } catch (e: Exception) {
                    // handle exception, Am I supposed?? I guess, That's for the TO-DO upside down
                    null
                }
            } else Log.d("SFTPFileObject", "fetchChildren() called on non-directory path: '$remotePath'")
            files
        }

        // Pre-fetch attributes for all children in parallel for efficiency
        coroutineScope {
            result.forEach { child ->
                launch { child.prefetchAttributes() }
            }
        }

        childrenCache?.addAll(result)
        childrenFetched = true
        return childrenCache ?: emptyList()
    }

    override suspend fun getInputStream(): InputStream {
        if (!isFile()) throw FileNotFoundException("Not a file or does not exist: $remotePath")
        try {
            return sftpClient?.open(remotePath)?.RemoteFileInputStream()
                ?: throw IOException("SFTP get failed: null channel")
        } catch (e: IOException) {
            Log.e("SftpFileObject", "get (InputStream) failed for $remotePath: ${e.message}", e)
            throw IOException("SFTP get failed: ${e.message}", e)
        }
    }

    override suspend fun getOutPutStream(append: Boolean): OutputStream {
        val mode = if (append) EnumSet.of(net.schmizz.sshj.sftp.OpenMode.APPEND) else EnumSet.of(
            net.schmizz.sshj.sftp.OpenMode.WRITE
        )
        try {
            return sftpClient?.open(remotePath, mode)?.RemoteFileOutputStream()
                ?: throw IOException("SFTP put failed: null channel")
        } catch (e: IOException) {
            Log.e("SftpFileObject", "put (OutputStream) failed for $remotePath: ${e.message}", e)
            throw IOException("SFTP put failed: ${e.message}", e)
        }
    }

    override suspend fun length(): Long = attrs?.size ?: 0L
    override suspend fun calcSize(): Long = withContext(Dispatchers.IO) {
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

    suspend fun lastModified(): Long =
        getSftpAttrs()?.mtime?.toLong()?.times(1000L) ?: 0L // sftp mTime is in seconds

    override suspend fun exists(): Boolean {
        if (attrsFetched?.not() == true) {
            Log.e("SFTPFileObject", "exists() called without prefetching attributes first!")
        }
        return attrs != null
    }

    override suspend fun createNewFile(): Boolean {
        if (exists()) return false
        return try {
            sftpClient?.open(remotePath, EnumSet.of(net.schmizz.sshj.sftp.OpenMode.CREAT))?.close()
            true
        } catch (e: Exception) {
            Log.e("SFTPFileObject", "createNewFile failed for $remotePath: ${e.message}", e)
            false
        }
    }

    override suspend fun getCanonicalPath(): String =
        absolutePath // SFTP paths are generally canonical


    override suspend fun mkdir(): Boolean {
        if (exists()) return false
        return try {
            sftpClient?.mkdir(remotePath)
            true
        } catch (e: IOException) {
            Log.e("SFTPFileObject", "mkdir failed for $remotePath: ${e.message}", e)
            false
        }
    }

    override suspend fun mkdirs(): Boolean {
        if (exists()) return isDirectory()
        return try {
            sftpClient?.mkdirs(remotePath)
            true
        } catch (e: IOException) {
            Log.e("SFTPFileObject", "mkdirs failed for $remotePath: ${e.message}", e)
            false
        }
    }

    suspend fun createFile(): Boolean = createNewFile() // Alias

    override suspend fun delete(): Boolean {
        if (!exists()) return false
        return try {
            if (isDirectory()) {
                sftpClient?.rmdir(remotePath)
            } else {
                sftpClient?.rm(remotePath)
            }
            true
        } catch (e: IOException) {
            Log.e("SFTPFileObject", "delete failed for $remotePath: ${e.message}", e)
            false
        }
    }

    override suspend fun toUri(): Uri {
        return Uri.parse("sftp://$username@$hostname:$port/$remotePath")
    }

    override suspend fun getMimeType(context: Context): String? {
        val currentName = getName()
        val extension = currentName.substringAfterLast('.', "")
        return if (extension.isNotEmpty()) {
            MimeTypeMap.getSingleton().getMimeTypeFromExtension(
                extension.lowercase(
                    Locale.ROOT
                )
            )
        } else {
            if (isDirectory()) "inode/directory" else null // Common for directories
        }
    }


    override suspend fun renameTo(string: String): Boolean {
        if (!exists()) return false
        val parentPath = remotePath.substringBeforeLast('/', "")
        val newFullPath = if (parentPath.isEmpty()) string else "$parentPath/$string"
        return try {
            sftpClient?.rename(remotePath, newFullPath)
            true
        } catch (e: IOException) {
            Log.e(
                "SFTPFileObject",
                "renameTo failed for $remotePath to $newFullPath: ${e.message}",
                e
            )
            false
        }
    }


    override suspend fun hasChild(name: String): Boolean {
        if (!isDirectory()) return false
        return listFiles().any { it.getName() == name }
    }

    override suspend fun createChild(createFile: Boolean, name: String): FileObject? {
        if (!isDirectory()) return null
        val childRemotePath =
            if (remotePath.endsWith("/")) remotePath + name else "$remotePath/$name"
        val childAbsolutePath =
            "sftp://$username@$hostname:$port/$childRemotePath"
        val childFile = SFTPFileObject(
            hostname,
            port,
            username,
            password,
            sshClient,
            sftpClient,
            childAbsolutePath
        )

        return try {
            if (createFile) {
                if (childFile.createNewFile()) childFile else null
            } else {
                if (childFile.mkdir()) childFile else null
            }
        } catch (e: Exception) {
            Log.e(
                "SFTPFileObject",
                "createChild ($name, file=$createFile) failed: ${e.message}",
                e
            )
            null
        }
    }

    suspend fun getParent(): FileObject? = getParentFile()

    suspend fun getUri(): Uri? = toUri()

    override fun canWrite(): Boolean {
        if (attrsFetched?.not() == true) {
            Log.e(
                "SFTPFileObject",
                "canWrite() called without prefetching attributes first! This can lead to incorrect results."
            )
        }
        val perms = attrs?.mode?.permissions ?: return false
        return perms.contains(FilePermission.USR_W) ||
                perms.contains(FilePermission.GRP_W) ||
                perms.contains(FilePermission.OTH_W)
    }

    override fun canRead(): Boolean {
        if (attrsFetched?.not() == true) {
            Log.e(
                "SFTPFileObject",
                "canRead() called without prefetching attributes first! This can lead to incorrect results."
            )
        }
        val perms = attrs?.mode?.permissions ?: return false
        return (
                perms.contains(FilePermission.USR_R)
                || perms.contains(FilePermission.GRP_R)
                || perms.contains(FilePermission.OTH_R)
               )
    }

    override fun canExecute(): Boolean {
    // 0b111 = (below)
    // 0b001 = others execute
    // 0b010 = group execute
    // 0b100 = owner execute
       return (attrs?.mode?.mask?.and(0b111)) != 0
    }

    override suspend fun getChildForName(name: String): FileObject {
        val childPath = if (remotePath.endsWith("/")) remotePath + name else "$remotePath/$name"
        val childAbsolutePath =
            "sftp://$username@$hostname:$port/$childPath"
        return SFTPFileObject(
            hostname,
            port,
            username,
            password,
            sshClient,
            sftpClient,
            childAbsolutePath
        )
    }


    override suspend fun readText(): String? = readText(Charsets.UTF_8)

    override suspend fun readText(charset: Charset): String? {
        if (!isFile()) return null
        return try {
            getInputStream().use { inputStream ->
                inputStream.reader(charset).use { reader ->
                    reader.readText()
                }
            }
        } catch (e: Exception) { // SftpException or IOException
            Log.e("SFTPFileObject", "readText failed for $remotePath", e)
            null
        }
    }

    override suspend fun writeText(text: String) {
        TODO("Not yet implemented")
    }

    override suspend fun writeText(content: String, charset: Charset): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                getOutPutStream(append = false).use { outputStream ->
                    outputStream.writer(charset).use { writer ->
                        writer.write(content)
                    }
                }
                true
            } catch (e: Exception) {
                Log.e("SFTPFileObject", "writeText failed for $remotePath", e)
                false
            }
        }
    }

    override fun isSymlink(): Boolean {
        Log.d(
            "SftpFileObject",
            "Checking if path is symlink: '$remotePath', attrsFetched=$attrsFetched"
        )
        if (attrsFetched?.not() == true) {
            Log.e("SFTPFileObject", "isSymlink() called without prefetching attributes first!")
        }

        return attrs?.type == net.schmizz.sshj.sftp.FileMode.Type.SYMLINK
    }

    suspend fun disconnect() {
        if (sftpClient !== null) {
            sftpClient?.close()
        }
        if (sshClient?.isConnected == true) {
            sshClient?.disconnect()
        }
        Log.d("SftpFileObject", "SFTP session to $hostname disconnected.")
    }
}