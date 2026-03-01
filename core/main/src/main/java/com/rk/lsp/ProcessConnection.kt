package com.rk.lsp

import android.util.Log
import com.rk.exec.ubuntuProcess
import java.io.InputStream
import java.io.OutputStream
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.eclipse.lsp4j.MessageType

class ProcessConnection(private val cmd: Array<String>, instance: BaseLspServerInstance) :
    BaseLspConnectionProvider(instance) {

    private var process: Process? = null
    private var loggingInput: InputStream? = null
    private var loggingOutput: OutputStream? = null

    private var scope: CoroutineScope? = null

    override val inputStream: InputStream
        get() = loggingInput ?: throw IllegalStateException("Process not running")

    override val outputStream: OutputStream
        get() = loggingOutput ?: throw IllegalStateException("Process not running")

    override val isClosed: Boolean
        get() = process == null || process?.isAlive == false

    override fun start() {
        if (process != null) return
        scope = CoroutineScope(Dispatchers.IO)
        runBlocking { process = ubuntuProcess(command = cmd) }

        loggingInput =
            LoggingInputStream(process!!.inputStream) { json ->
                Log.d("ProcessConnection", "[stdout] $json")
                instance.addLog(LspLogEntry(MessageType.Log, "→ $json"))
            }
        loggingOutput =
            LoggingOutputStream(process!!.outputStream) { json ->
                Log.d("ProcessConnection", "[stdin] $json")
                instance.addLog(LspLogEntry(MessageType.Log, "← $json"))
            }

        scope!!.launch {
            runCatching {
                process!!.errorStream.bufferedReader().forEachLine { line ->
                    Log.e("ProcessConnection", "[stderr] $line")
                    instance.addLog(LspLogEntry(MessageType.Error, line))
                }
            }
        }
    }

    override fun close() {
        scope?.cancel()
        scope = null
        process?.destroy()
        process = null
        loggingInput = null
        loggingOutput = null
    }
}
