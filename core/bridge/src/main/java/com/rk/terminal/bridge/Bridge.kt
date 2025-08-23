package com.rk.terminal.bridge

import android.net.LocalServerSocket
import android.net.LocalSocket
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
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

    suspend fun startServer(handler: suspend (String) -> String): Unit = withContext(Dispatchers.IO) {
        try {
            if (isRunning){
                return@withContext
            }
            serverSocket = LocalServerSocket("bridge")
            isRunning = true
            println("Server started")
            listenForClients(handler)
        }catch (e: Exception){
            delay(1500)
            println("Server failed to start trying again... ${e.message}")
            startServer(handler)
        }

    }

    private suspend fun listenForClients(handler: suspend (String) -> String) = withContext(Dispatchers.IO) {
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

    private suspend fun handleClient(clientSocket: LocalSocket, handler: suspend (String) -> String) {
        withContext(Dispatchers.IO){
            try {
                BufferedReader(InputStreamReader(clientSocket.inputStream)).use { input ->
                    BufferedWriter(OutputStreamWriter(clientSocket.outputStream)).use { output ->
                        var line: String?
                        while (input.readLine().also { line = it } != null && isRunning && clientSocket.isConnected) {
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
                try { clientSocket.close() } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    fun close(){
        try {
            serverSocket?.close()
            // Close all active client sockets
            synchronized(clientSockets) {
                for (socket in clientSockets) {
                    try { socket.close() } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
                clientSockets.clear()
            }
            isRunning = false
        } catch (e: Exception) {
            e.printStackTrace()
        }
        serverSocket = null


        println("Bridge cleaned up")
    }
}
