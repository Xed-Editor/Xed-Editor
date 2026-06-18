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

suspend fun ubuntuProcess(
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
            var resolved = arg
            if (resolved.startsWith("/home/")) {
                resolved = homePath + "/" + resolved.substring(6)
            } else if (resolved == "/home") {
                resolved = homePath
            }
            if (resolved.startsWith("/usr/bin/")) {
                resolved = prefixPath + "/bin/" + resolved.substring(9)
            } else if (resolved.startsWith("/usr/")) {
                resolved = prefixPath + "/" + resolved.substring(5)
            } else if (resolved.startsWith("/bin/")) {
                resolved = prefixPath + "/bin/" + resolved.substring(5)
            }
            resolved
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
            val resolvedWkDir = if (workingDir.startsWith("/home/")) {
                homePath + "/" + workingDir.substring(6)
            } else if (workingDir == "/home") {
                homePath
            } else {
                workingDir
            }
            processBuilder.directory(File(resolvedWkDir))
        }

        processBuilder.environment().let { env ->
            env["PREFIX"] = prefixPath
            env["LD_PRELOAD"] = "$prefixPath/lib/libtermux-exec.so"
            env["PROOT"] = "${application!!.applicationInfo.nativeLibraryDir}/libproot.so"
            env["PROOT_LOADER"] = "${application!!.applicationInfo.nativeLibraryDir}/libloader.so"
            env["PROOT_TMP_DIR"] = tmpDir.absolutePath
            env["WKDIR"] = workingDir.orEmpty()
            env["COLORTERM"] = "truecolor"
            env["TERM"] = "xterm-256color"
            env["LANG"] = "C.UTF-8"
            env["PUBLIC_HOME"] = application!!.getExternalFilesDir(null)?.absolutePath.orEmpty()
            env["DEBUG"] = BuildConfig.DEBUG.toString()
            env["LOCAL"] = localDir().absolutePath
            env["PRIVATE_DIR"] = application!!.filesDir.parentFile!!.absolutePath
            env["EXT_HOME"] = sandboxHomeDir().absolutePath
            env["HOME"] = sandboxHomeDir().absolutePath
            env["PROMPT_DIRTRIM"] = "2"
            env["LINKER"] = linker
            env["NATIVE_LIB_DIR"] = application!!.applicationInfo.nativeLibraryDir
            env["TMP_DIR"] = getTempDir().absolutePath
            env["TMPDIR"] = getTempDir().absolutePath
            env["TZ"] = "UTC"
            env["DOTNET_GCHeapHardLimit"] = "1C0000000"
            env["SOURCE_DIR"] = application!!.applicationInfo.sourceDir
            env["TERMUX_X11_SOURCE_DIR"] = getSourceDirOfPackage(application!!, "com.termux.x11").orEmpty()
            env["DISPLAY"] = ":0"
            env["LD_LIBRARY_PATH"] = "$prefixPath/lib:${localLibDir().absolutePath}"

            val loader32 = "${application!!.applicationInfo.nativeLibraryDir}/libloader32.so"
            if (File(loader32).exists()) {
                env["PROOT_LOADER_32"] = loader32
            }

            env["ANDROID_ART_ROOT"] = System.getenv("ANDROID_ART_ROOT").orEmpty()
            env["ANDROID_DATA"] = System.getenv("ANDROID_DATA").orEmpty()
            env["ANDROID_I18N_ROOT"] = System.getenv("ANDROID_I18N_ROOT").orEmpty()
            env["ANDROID_ROOT"] = System.getenv("ANDROID_ROOT").orEmpty()
            env["ANDROID_RUNTIME_ROOT"] = System.getenv("ANDROID_RUNTIME_ROOT").orEmpty()
            env["ANDROID_TZDATA_ROOT"] = System.getenv("ANDROID_TZDATA_ROOT").orEmpty()
            env["BOOTCLASSPATH"] = System.getenv("BOOTCLASSPATH").orEmpty()
            env["DEX2OATBOOTCLASSPATH"] = System.getenv("DEX2OATBOOTCLASSPATH").orEmpty()
            env["EXTERNAL_STORAGE"] = System.getenv("EXTERNAL_STORAGE").orEmpty()
            env["PATH"] = "$prefixPath/bin:$prefixPath/bin/applets:${System.getenv("PATH")}:${localBinDir().absolutePath}"
        }

        return@withContext processBuilder.start()
    }

@SuppressLint("SdCardPath")
suspend fun ubuntuProcess(
    excludeMounts: List<String> = listOf(),
    root: File = sandboxDir(),
    workingDir: String? = null,
    vararg command: String,
): Process {
    return ubuntuProcess(excludeMounts, root, workingDir, command.toMutableList())
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
