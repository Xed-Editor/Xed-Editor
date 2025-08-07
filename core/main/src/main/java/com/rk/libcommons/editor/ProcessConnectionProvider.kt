package com.rk.libcommons.editor

import com.rk.libcommons.child
import com.rk.libcommons.localBinDir
import io.github.rosemoe.sora.lsp.client.connection.StreamConnectionProvider
import java.io.InputStream
import java.io.OutputStream

class ProcessConnectionProvider(
    private val command: Array<String>,
    private val sandbox: Boolean = true
) : StreamConnectionProvider {

    private var process: Process? = null

    override val inputStream: InputStream
        get() = process?.inputStream
            ?: throw IllegalStateException("Process not started yet")

    override val outputStream: OutputStream
        get() = process?.outputStream
            ?: throw IllegalStateException("Process not started yet")

    override fun start() {
        if (process != null) return // already started

        val pb = if (sandbox) {
            val initHost = localBinDir().child("sandbox.sh")
            ProcessBuilder("sh", "-c",initHost.absolutePath, command.joinToString(" "))
        } else {
            ProcessBuilder(*command)
        }

        pb.redirectError(ProcessBuilder.Redirect.INHERIT)
        process = pb.start()
    }

    override fun close() {
        try {
            process?.destroy()
        } catch (_: Exception) {
        } finally {
            process = null
        }
    }
}
