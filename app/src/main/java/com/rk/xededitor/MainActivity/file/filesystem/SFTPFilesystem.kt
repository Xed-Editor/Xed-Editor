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

    fun load(remotePath: String) {
        if (channel == null || !channel!!.isConnected) {
            rkUtils.toast("Error. Not connected!")
            return
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
            rkUtils.toast("Error: ${e.message}")
        }
    }

    fun save(file: File) {
        if (channel == null || !channel!!.isConnected) {
            rkUtils.toast("Error. Not connected!")
            return
        }
        try {
            if (file.isDirectory) {
                channel?.mkdir(getConfig(file.absolutePath, 2))
            } else {
                channel?.put(file.absolutePath, getConfig(file.absolutePath, 2))
            }
        } catch (e: Exception) {
            rkUtils.toast("Error: ${e.message}")
        }
    }
    
    fun disconnect() {
        channel?.disconnect()
        session?.disconnect()
        channel = null
        session = null
    }
    
    companion object {
        val configFormat = Regex("""^[^_]+_[^_]+_[^_]+_\d+$""")
        val sftpFormat = Regex("""/([^_]+_[^_]+_[^_]+_\d+)(/.*)?""")

        fun getConfig(path: String, value: Int): String {
            return sftpFormat.find(path)?.groupValues?.get(value) ?: ""
        }
    }
}

fun save(file: File) {
    if (channel == null || !channel!!.isConnected) {
        rkUtils.toast("Error. Not connected!")
        return
    }

    try {
        if (file.isDirectory) {
            // Создаем директорию на сервере
            val remoteDirPath = file.absolutePath.replace(context.filesDir.absolutePath, "").replace(File.separator, "/")
            kotlin.runCatching { channel!!.mkdir(remoteDirPath) }

            // Рекурсивно загружаем файлы внутри директории
            file.listFiles()?.forEach { childFile ->
                save(childFile)
            }
        } else {
            // Загружаем файл на сервер
            val remoteFilePath = file.absolutePath.replace(context.filesDir.absolutePath, "").replace(File.separator, "/")
            channel!!.put(file.absolutePath, remoteFilePath)
        }
    } catch (e: Exception) {
        rkUtils.toast("Error saving ${file.absolutePath}: ${e.message}")
    }
}