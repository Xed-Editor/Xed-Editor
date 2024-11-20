package com.rk.xededitor.MainActivity.file.filesystem

import com.jcraft.jsch.*
import com.rk.xededitor.rkUtils
import java.io.File
import android.content.Context

class SFTPFilesystem(private val context: Context, private val connectionString: String) {

    private val jsch = JSch()
    private var session: Session? = null
    private var channel: ChannelSftp? = null
    var tempDir: File? = null

    init {
        val parts = connectionString.split("@", ":", "/", limit = 5)
        session = jsch.getSession(parts[0], parts[2], parts[3].toInt()).apply {
            setPassword(parts[1])
            setConfig("StrictHostKeyChecking", "no")
        }
    }

    fun connect() {
        try {
            session?.connect(5000)
            if (session?.isConnected == true) {
                channel = session?.openChannel("sftp") as ChannelSftp
                channel?.connect()
            } else {
                rkUtils.toast("Error. Check your connection data")
            }
        } catch (e: Exception) {
            disconnect()
            rkUtils.toast("Error: ${e.message}")
        }
    }

    fun openFolder(remotePath: String) {
        if (channel == null || !channel!!.isConnected) {
            rkUtils.toast("Error. Not connected!")
            return
        }
        tempDir = File(context.filesDir, connectionString).apply {
            if (!exists()) {
                mkdirs()
            }
        }
        try {
            val files = channel?.ls(remotePath) as? List<ChannelSftp.LsEntry>
            files?.forEach { entry ->
                val remoteFilePath = "$remotePath/${entry.filename}"
                val localFile = File(tempDir, entry.filename)
                if (!localFile.exists()) {
                    if (entry.attrs.isDir) {
                        localFile.mkdirs()
                    } else {
                        localFile.createNewFile()
                    }
                }
            }
        } catch (e: Exception) {
            rkUtils.toast("${remotePath} not found")
        }
    }

    fun clearTemp() {
        tempDir?.delete()
        tempDir = null
    }

    fun disconnect() {
        channel?.disconnect()
        session?.disconnect()
        channel = null
        session = null
    }

    companion object {
        val configFormat = Regex("""^[^:@]+:[^:@]+@[^:@]+:\d+$""")
        val sftpFormat = Regex("""/([^/]+:[^/]+@[^/]+:\d+)/""")

        fun getConfig(file: File): String {
            return sftpFormat.find(file.absolutePath)?.groupValues?.get(1) ?: ""
        }
    }
}