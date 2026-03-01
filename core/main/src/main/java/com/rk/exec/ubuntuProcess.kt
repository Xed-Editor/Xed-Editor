package com.rk.exec

import android.annotation.SuppressLint
import android.os.Build
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
import com.rk.utils.isFDroid
import com.rk.xededitor.BuildConfig
import java.io.File
import java.io.IOException
import java.io.OutputStreamWriter
import kotlin.random.Random
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class Binding(val outside: String, val inside: String? = null)

private fun MutableList<String>.bind(outside: String, inside: String? = null) {
    if (File(outside).exists()) {
        add("-b")
        add("$outside${if (inside != null){":$inside"}else{""}}")
    }
}

fun List<Binding>.attachTo(list: MutableList<String>, excludeMounts: List<String> = listOf<String>()) {
    forEach {
        if (!excludeMounts.contains(it.outside)) {
            list.bind(it.outside, it.inside)
        }
    }
}

fun getDefaultBindings(): List<Binding> {
    fun MutableList<Binding>.bind(outside: String, inside: String? = null) {
        if (File(outside).exists()) {
            add(Binding(outside, inside))
        }
    }

    val list = mutableListOf<Binding>()

    with(list) {
        bind(sandboxHomeDir().absolutePath, "/home")
        bind("/sdcard")
        bind("/storage")
        bind("/data")
        bind(application!!.filesDir.parentFile!!.absolutePath)
        bind("/dev")
        bind("/proc")
        bind("/system")
        bind("/sys")
        bind("/dev/urandom", "/dev/random")
        bind("/system_ext")
        bind("/product")
        bind("/odm")
        bind("/apex")
        bind("/vendor")
        bind("/linkerconfig/ld.config.txt")
        bind("/linkerconfig/com.android.art/ld.config.txt")
        bind("/plat_property_contexts", "/property_contexts")
        bind("/sys")
        bind("${getTempDir().absolutePath}", "/dev/shm")
    }

    return list
}

suspend fun ubuntuProcess(
    excludeMounts: List<String> = listOf(),
    root: File = sandboxDir(),
    workingDir: String? = null,
    command: MutableList<String>,
): Process =
    withContext(Dispatchers.IO) {
        if (!root.exists()) throw NoSuchFileException(root)

        val randomInt = Random.nextInt()
        val tmpDir = getTempDir().child("$randomInt-sandbox")
        if (!tmpDir.exists()) {
            tmpDir.mkdirs()
        }

        val linker = if (File("/system/bin/linker64").exists()) "/system/bin/linker64" else "/system/bin/linker"

        val args =
            mutableListOf<String>().apply {
                add(localBinDir().child("proot").absolutePath)
                add("--kill-on-exit")

                if (workingDir != null) {
                    add("-w")
                    add(workingDir)
                }

                getDefaultBindings().attachTo(this, excludeMounts)

                bind(tmpDir.absolutePath)

                add("-0")
                add("--link2symlink")
                add("--sysvipc")
                add("-L")

                add("-r")
                add(root.absolutePath)
                addAll(command)
            }

        if (BuildConfig.DEBUG) {
            Log.i("SANDBOX", args.toList().toString())
        }

        val processBuilder = ProcessBuilder(linker, *args.toTypedArray())

        processBuilder.environment().let { env ->
            env["WKDIR"] = workingDir.orEmpty()
            env["COLORTERM"] = "truecolor"
            env["TERM"] = "xterm-256color"
            env["LANG"] = "C.UTF-8"
            env["PUBLIC_HOME"] = application!!.getExternalFilesDir(null)?.absolutePath.orEmpty()
            env["DEBUG"] = BuildConfig.DEBUG.toString()
            env["LOCAL"] = localDir().absolutePath
            env["PRIVATE_DIR"] = application!!.filesDir.parentFile!!.absolutePath
            env["EXT_HOME"] = sandboxHomeDir().absolutePath
            env["HOME"] =
                if (Settings.sandbox) {
                    "/home"
                } else {
                    sandboxHomeDir().absolutePath
                }
            env["PROMPT_DIRTRIM"] = "2"
            env["LINKER"] =
                if (File("/system/bin/linker64").exists()) {
                    "/system/bin/linker64"
                } else {
                    "/system/bin/linker"
                }
            env["NATIVE_LIB_DIR"] = application!!.applicationInfo.nativeLibraryDir
            env["FDROID"] = isFDroid.toString()
            env["SANDBOX"] = Settings.sandbox.toString()
            env["TMP_DIR"] = getTempDir().absolutePath
            env["TMPDIR"] = getTempDir().absolutePath
            env["TZ"] = "UTC"
            env["DOTNET_GCHeapHardLimit"] = "1C0000000"
            env["SOURCE_DIR"] = application!!.applicationInfo.sourceDir
            env["TERMUX_X11_SOURCE_DIR"] = getSourceDirOfPackage(application!!, "com.termux.x11").orEmpty()
            env["DISPLAY"] = ":0"
            env["LD_LIBRARY_PATH"] = localLibDir().absolutePath
            env["PROOT_TMP_DIR"] = tmpDir.absolutePath

            env["ANDROID_ART_ROOT"] = System.getenv("ANDROID_ART_ROOT").orEmpty()
            env["ANDROID_DATA"] = System.getenv("ANDROID_DATA").orEmpty()
            env["ANDROID_I18N_ROOT"] = System.getenv("ANDROID_I18N_ROOT").orEmpty()
            env["ANDROID_ROOT"] = System.getenv("ANDROID_ROOT").orEmpty()
            env["ANDROID_RUNTIME_ROOT"] = System.getenv("ANDROID_RUNTIME_ROOT").orEmpty()
            env["ANDROID_TZDATA_ROOT"] = System.getenv("ANDROID_TZDATA_ROOT").orEmpty()
            env["BOOTCLASSPATH"] = System.getenv("BOOTCLASSPATH").orEmpty()
            env["DEX2OATBOOTCLASSPATH"] = System.getenv("DEX2OATBOOTCLASSPATH").orEmpty()
            env["EXTERNAL_STORAGE"] = System.getenv("EXTERNAL_STORAGE").orEmpty()

            env["PATH"] =
                "/bin:/sbin:/usr/bin:/usr/sbin:/usr/games:/usr/local/bin:/usr/local/sbin:${localBinDir()}:${System.getenv("PATH")}"

            if (!isFDroid) {
                env["PROOT_LOADER"] = "${application!!.applicationInfo.nativeLibraryDir}/libproot-loader.so"
                if (
                    Build.SUPPORTED_32_BIT_ABIS.isNotEmpty() &&
                        File(application!!.applicationInfo.nativeLibraryDir).child("libproot-loader32.so").exists()
                ) {
                    env["PROOT_LOADER32"] = "${application!!.applicationInfo.nativeLibraryDir}/libproot-loader32.so"
                }
            }

            if (Settings.seccomp) {
                env["SECCOMP"] = "1"
            }
        }

        return@withContext processBuilder.start()
    }

@SuppressLint("SdCardPath")
suspend fun ubuntuProcess(
    excludeMounts: List<String> = listOf<String>(),
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
