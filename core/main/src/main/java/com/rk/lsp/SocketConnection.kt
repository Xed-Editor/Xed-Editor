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
        socket = Socket()

        socket!!.connect(InetSocketAddress(host ?: "localhost", port), 20)
        socket!!.soTimeout = 0

        loggingInput =
            LoggingInputStream(socket!!.getInputStream()) { json ->
                Log.d("SocketConnection", "[stdout] $json")
                if (com.rk.settings.Preference.getBoolean("debug_mode", com.rk.xededitor.BuildConfig.DEBUG)) {
                    instance.addLog(LspLogEntry(MessageSource.RPC, null, "→ $json"))
                }
            }
        loggingOutput =
            LoggingOutputStream(socket!!.getOutputStream()) { json ->
                Log.d("SocketConnection", "[stdin] $json")
                if (com.rk.settings.Preference.getBoolean("debug_mode", com.rk.xededitor.BuildConfig.DEBUG)) {
                    instance.addLog(LspLogEntry(MessageSource.RPC, null, "← $json"))
                }
            }
    }

    override fun close() {
        runCatching { socket?.close() }
        socket = null
        loggingInput = null
        loggingOutput = null
    }
}
