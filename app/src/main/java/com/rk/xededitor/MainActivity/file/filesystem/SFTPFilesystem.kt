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
        if (session == null) {
            val parts = connectionString.split("@", ":", limit = 4)
            session = jsch.getSession(parts[0], parts[2], parts[3].toInt()).apply {
                setPassword(parts[1])
                setConfig("StrictHostKeyChecking", "no")
            }
        }
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
            rkUtils.toast("Error while connecting: ${e.message}")
        }
    }

    fun load(remotePath: String) {
        if (channel == null || !channel!!.isConnected || session == null || !session!!.isConnected) {
            rkUtils.toast("Error. Not connected! Reconnecting...")
            connect()
        }
        try {
            if (channel!!.stat(remotePath).isDir) {
                val tempDir = File(File(context.filesDir,"sftp"), connectionString.replace(":", "_").replace("@", "_") + remotePath).apply {
                    if (!exists()) {
                        mkdirs()
                    }
                }
                val files = channel?.ls(remotePath) as? List<ChannelSftp.LsEntry>
                files?.forEach { entry ->
                    kotlin.runCatching {
                        // Skip . and .. directories
                        if (entry.filename == "." || entry.filename == "..") return@forEach
                        val localFile = File(tempDir, entry.filename)
                        if (entry.attrs.isDir) {
                            if (!localFile.exists()) {
                                localFile.mkdirs()
                            }
                        } else {
                            if (!localFile.exists()) {
                                localFile.createNewFile()
                            }
                        }
                    }
                }
            } else {
                channel?.get(remotePath, File(File(context.filesDir,"sftp"), connectionString.replace(":", "_").replace("@", "_") + remotePath).absolutePath)
            }
        } catch (e: Exception) {
            rkUtils.toast("Error while loading: ${e.message}")
        }
    }

    fun save(file: File) {
        if (channel == null || !channel!!.isConnected || session == null || !session!!.isConnected) {
            rkUtils.toast("Error. Not connected! Reconnecting...")
            connect()
        }
        try {
            if (file.isDirectory) {
                channel?.mkdir(getConfig(file.absolutePath, 2))
            } else {
                channel?.put(file.absolutePath, getConfig(file.absolutePath, 2))
            }
        } catch (e: Exception) {
            rkUtils.toast("Error while saving: ${e.message}")
        }
    }

    fun delete(file: File) {
        if (channel == null || !channel!!.isConnected || session == null || !session!!.isConnected) {
            rkUtils.toast("Error. Not connected! Reconnecting...")
            connect()
        }
        try {
            val remotePath = getConfig(file.absolutePath, 2)
            rkUtils.toast(remotePath)
            if (file.isDirectory) {
                val files = channel?.ls(remotePath) as? List<ChannelSftp.LsEntry>
                files?.forEach { entry ->
                    rkUtils.toast(File(file, entry.filename).absolutePath)
                    kotlin.runCatching {
                        // Skip . and .. directories
                        if (entry.filename == "." || entry.filename == "..") return@forEach
                        delete(File(file, entry.filename))
                    }
                }
                channel?.rmdir(remotePath)
            } else {
                channel?.rm(remotePath)
            }
        } catch (e: Exception) {
            rkUtils.toast("Error while deleting: ${e.message}")
        }
    }

    fun disconnect() {
        channel?.disconnect()
        session?.disconnect()
        channel = null
        session = null
    }

    fun clearTempFiles() {
        File(File(context.filesDir,"sftp"), connectionString.replace(":", "_").replace("@", "_")).apply {
            if (exists()) {
                deleteRecursively()
            }
        }
    }
    
    companion object {
        val configFormat = Regex("""^[^_]+_[^_]+_[^_]+_\d+$""")
        val sftpFormat = Regex("""/([^_]+_[^_]+_[^_]+_\d+)(/.*)?""")

        fun getConfig(path: String, value: Int): String {
            return sftpFormat.find(path)?.groupValues?.get(value) ?: ""
        }
    }
}