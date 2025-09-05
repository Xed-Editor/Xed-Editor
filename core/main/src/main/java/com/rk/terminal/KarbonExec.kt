package com.rk.terminal

import android.content.Context
import android.content.Intent
import android.util.Log
import com.rk.file.child
import com.rk.file.sandboxDir
import com.rk.file.sandboxHomeDir
import com.rk.libcommons.TerminalCommand
import com.rk.libcommons.errorDialog
import com.rk.libcommons.pendingCommand
import com.rk.libcommons.showTerminalNotice
import com.rk.xededitor.BuildConfig
import com.rk.xededitor.ui.activities.main.MainActivity
import com.rk.xededitor.ui.activities.terminal.Terminal
import io.github.rosemoe.sora.lsp.client.connection.StreamConnectionProvider
import java.io.InputStream
import java.io.OutputStream
import kotlinx.coroutines.*
import java.util.concurrent.TimeUnit


fun isTerminalInstalled(): Boolean{
    val rootfs = sandboxDir().listFiles()?.filter {
        it.absolutePath != sandboxHomeDir().absolutePath && it.absolutePath != sandboxDir().child(
            "tmp"
        ).absolutePath
    } ?: emptyList()

    return rootfs.isNotEmpty()
}

fun launchInternalTerminal(context: Context, terminalCommand: TerminalCommand) {
    showTerminalNotice(activity = MainActivity.instance!!){
        pendingCommand = terminalCommand
        context.startActivity(
            Intent(
                context, Terminal::class.java
            )
        )
    }
}


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
