package com.rk.runner

import android.content.Context
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.ServerSocket

object commonUtils {
    fun extractAssets(context: Context, onComplete: () -> Unit) {
        if (File("${context.filesDir.parentFile!!.absolutePath}/rootfs/python.sh").exists().not()) {
            extractAssets(
                context,
                "python.sh",
                "${context.filesDir.parentFile!!.absolutePath}/rootfs/python.sh",
            )
        }

        if (File("${context.filesDir.parentFile!!.absolutePath}/rootfs/nodejs.sh").exists().not()) {
            extractAssets(
                context,
                "nodejs.sh",
                "${context.filesDir.parentFile!!.absolutePath}/rootfs/nodejs.sh",
            )
        }

        if (File("${context.filesDir.parentFile!!.absolutePath}/rootfs/java.sh").exists().not()) {
            extractAssets(
                context,
                "java.sh",
                "${context.filesDir.parentFile!!.absolutePath}/rootfs/java.sh",
            )
        }

        onComplete()
    }

    fun extractAssets(context: Context, assetFileName: String, outputFilePath: String) {
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
        try {
            ServerSocket(0).use { socket ->
                return socket.localPort
            }
        }catch (e:Exception){
            e.printStackTrace()
            return 9999
        }
    }
}
