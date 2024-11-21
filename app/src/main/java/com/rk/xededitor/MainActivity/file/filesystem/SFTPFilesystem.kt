package com.rk.xededitor.MainActivity.file.filesystem

import android.content.Context
import com.jcraft.jsch.ChannelSftp
import com.jcraft.jsch.JSch
import com.jcraft.jsch.Session
import com.rk.xededitor.rkUtils
import java.io.File

class SFTPFilesystem(private val context: Context, private val connectionString: String) {
    
    private val jsch = JSch()
    private var session: Session? = null
    private var channel: ChannelSftp? = null
    
    init {
        val parts = connectionString.split("@", ":", limit = 4)
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
        
        val tempDir = File(File(context.filesDir,"sftp"), connectionString.replace(":", "_").replace("@", "_") + remotePath).apply {
            if (!exists()) {
                mkdirs()
            }
        }
        
        try {
            createFS(remotePath, tempDir)
        } catch (e: Exception) {
            rkUtils.toast("Error cloning filesystem: ${e.message}")
        }
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
    
    private fun createFS(remotePath: String, parent: File) {
        val files = channel?.ls(remotePath) as? List<ChannelSftp.LsEntry>
        files?.forEach { entry ->
            kotlin.runCatching {
                // Skip . and .. directories
                if (entry.filename == "." || entry.filename == "..") return@forEach
                val localFile = File(parent, entry.filename)
                if (entry.attrs.isDir) {
                    if (!localFile.exists()) {
                        localFile.mkdirs()
                    }
                    createFS("$remotePath/${entry.filename}", localFile)
                } else {
                    if (!localFile.exists()) {
                        localFile.createNewFile()
                    }
                }
            }
        }
    }
}