package com.rk.terminal.bridge

import android.net.LocalServerSocket
import android.net.LocalSocket
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.util.Collections

object Bridge {
    private var serverSocket: LocalServerSocket? = null
    private val clientSockets = Collections.synchronizedSet(mutableSetOf<LocalSocket>())
    @Volatile private var isRunning = false

    object RESULT {
        const val OK = "ok"
        const val ERR = "err"
    }

    suspend fun startServer(handler: (String) -> String) = withContext(Dispatchers.IO) {
        if (isRunning){
            return@withContext
        }
        serverSocket = LocalServerSocket("bridge")
        isRunning = true
        println("Server started")
        listenForClients(handler)
    }

    private suspend fun listenForClients(handler: (String) -> String) = withContext(Dispatchers.IO) {
        val server = serverSocket ?: return@withContext
        while (isRunning) {
            try {
                val clientSocket = server.accept()
                if (!isRunning) {
                    clientSocket.close()
                    break
                }
                clientSockets.add(clientSocket)
                println("Client connected")

                // Handle client in a new coroutine/thread if needed for concurrency
                handleClient(clientSocket, handler)
            } catch (e: Exception) {
                if (!isRunning) break
                e.printStackTrace()
            }
        }
    }

    private fun handleClient(clientSocket: LocalSocket, handler: (String) -> String) {
        Thread {
            try {
                BufferedReader(InputStreamReader(clientSocket.inputStream)).use { input ->
                    BufferedWriter(OutputStreamWriter(clientSocket.outputStream)).use { output ->
                        var line: String?
                        while (input.readLine().also { line = it } != null && isRunning) {
                            println("Received from client: $line")
                            val result = handler(line!!)
                            output.write(result)
                            output.newLine()
                            output.flush()
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                clientSockets.remove(clientSocket)
                try { clientSocket.close() } catch (_: Exception) {}
            }
        }.start()
    }

    suspend fun cleanup() = withContext(Dispatchers.IO) {
        isRunning = false
        try {
            serverSocket?.close()
        } catch (_: Exception) {}
        serverSocket = null

        // Close all active client sockets
        synchronized(clientSockets) {
            for (socket in clientSockets) {
                try { socket.close() } catch (_: Exception) {}
            }
            clientSockets.clear()
        }
        println("Bridge cleaned up")
    }
}
