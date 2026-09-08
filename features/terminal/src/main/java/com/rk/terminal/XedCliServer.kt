package com.rk.terminal

import android.content.Context
import android.net.LocalServerSocket
import android.net.LocalSocket
import com.rk.activities.main.MainActivity
import com.rk.file.FileWrapper
import com.rk.utils.logError
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

object XedCliServer {
    private var localServerSocket: LocalServerSocket? = null
    private var socketJob: Job? = null
    private var isRunning = false

    fun start(context: Context) {
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
                            } catch (e: Exception) {
                                break
                            }
                        launch(Dispatchers.IO) {
                            handleClientSocket(context, clientSocket)
                        }
                    }
                } catch (e: Exception) {
                    logError(e)
                }
            }
    }

    fun stop() {
        isRunning = false
        runCatching {
            localServerSocket?.close()
        }
        localServerSocket = null
        socketJob?.cancel()
        socketJob = null
    }

    private suspend fun handleClientSocket(context: Context, socket: LocalSocket) {
        try {
            val inputStream = socket.inputStream
            val bytes = inputStream.readBytes()
            try {
                socket.close()
            } catch (e: Exception) {
                // ignore
            }

            if (bytes.isEmpty()) return

            val strings = mutableListOf<String>()
            var start = 0
            for (i in bytes.indices) {
                if (bytes[i] == 0.toByte()) {
                    if (i > start) {
                        strings.add(String(bytes, start, i - start, Charsets.UTF_8))
                    } else {
                        strings.add("")
                    }
                    start = i + 1
                }
            }
            if (start < bytes.size) {
                strings.add(String(bytes, start, bytes.size - start, Charsets.UTF_8))
            }

            if (strings.isEmpty()) return
            val cwd = strings[0]
            val arguments = strings.drop(1)

            if (arguments.isEmpty()) {
                return
            }

            val viewModel = MainActivity.instance?.viewModel
            if (viewModel != null) {
                withContext(Dispatchers.Main) {
                    arguments.forEach { arg ->
                        var file = File(arg)
                        if (!file.isAbsolute) {
                            file = File(cwd, arg)
                        }
                        viewModel.editorManager.openFile(
                            FileWrapper(file),
                            projectRoot = null,
                            switchToTab = true,
                        )
                    }
                }
            }
        } catch (e: Exception) {
            logError(e)
        }
    }
}
