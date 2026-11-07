package com.rk.terminal

import android.net.LocalServerSocket
import android.net.LocalSocket
import com.rk.utils.FilePathUtils
import com.rk.utils.logError
import com.rk.utils.terminalToHostFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

object XedCliServer {
    private var localServerSocket: LocalServerSocket? = null
    private var socketJob: Job? = null
    private var isRunning = false

    fun start() {
        if (isRunning) return
        isRunning = true
        socketJob =
            GlobalScope.launch(Dispatchers.IO) {
                try {
                    val server = LocalServerSocket("xed_socket")
                    localServerSocket = server
                    while (isRunning) {
                        val clientSocket =
                            try {
                                server.accept()
                            } catch (_: Exception) {
                                break
                            }
                        launch(Dispatchers.IO) { handleClientSocket(clientSocket) }
                    }
                } catch (e: Exception) {
                    logError(e)
                }
            }
    }

    fun stop() {
        isRunning = false
        runCatching { localServerSocket?.close() }
        localServerSocket = null
        socketJob?.cancel()
        socketJob = null
    }

    private suspend fun handleClientSocket(socket: LocalSocket) {
        try {
            val bytes = socket.inputStream.use { it.readBytes() }
            if (bytes.isEmpty()) return

            val strings = bytes.splitByNullByte()
            if (strings.isEmpty()) return

            val cwd = strings[0]
            val arguments = strings.drop(1)

            val hostCwd = terminalToHostFile(cwd)
            val targetArgs = arguments.ifEmpty { listOf(".") }

            withContext(Dispatchers.Main) {
                targetArgs.forEach { arg ->
                    FilePathUtils.parseFilePath(arg, hostCwd)?.let { FilePathUtils.openPathMatch(it) }
                }
            }
        } catch (e: Exception) {
            logError(e)
        }
    }

    private fun ByteArray.splitByNullByte(): List<String> = buildList {
        var start = 0

        for (i in this@splitByNullByte.indices) {
            if (this@splitByNullByte[i] == 0.toByte()) {
                add(
                    String(
                        this@splitByNullByte,
                        start,
                        i - start,
                        Charsets.UTF_8,
                    )
                )
                start = i + 1
            }
        }

        if (start < this@splitByNullByte.size) {
            add(
                String(
                    this@splitByNullByte,
                    start,
                    this@splitByNullByte.size - start,
                    Charsets.UTF_8,
                )
            )
        }
    }
}
