package com.rk.lsp

import android.util.Log
import com.rk.utils.errorDialog
import com.rk.exec.newSandbox
import com.rk.exec.readStderr
import com.rk.xededitor.BuildConfig
import io.github.rosemoe.sora.lsp.client.connection.StreamConnectionProvider
import kotlinx.coroutines.runBlocking
import java.io.InputStream
import java.io.OutputStream
import java.util.concurrent.TimeUnit

class ProcessConnection(
    private val cmd: Array<String>,
) : StreamConnectionProvider {

    private var process: Process? = null

    override val inputStream: InputStream
        get() = process?.inputStream
            ?: throw IllegalStateException("Process not started")

    override val outputStream: OutputStream
        get() = process?.outputStream
            ?: throw IllegalStateException("Process not started")

    override fun start() {
        if (process != null) return
        runBlocking{
            process = newSandbox(command = cmd)

            if (BuildConfig.DEBUG && process?.waitFor(110, TimeUnit.MILLISECONDS) == true) {
                val exitCode = process?.exitValue() ?: -1
                if (exitCode != 0) {
                    val stderr = process?.readStderr().orEmpty()
                    Log.e(this@ProcessConnection::class.java.simpleName, stderr)
                    errorDialog(stderr)
                }
            }
        }
    }

    override fun close() {
        runBlocking{
            Log.e(this@ProcessConnection::class.java.simpleName,process?.readStderr().toString())
        }

        process?.destroy()
        process = null
    }
}
