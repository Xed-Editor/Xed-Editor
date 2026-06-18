package com.rk.exec

import android.annotation.SuppressLint
import android.util.Log
import com.rk.file.child
import com.rk.file.localBinDir
import com.rk.file.localDir
import com.rk.file.localLibDir
import com.rk.file.sandboxDir
import com.rk.file.sandboxHomeDir
import com.rk.settings.Settings
import com.rk.utils.application
import com.rk.utils.getSourceDirOfPackage
import com.rk.utils.getTempDir
import com.rk.xededitor.BuildConfig
import java.io.File
import java.io.IOException
import java.io.OutputStreamWriter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

suspend fun termuxProcess(
    excludeMounts: List<String> = listOf(),
    root: File = sandboxDir(),
    workingDir: String? = null,
    command: List<String>,
): Process =
    withContext(Dispatchers.IO) {
        val homePath = sandboxHomeDir().absolutePath
        val prefixPath = File(application!!.filesDir, "usr").absolutePath
        val linker = if (File("/system/bin/linker64").exists()) "/system/bin/linker64" else "/system/bin/linker"

        val randomInt = kotlin.random.Random.nextInt()
        val tmpDir = getTempDir().child("$randomInt-sandbox")
        if (!tmpDir.exists()) {
            tmpDir.mkdirs()
        }

        val resolvedCommand = command.map { arg ->
            ProcessEnv.resolvePath(arg, prefixPath, homePath)
        }.toMutableList()

        val bashPath = "$prefixPath/bin/bash"
        val exeFile = File(resolvedCommand[0])
        val runArgs = mutableListOf<String>()

        if (exeFile.exists() && exeFile.isFile) {
            val isScript = try {
                val header = ByteArray(2)
                exeFile.inputStream().use { it.read(header) }
                header[0] == '#'.code.toByte() && header[1] == '!'.code.toByte()
            } catch (e: Exception) {
                false
            }

            if (isScript || resolvedCommand[0].endsWith(".sh")) {
                runArgs.add(linker)
                runArgs.add(bashPath)
                runArgs.addAll(resolvedCommand)
            } else {
                runArgs.add(linker)
                runArgs.addAll(resolvedCommand)
            }
        } else {
            val prefixBinFile = File(prefixPath + "/bin/" + resolvedCommand[0])
            if (prefixBinFile.exists()) {
                runArgs.add(linker)
                resolvedCommand[0] = prefixBinFile.absolutePath
                runArgs.addAll(resolvedCommand)
            } else {
                runArgs.addAll(resolvedCommand)
            }
        }

        val processBuilder = ProcessBuilder(runArgs)

        if (workingDir != null) {
            val resolvedWkDir = ProcessEnv.resolvePath(workingDir, prefixPath, homePath)
            processBuilder.directory(File(resolvedWkDir))
        }

        processBuilder.environment().putAll(
            ProcessEnv.getEnvironment(application!!, tmpDir, workingDir.orEmpty(), linker)
        )

        return@withContext processBuilder.start()
    }

@SuppressLint("SdCardPath")
suspend fun termuxProcess(
    excludeMounts: List<String> = listOf(),
    root: File = sandboxDir(),
    workingDir: String? = null,
    vararg command: String,
): Process {
    return termuxProcess(excludeMounts, root, workingDir, command.toMutableList())
}

/** Extension to read all stdout as a single string */
suspend fun Process.readStdout(): String =
    withContext(Dispatchers.IO) {
        try {
            inputStream.bufferedReader().use { reader ->
                if (inputStream.available() <= 0) return@use ""
                reader.readText()
            }
        } catch (e: IOException) {
            e.printStackTrace()
            if (e.message?.contains("Stream closed") == true) "" else throw e
        }
    }

suspend fun Process.readStderr(): String =
    withContext(Dispatchers.IO) {
        try {
            errorStream.bufferedReader().use { reader ->
                if (errorStream.available() <= 0) return@use ""
                reader.readText()
            }
        } catch (e: IOException) {
            e.printStackTrace()
            if (e.message?.contains("Stream closed") == true) "" else throw e
        }
    }

/** Extension to write to process stdin */
suspend fun Process.writeInput(input: String, flush: Boolean = true) =
    withContext(Dispatchers.IO) {
        OutputStreamWriter(outputStream).use { writer ->
            writer.write(input)
            if (flush) writer.flush()
        }
    }

/** Extension to wait for process to finish and return exit code */
suspend fun Process.awaitExit(): Int = withContext(Dispatchers.IO) { waitFor() }

/** Extension to destroy process safely */
fun Process.terminate() {
    if (isAlive) destroy()
}

/** Extension to check if process is alive */
fun Process.isRunning(): Boolean = isAlive
