package com.rk.lsp

import android.util.Log
import com.rk.file.localBinDir
import com.rk.file.localLibDir
import com.rk.settings.Settings
import com.rk.settings.app.InbuiltFeatures
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.io.File
import java.io.InputStream
import java.io.OutputStream

class AndroidProcessConnection(private val cmd: Array<String>, instance: LspServerInstance) :
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

        val pb = ProcessBuilder(*cmd)

        val rootPath = instance.projectRoot.getAbsolutePath()
        val rootFile = File(rootPath)
        if (rootFile.exists() && rootFile.isDirectory) {
            pb.directory(rootFile)
        }

        pb.environment().apply {
            put("ANDROID_ART_ROOT", System.getenv("ANDROID_ART_ROOT").orEmpty())
            put("ANDROID_DATA", System.getenv("ANDROID_DATA").orEmpty())
            put("ANDROID_I18N_ROOT", System.getenv("ANDROID_I18N_ROOT").orEmpty())
            put("ANDROID_ROOT", System.getenv("ANDROID_ROOT").orEmpty())
            put("ANDROID_RUNTIME_ROOT", System.getenv("ANDROID_RUNTIME_ROOT").orEmpty())
            put("ANDROID_TZDATA_ROOT", System.getenv("ANDROID_TZDATA_ROOT").orEmpty())
            put("BOOTCLASSPATH", System.getenv("BOOTCLASSPATH").orEmpty())
            put("DEX2OATBOOTCLASSPATH", System.getenv("DEX2OATBOOTCLASSPATH").orEmpty())
            put("EXTERNAL_STORAGE", System.getenv("EXTERNAL_STORAGE").orEmpty())
            put("PATH", "${System.getenv("PATH")}:${localBinDir().absolutePath}")
            put("LD_LIBRARY_PATH", localLibDir().absolutePath)
        }

        process = pb.start()

        loggingInput =
            LoggingInputStream(process!!.inputStream) { json ->
                Log.d("AndroidProcessConnection", "[stdout] $json")
                if (InbuiltFeatures.debugMode.state.value && Settings.record_rpc) {
                    instance.addLog(LspLogEntry(MessageSource.RPC, null, "→ $json"))
                }
            }
        loggingOutput =
            LoggingOutputStream(process!!.outputStream) { json ->
                Log.d("AndroidProcessConnection", "[stdin] $json")
                if (InbuiltFeatures.debugMode.state.value && Settings.record_rpc) {
                    instance.addLog(LspLogEntry(MessageSource.RPC, null, "← $json"))
                }
            }

        scope!!.launch {
            runCatching {
                process!!.errorStream.bufferedReader().forEachLine { line ->
                    Log.e("AndroidProcessConnection", "[stderr] $line")
                    instance.addLog(LspLogEntry(MessageSource.Runtime, null, line))
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
