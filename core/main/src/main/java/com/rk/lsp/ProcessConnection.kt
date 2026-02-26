package com.rk.lsp

import android.util.Log
import com.rk.exec.readStderr
import com.rk.exec.ubuntuProcess
import com.rk.utils.toast
import com.rk.xededitor.BuildConfig
import io.github.rosemoe.sora.lsp.client.connection.StreamConnectionProvider
import java.io.InputStream
import java.io.OutputStream
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.runBlocking

class ProcessConnection(private val cmd: Array<String>) : StreamConnectionProvider {
    private var process: Process? = null

    override val inputStream: InputStream
        get() = process?.inputStream ?: throw IllegalStateException("Process not running")

    override val outputStream: OutputStream
        get() = process?.outputStream ?: throw IllegalStateException("Process not running")

    override val isClosed: Boolean
        get() = process == null || process?.isAlive == false

    override fun start() {
        if (process != null) return
        runBlocking {
            process = ubuntuProcess(command = cmd)

            if (BuildConfig.DEBUG && process?.waitFor(110, TimeUnit.MILLISECONDS) == true) {
                val exitCode = process?.exitValue() ?: -1
                if (exitCode != 0) {
                    val stderr = process?.readStderr().orEmpty()
                    Log.e(this@ProcessConnection::class.java.simpleName, stderr)
                    toast(stderr)
                }
            }
        }
    }

    override fun close() {
        runBlocking { Log.e(this@ProcessConnection::class.java.simpleName, process?.readStderr().toString()) }

        process?.destroy()
        process = null
    }
}
