package com.rk.lsp

import android.util.Log
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.Socket
import org.eclipse.lsp4j.MessageType

class SocketConnection(private val port: Int, private val host: String? = null, instance: LspServerInstance) :
    BaseLspConnectionProvider(instance) {

    private var socket: Socket? = null
    private var loggingInput: InputStream? = null
    private var loggingOutput: OutputStream? = null

    override val inputStream: InputStream
        get() = loggingInput ?: throw IllegalStateException("Socket not running")

    override val outputStream: OutputStream
        get() = loggingOutput ?: throw IllegalStateException("Socket not running")

    override val isClosed: Boolean
        get() = socket == null || socket?.isClosed == true

    @Throws(IOException::class)
    override fun start() {
        if (socket != null) return
        val s = Socket()
        socket = s

        s.connect(InetSocketAddress(host ?: "localhost", port), 20)
        s.soTimeout = 0

        loggingInput =
            LoggingInputStream(s.getInputStream()) { json ->
                Log.d("SocketConnection", "[stdout] $json")
                instance.addLog(LspLogEntry(MessageType.Log, "→ $json"))
            }
        loggingOutput =
            LoggingOutputStream(s.getOutputStream()) { json ->
                Log.d("SocketConnection", "[stdin] $json")
                instance.addLog(LspLogEntry(MessageType.Log, "← $json"))
            }
    }

    override fun close() {
        runCatching { loggingInput?.close() }
        runCatching { loggingOutput?.close() }
        runCatching { socket?.close() }
        socket = null
        loggingInput = null
        loggingOutput = null
    }
}
