package com.rk.file.external

import android.content.Context
import android.net.Uri
import android.util.Log
import android.webkit.MimeTypeMap
import com.jcraft.jsch.ChannelSftp
import com.jcraft.jsch.JSch
import com.jcraft.jsch.JSchException
import com.jcraft.jsch.Logger
import com.jcraft.jsch.Logger.DEBUG
import com.jcraft.jsch.Logger.ERROR
import com.jcraft.jsch.Logger.FATAL
import com.jcraft.jsch.Logger.INFO
import com.jcraft.jsch.Logger.WARN
import com.jcraft.jsch.Session
import com.jcraft.jsch.SftpATTRS
import com.jcraft.jsch.SftpException
import com.rk.file.FileObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.FileNotFoundException
import java.io.IOException
import java.io.InputStream
import java.io.ObjectInputStream
import java.io.OutputStream
import java.nio.charset.Charset
import java.util.Locale
import java.util.Properties
import java.util.Vector

public class JsChSFTPLogger: Logger {

    var name: MutableMap<Int?, String?> = HashMap<Int?, String?>()

    init
    {
        name.put(DEBUG, "DEBUG: ");
        name.put(INFO, "INFO: ");
        name.put(WARN, "WARN: ");
        name.put(ERROR, "ERROR: ");
        name.put(FATAL, "FATAL: ");
    }

    override fun isEnabled(level: Int): Boolean {
        return true
    }

    override fun log(level: Int, message: String?) {
        Log.println(level, "JschSftpLogger", message!!)
    }

}

class SFTPFileObject(
    private val hostname: String,
    private val port: Int,
    private val username: String,
    private val password: String,
    @Transient
    private var session: Session,
    @Transient
    private var channelSftp: ChannelSftp,
    private val absolutePath: String,
    private val isRoot: Boolean = false,
) : FileObject {
//    variables, I love them, being at the top 🙃
    @Transient
    private var attrs: SftpATTRS? = null
    @Transient
    private var attrsFetched: Boolean = false
    @Transient
    private val childrenCache = mutableListOf<FileObject>()
    @Transient
    private var childrenFetched = false


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

    suspend fun connectOnDeserialization() {
            val session = createSession(this.hostname, this.port, this.username, this.password)
            if (session == null || !session.isConnected) {
                Log.e("SFTP_CONNECT", "Failed to create or connect session to ${this.hostname}")
                return;
            }

            val channelSftp = createChannel(session);

            if (channelSftp == null || !channelSftp.isConnected) {
                Log.e("SFTP_CONNECT", "Failed to create or connect SFTP channel to ${this.hostname}")
                session.disconnect() // Clean up session
                return;
            }

            this.session = session;
            this.channelSftp = channelSftp;

            prefetchAttributes()
            fetchChildren()


        // ?: Path is serializable & will last within the process of it.
        // OR maybe I should query it again, If the server has changed it. IDK (think, YOU TOO!).
    }

    companion object {
        fun createSession(
            hostname: String,
            port: Int,
            username: String,
            password: String?
        ): Session? {
            return try {
                JSch.setLogger(JsChSFTPLogger())

                val jsch = JSch()


                val session = jsch.getSession(username, hostname, port)

                password?.let { session.setPassword(it) }

                val config = Properties()

                config.setProperty("StrictHostKeyChecking", "no")

                session.setConfig(config)

                session.connect(30000)

                session
            } catch (e: JSchException) {
                Log.e("SFTPFileObject", "createSession: ${e.message}")
                null
            }

        }

        fun createChannel(session: Session): ChannelSftp? {
            return try {
                val channel = session.openChannel("sftp") as ChannelSftp
                channel.connect(5000)
                
                channel
            } catch (e: JSchException) {
                Log.e("SftpFileObject", "ChannelSftp creation failed: ${e.message}", e)
                session.disconnect()
                null
            }
        }
    }

    /**
     * Asynchronously fetches and caches the file attributes from the SFTP server.
     * This MUST be called from a background thread before accessing synchronous
     * methods like isFile(), isDirectory(), getName() if they need network data.
     */
    suspend fun prefetchAttributes() {
        if (attrsFetched) return // Don't fetch again if we already tried

        attrs = withContext(Dispatchers.IO) {
            try {
                Log.d("SftpFileObject", "Prefetching attributes for: '$remotePath'")
                if (remotePath.isNotEmpty()) {
                    channelSftp.lstat(remotePath)
                } else {
                    // Handle case where remotePath might be empty for root, JSch might expect "."
                    // Or if your logic now ensures root is "/", this branch may not be needed.
                    // Let's assume remotePath is now always a valid path like "/" or "file.txt"
                    // If remotePath can be empty, use "."
                    // That's all, Buy a better Chair & touch Grass 🙃
                    channelSftp.lstat(if (remotePath.isEmpty() && isRoot) "." else remotePath)
                }
            } catch (e: Exception) {
                Log.e("SftpFileObject", "lstat failed for $remotePath: ${e.message}", e)
                null
            }
        }
        attrsFetched = true
    }

   // @Throws(IOException::class, ClassNotFoundException::class)
   // private suspend fun readObject(input: ObjectInputStream) {
    //    input.defaultReadObject()   // Restores host, port, username, password, remotePath
        //// Reconnect after deserialization

     //   connectOnDeserialization();
   //     Log.d("SFTPFileObject", "Deserialized SFTPFileObject: $remotePath")
    //}

    override fun getName(): String {
        if (isRoot) {
            val userInfo = session.userName
            return "$userInfo@${session.host}"
        }
        return remotePath.substringAfterLast('/', if (remotePath.contains('/')) "" else remotePath)
    }

    override fun getAbsolutePath(): String {
        return absolutePath
    }

    override fun getParentFile(): FileObject? {
        if (isRoot || remotePath.isEmpty() || remotePath == "/") {
            return null // Root has no parent in this context
        }
        val parentPathString = remotePath.substringBeforeLast('/', "")
        val parentAbsolutePath = "sftp://${session.userName}@${session.host}:${session.port}/${parentPathString.removePrefix("/")}"
        return SFTPFileObject(hostname, port, username, password, session, channelSftp, parentAbsolutePath)
    }

    private fun getSftpAttrs(): SftpATTRS? {
        Log.d("SftpFileObject", "Attempting lstat for path: '$remotePath'")
        return try {
            channelSftp.lstat(remotePath)
        } catch (e: SftpException) {
            if (e.id == ChannelSftp.SSH_FX_NO_SUCH_FILE) {
                // File or directory does not exist
            } else {
                Log.e("SftpFileObject", "lstat failed for $remotePath: ${e.message}", e);
            }
            null
        } catch (e: Exception) {
            Log.e("SftpFileObject", "SftpException during lstat for $remotePath", e);
            null
        }
    }

    override fun isFile(): Boolean {
        Log.d("SftpFileObject", "Checking if path is file: '$remotePath'")
        if (!attrsFetched) {
            Log.e("SFTPFileObject", "isFile() called without prefetching attributes first! This can lead to incorrect results.")
        }

        return attrs?.isReg ?: false
    }

    override fun isDirectory(): Boolean {
        if (!attrsFetched) {
            Log.e("SFTPFileObject", "isDirectory() called without prefetching attributes first!")
        }

        return attrs?.isDir ?: false
    }

//    @Suppress("UNCHECKED_CAST")
//    override fun listFiles(): List<FileObject> {
//        if (!isDirectory()) return emptyList()
//        val files = mutableListOf<SFTPFileObject>()
//        try {
//            val entries = channelSftp.ls(remotePath) as Vector<ChannelSftp.LsEntry>
//            for (entry in entries) {
//                val entryName = entry.filename
//                if (entryName == "." || entryName == "..") continue
//
//                val childPath = if (remotePath.endsWith("/")) remotePath + entryName else "$remotePath/$entryName"
//                val childAbsolutePath = "sftp://${session.userName}@${session.host}:${session.port}/$childPath"
//                files.add(SFTPFileObject(session, channelSftp, childAbsolutePath))
//            }
//        } catch (e: SftpException) {
//            Log.e("SftpFileObject", "ls failed for $remotePath: ${e.message}", e)
//        }
//        return files
//    }

    override fun listFiles(): List<FileObject> {
        Log.d("SftpFileObject", "Listing files for path: '$remotePath', childrenFetched=$childrenFetched (using cache/pre-fetched? $childrenFetched), cached children size: ${childrenCache.size}")
        if (!childrenFetched) {
            Log.w("SFTPFileObject", "listFiles() called without pre-fetching children. Returning empty list.")
        }
        return childrenCache
    }

    suspend fun fetchChildren(): List<FileObject> {
        if (childrenFetched) return childrenCache

        val result = withContext(Dispatchers.IO) {
            val files = mutableListOf<SFTPFileObject>()
            if (isDirectory()) { // Uses the cached 'attrs'
                try {
                    @Suppress("UNCHECKED_CAST")
                    val entries = channelSftp.ls(remotePath) as Vector<ChannelSftp.LsEntry>
                    for (entry in entries) {
                        val entryName = entry.filename
                        if (entryName == "." || entryName == "..") continue

                        val childPath = if (remotePath.endsWith("/")) remotePath + entryName else "$remotePath/$entryName"
                        val childAbsolutePath =
                            "sftp://${session.userName}@${session.host}:${session.port}${if (childPath.startsWith("/")) childPath else "/$childPath" }"

                        files.add(SFTPFileObject(hostname, port, username, password, session, channelSftp, childAbsolutePath))
                    }
                } catch (e: Exception) {
                    // handle exception, Am I supposed?? I guess, That's for the TO-DO upside down
                    null
                }
            }
            files
        }

        // Pre-fetch attributes for all children in parallel for efficiency
        coroutineScope {
            result.forEach { child ->
                launch { child.prefetchAttributes() }
            }
        }

        childrenCache.addAll(result)
        childrenFetched = true
        return childrenCache
    }

    override  fun getInputStream(): InputStream {
        if (!isFile()) throw FileNotFoundException("Not a file or does not exist: $remotePath")
        try {
            return channelSftp.get(remotePath)
        } catch (e: SftpException) {
            Log.e("SftpFileObject", "get (InputStream) failed for $remotePath: ${e.message}", e)
            throw IOException("SFTP get failed: ${e.message}", e)
        }
    }

    override fun getOutPutStream(append: Boolean): OutputStream {
        // JSch's put method can take an OutputStream, or you can write to a local temp file first.
        // For direct streaming, ensure channelSftp.put() is used correctly.
        // This example shows writing (overwrite or append).
        val mode = if (append) ChannelSftp.APPEND else ChannelSftp.OVERWRITE
        try {
            return channelSftp.put(remotePath, mode)
        } catch (e: SftpException) {
            Log.e("SftpFileObject", "put (OutputStream) failed for $remotePath: ${e.message}", e)
            throw IOException("SFTP put failed: ${e.message}", e)
        }
    }

    override fun length(): Long = attrs?.size ?: 0L

    fun lastModified(): Long = getSftpAttrs()?.mTime?.toLong()?.times(1000L) ?: 0L // sftp mTime is in seconds

    // Other methods like length(), canWrite(), etc., would also use the cached 'attrs'
    // just so I don't forget to modify other Methods 🙃
    override fun exists(): Boolean {
        if (!attrsFetched) {
            Log.e("SFTPFileObject", "exists() called without prefetching attributes first!")
        }
        // If we successfully fetched and got a result, it exists.
        return attrs != null
    }

    override fun createNewFile(): Boolean {
        if (exists()) return false
        return try {
            // Create an empty file by opening an output stream and immediately closing it.
            channelSftp.put(remotePath, ChannelSftp.OVERWRITE).close()
            true
        } catch (e: Exception) { // SftpException or IOException
            Log.e("JschSftpFileObject", "createNewFile failed for $remotePath: ${e.message}", e)
            false
        }
    }

    override fun getCanonicalPath(): String = absolutePath // SFTP paths are generally canonical


    override fun mkdir(): Boolean {
        if (exists()) return false
        return try {
            channelSftp.mkdir(remotePath)
            true
        } catch (e: SftpException) {
            Log.e("JschSftpFileObject", "mkdir failed for $remotePath: ${e.message}", e)
            false
        }
    }

    override  fun mkdirs(): Boolean {
        if (exists()) return isDirectory()
        // JSch doesn't have a direct mkdirs. You might need to create parent directories iteratively.
        // For simplicity, this is not implemented here. You can use channelSftp.cd and mkdir.
        Log.w("JschSftpFileObject", "mkdirs() not fully implemented. Will attempt single mkdir.")
        return mkdir() //
    }

    override fun writeText(text: String) {
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
                Log.e("JschSftpFileObject", "writeText failed for $remotePath", e)
                false
            }
        }
    }


    fun createFile(): Boolean = createNewFile() // Alias

    override  fun delete(): Boolean {
        if (!exists()) return false
        return try {
            if (isDirectory()) {
                channelSftp.rmdir(remotePath)
            } else {
                channelSftp.rm(remotePath)
            }
            true
        } catch (e: SftpException) {
            Log.e("JschSftpFileObject", "delete failed for $remotePath: ${e.message}", e)
            false
        }
    }

    override fun toUri(): Uri {
        // sftp://username@host:port/path/to/file
        return Uri.parse("sftp://${session.userName}@${session.host}:${session.port}/$remotePath")
    }

    override  fun getMimeType(context: Context): String? {
        val currentName = getName()
        val extension = currentName.substringAfterLast('.', "")
        return if (extension.isNotEmpty()) {
            MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension.lowercase(
                Locale.ROOT))
        } else {
            if (isDirectory()) "inode/directory" else null // Common for directories
        }
    }


    override fun renameTo(string: String): Boolean {
        // newNameFull here is expected to be just the new name, not the full path
        // For JSch, rename needs the old path and the new full path.
        if (!exists()) return false
        val parentPath = remotePath.substringBeforeLast('/', "")
        val newFullPath = if (parentPath.isEmpty()) string else "$parentPath/$string"
        return try {
            channelSftp.rename(remotePath, newFullPath)
            // If successful, you might want to update the internal remotePath of this object,
            // but FileObject API typically implies the original object is now invalid
            // and a new one should be fetched/created for the new name.
            true
        } catch (e: SftpException) {
            Log.e("JschSftpFileObject", "renameTo failed for $remotePath to $newFullPath: ${e.message}", e)
            false
        }
    }


    override  fun hasChild(name: String): Boolean {
        if (!isDirectory()) return false
        return listFiles().any { it.getName() == name }
    }

    override  fun createChild(createFile: Boolean, name: String): FileObject? {
        if (!isDirectory()) return null
        val childRemotePath = if (remotePath.endsWith("/")) remotePath + name else "$remotePath/$name"
        val childAbsolutePath = "sftp://${session.userName}@${session.host}:${session.port}/$childRemotePath"
        val childFile = SFTPFileObject(hostname, port, username, password, session, channelSftp, childAbsolutePath)

        return try {
            if (createFile) {
                if (childFile.createNewFile()) childFile else null
            } else {
                if (childFile.mkdir()) childFile else null
            }
        } catch (e: Exception) {
            Log.e("JschSftpFileObject", "createChild ($name, file=$createFile) failed: ${e.message}", e)
            null
        }
    }

    fun getParent(): FileObject? = getParentFile()

    fun getUri(): Uri? = toUri()

    override fun canRead(): Boolean {
        // JSch doesn't have a direct "canRead" like java.io.File.
        // Existence and permissions from SftpATTRS can give an idea.
        // For simplicity, if it exists, assume readable.
        // More precise check would involve trying to open for read or checking attrs.permissions.
        return exists()
    }

    override fun getChildForName(name: String): FileObject {
        val childPath = if (remotePath.endsWith("/")) remotePath + name else "$remotePath/$name"
        val childAbsolutePath = "sftp://${session.userName}@${session.host}:${session.port}/$childPath"
        return SFTPFileObject(hostname, port, username, password, session, channelSftp, childAbsolutePath)
    }


    override  fun readText(): String? = readText(Charsets.UTF_8)

    override  fun readText(charset: Charset): String? {
        if (!isFile()) return null
        return try {
            getInputStream().use { inputStream ->
                inputStream.reader(charset).use { reader ->
                    reader.readText()
                }
            }
        } catch (e: Exception) { // SftpException or IOException
            Log.e("JschSftpFileObject", "readText failed for $remotePath", e)
            null
        }
    }


//    override fun isSymlink(): Boolean = getSftpAttrs()?.isLink ?: false

    override fun isSymlink(): Boolean {
        Log.d("SftpFileObject", "Checking if path is symlink: '$remotePath', attrsFetched=$attrsFetched")
        if (!attrsFetched) {
            Log.e("SFTPFileObject", "isSymlink() called without prefetching attributes first!")
        }

        return attrs?.isLink ?: false
    }

    override fun canWrite(): Boolean {
        // Similar to canRead, a precise check is complex.
        // Checking SftpATTRS permissions is better.
        // For now, assume writable if it exists and isn't a directory (or handle dir writes via mkdir).
        // This is a simplification.
        return exists() // A more robust check would look at sftpAttrs.getPermissions()
    }

    // Call this when the FileObject is no longer needed, especially for the root object
    // to close the session and channel.
    fun disconnect() {
        if (channelSftp.isConnected) {
            channelSftp.disconnect()
        }
        if (session.isConnected) {
            session.disconnect()
        }
        Log.d("SftpFileObject", "SFTP session to ${session.host} disconnected.")
    }
}
