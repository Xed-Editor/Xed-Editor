package com.rk.runner

import android.content.Context
import android.content.Intent
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.ServerSocket

object commonUtils {
    fun exctractAssets(context: Context, onComplete: () -> Unit) {
        if (File("${context.filesDir.parentFile!!.absolutePath}/rootfs/python.sh").exists().not()) {
            exctractAssets(
                context,
                "python.sh",
                "${context.filesDir.parentFile!!.absolutePath}/rootfs/python.sh",
            )
        }

        if (File("${context.filesDir.parentFile!!.absolutePath}/rootfs/nodejs.sh").exists().not()) {
            exctractAssets(
                context,
                "nodejs.sh",
                "${context.filesDir.parentFile!!.absolutePath}/rootfs/nodejs.sh",
            )
        }

        if (File("${context.filesDir.parentFile!!.absolutePath}/rootfs/java.sh").exists().not()) {
            exctractAssets(
                context,
                "java.sh",
                "${context.filesDir.parentFile!!.absolutePath}/rootfs/java.sh",
            )
        }

        onComplete()
    }

    @JvmOverloads
    @JvmStatic
    fun runCommand(
        // run in alpine or not
        alpine: Boolean,
        // shell or binary to run
        shell: String,
        // arguments passed to shell or binary
        args: Array<String> = arrayOf(),
        // working directory
        workingDir: String,
        // array of environment variables with key value pair eg. HOME=/sdcard,TMP=/tmp
        environmentVars: Array<String>? = arrayOf(),
        // should override default environment variables or not
        overrideEnv: Boolean = false,
        // context to launch terminal activity
        context: Context,
    ) {
        context.startActivity(
            Intent(context, Class.forName("com.rk.xededitor.terminal.Terminal")).also {
                it.putExtra("run_cmd", true)
                it.putExtra("shell", shell)
                it.putExtra("args", args)
                it.putExtra("cwd", workingDir)
                it.putExtra("env", environmentVars)
                it.putExtra("overrideEnv", overrideEnv)
                it.putExtra("alpine", alpine)
            }
        )
    }

    fun exctractAssets(context: Context, assetFileName: String, outputFilePath: String) {
        val assetManager = context.assets
        val outputFile = File(outputFilePath)

        try {
            // Open the asset file as an InputStream
            assetManager.open(assetFileName).use { inputStream ->
                // Create an output file and its parent directories if they don't exist
                outputFile.parentFile?.mkdirs()

                // Write the input stream to the output file
                FileOutputStream(outputFile).use { outputStream ->
                    val buffer = ByteArray(1024)
                    var length: Int
                    while (inputStream.read(buffer).also { length = it } > 0) {
                        outputStream.write(buffer, 0, length)
                    }
                }
            }
        } catch (e: IOException) {
            e.printStackTrace()
            println("Failed to copy file: ${e.message}")
        }
    }
    fun getAvailablePort(): Int {
        ServerSocket(0).use { socket ->
            return socket.localPort
        }
    }
}
