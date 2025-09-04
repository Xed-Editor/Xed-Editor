package com.rk.terminal

import android.annotation.SuppressLint
import android.util.Log
import com.rk.App
import com.rk.file.child
import com.rk.file.localBinDir
import com.rk.file.localLibDir
import com.rk.file.sandboxDir
import com.rk.file.sandboxHomeDir
import com.rk.libcommons.application
import com.rk.terminal.bind
import com.rk.xededitor.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.random.Random

import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.io.Writer
import kotlinx.coroutines.withContext

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
        bind(sandboxHomeDir().absolutePath,"/home")
        bind("/sdcard")
        bind("/storage")
        bind("/data")
        bind(application!!.filesDir.parentFile!!.absolutePath)
        bind("/dev")
        bind("/proc")
        bind("/system")
        bind("/sys")
        bind("/dev/urandom","/dev/random")
        bind("/system_ext")
        bind("/product")
        bind("/odm")
        bind("/apex")
        bind("/vendor")
        bind("/linkerconfig/ld.config.txt")
        bind("/linkerconfig/com.android.art/ld.config.txt")
        bind("/plat_property_contexts","/property_contexts")
        bind("/sys")
        bind("${App.getTempDir().absolutePath}","/dev/shm")
    }

    return list
}


suspend fun newSandbox(excludeMounts:List<String> = listOf<String>(), root: File = sandboxDir(), workingDir: String? = null, command: MutableList<String>): Process = withContext(
    Dispatchers.IO){

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

        bind(tmpDir.absolutePath)

        add("-0")
        add("--link2symlink")
        add("--sysvipc")
        add("-L")

        add("-r")
        add(root.absolutePath)
        addAll(command)
    }

    if (BuildConfig.DEBUG){
        Log.i("SANDBOX", args.toList().toString())
    }

    val processBuilder = ProcessBuilder(linker, *args.toTypedArray())

    processBuilder.environment().let { env ->
        env["LD_LIBRARY_PATH"] = localLibDir().absolutePath

        env["PROOT_TMP_DIR"] = tmpDir.absolutePath

        if (!App.isFDroid){
            env["PROOT_LOADER"] = "${application!!.applicationInfo.nativeLibraryDir}/libproot-loader.so"
            if (File(application!!.applicationInfo.nativeLibraryDir).child("libproot-loader32.so").exists()){
                env["PROOT_LOADER32"] ="${application!!.applicationInfo.nativeLibraryDir}/libproot-loader32.so"
            }
        }
    }

    return@withContext processBuilder.start()
}

@SuppressLint("SdCardPath")
suspend fun newSandbox(excludeMounts:List<String> = listOf<String>(),root: File = sandboxDir(), workingDir: String? = null, vararg command: String): Process{
    return newSandbox(excludeMounts,root,workingDir,command.toMutableList())
}

/** Extension to read all stdout as a single string */
suspend fun Process.readStdout(): String = withContext(Dispatchers.IO) {
    inputStream.bufferedReader().use(BufferedReader::readText)
}

/** Extension to read all stderr as a single string */
suspend fun Process.readStderr(): String = withContext(Dispatchers.IO) {
    errorStream.bufferedReader().use(BufferedReader::readText)
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

