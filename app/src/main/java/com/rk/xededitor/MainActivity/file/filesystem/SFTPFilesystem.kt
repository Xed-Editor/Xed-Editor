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

    fun open(remotePath: String) {
        if (channel == null || !channel!!.isConnected) {
            rkUtils.toast("Error")
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
                if (entry.attrs.isDir) {
                    if (!localFile.exists()) {
                        localFile.mkdirs()
                    }
                } else {
                    channel?.get(remoteFilePath, localFile.absolutePath)
                }
            }
        } catch (e: Exception) {
            rkUtils.toast("Error while opening ${remotePath}")
        }
    }

    fun disconnect() {
        channel?.disconnect()
        session?.disconnect()
        channel = null
        session = null
    }
}