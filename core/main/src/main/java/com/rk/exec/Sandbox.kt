package com.rk.exec

import android.annotation.SuppressLint
import android.os.Build
import android.util.Log
import com.rk.App
import com.rk.file.child
import com.rk.file.localBinDir
import com.rk.file.localDir
import com.rk.file.localLibDir
import com.rk.file.sandboxDir
import com.rk.file.sandboxHomeDir
import com.rk.utils.application
import com.rk.utils.errorDialog
import com.rk.utils.toast
import com.rk.xededitor.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.random.Random

import java.io.OutputStreamWriter
import java.io.IOException

data class Binding(val outside: String,val inside: String? = null)

private fun MutableList<String>.bind(outside: String,inside: String? = null){
    if (File(outside).exists()){
        add("-b")
        add("$outside${if (inside != null){":$inside"}else{""}}")
    }
}

fun List<Binding>.attachTo(list:MutableList<String>,excludeMounts:List<String> = listOf<String>()){
    forEach{
        if (!excludeMounts.contains(it.outside)){
            list.bind(it.outside,it.inside)
        }

    }
}

fun getDefaultBindings(): List<Binding>{
    fun MutableList<Binding>.bind(outside: String, inside: String? = null){
        if (File(outside).exists()){
            add(Binding(outside,inside))
        }
    }

    val list = mutableListOf<Binding>()

    with(list){
        bind(application!!.filesDir.parentFile!!.absolutePath,"/data/data/com.termux")
    }

    return list
}


suspend fun newSandbox(excludeMounts:List<String> = listOf<String>(), workingDir: String? = sandboxHomeDir().absolutePath, command: MutableList<String>): Process = withContext(
    Dispatchers.IO){

    val root: File = File("/")
    if (!root.exists()) throw NoSuchFileException(root)

    val randomInt = Random.nextInt()
    val tmpDir = App.getTempDir().child("$randomInt-sandbox")
    if (!tmpDir.exists()) {
        tmpDir.mkdirs()
    }

    val linker = if (File("/system/bin/linker64").exists()) "/system/bin/linker64" else "/system/bin/linker"

    val args = mutableListOf<String>().apply {
        add(localBinDir().child("proot").absolutePath)
        add("--kill-on-exit")

        if (workingDir != null){
            add("-w")
            add(workingDir)
        }

        getDefaultBindings().attachTo(this,excludeMounts)

        //bind(tmpDir.absolutePath)

        add("-r")
        add(root.absolutePath)
        addAll(command)
    }

    if (BuildConfig.DEBUG){
        Log.i("SANDBOX", args.toList().toString())
        //errorDialog(args.toList().toString())
    }

    val processBuilder = ProcessBuilder(linker, *args.toTypedArray())

    processBuilder.environment().let { env ->
        env["LD_LIBRARY_PATH"] = localLibDir().absolutePath
        env["PROOT_TMP_DIR"] = tmpDir.absolutePath
        env["HOME"] = "/data/data/com.termux/files/home"
        env["PATH"] = "/data/data/com.termux/files/usr/bin:/system/bin"
        //env["LD_PRELOAD"] = "/data/data/com.termux/files/usr/lib/libtermux-exec.so"
        env["PREFIX"] = "/data/data/com.termux/files/usr"

        if (!App.isFDroid){
            env["PROOT_LOADER"] = "${application!!.applicationInfo.nativeLibraryDir}/libproot-loader.so"
            if (Build.SUPPORTED_32_BIT_ABIS.isNotEmpty() && File(application!!.applicationInfo.nativeLibraryDir).child("libproot-loader32.so").exists()){
                env["PROOT_LOADER32"] ="${application!!.applicationInfo.nativeLibraryDir}/libproot-loader32.so"
            }
        }
    }

    return@withContext processBuilder.start()
}

@SuppressLint("SdCardPath")
suspend fun newSandbox(excludeMounts:List<String> = listOf<String>(), workingDir: String? = null, vararg command: String): Process{
    return newSandbox(excludeMounts,workingDir,command.toMutableList())
}

/** Extension to read all stdout as a single string */
suspend fun Process.readStdout(): String = withContext(Dispatchers.IO) {
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

suspend fun Process.readStderr(): String = withContext(Dispatchers.IO) {
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
suspend fun Process.writeInput(input: String, flush: Boolean = true) = withContext(Dispatchers.IO) {
    OutputStreamWriter(outputStream).use { writer ->
        writer.write(input)
        if (flush) writer.flush()
    }
}

/** Extension to wait for process to finish and return exit code */
suspend fun Process.awaitExit(): Int = withContext(Dispatchers.IO) {
    waitFor()
}

/** Extension to destroy process safely */
fun Process.terminate() {
    if (isAlive) destroy()
}

/** Extension to check if process is alive */
fun Process.isRunning(): Boolean = isAlive

