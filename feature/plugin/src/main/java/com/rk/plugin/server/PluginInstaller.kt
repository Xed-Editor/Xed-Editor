package com.rk.plugin.server

import android.content.Context
import android.widget.Toast
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch

@Suppress("NOTHING_TO_INLINE")
object PluginInstaller {
    
    inline fun installFromZip(context: Context, file: File): Boolean {
        return installFromZip(context, FileInputStream(file))
    }

    @OptIn(DelicateCoroutinesApi::class)
    fun installFromZip(context: Context, inputStream: InputStream): Boolean {
        val tempDir = File(context.cacheDir, "PluginInstallTempDir")
        if (!tempDir.exists()) {
            tempDir.mkdirs()
        }
        val pluginsDir = File(context.filesDir.parentFile!!, "plugins")
        if (pluginsDir.exists().not()) {
            pluginsDir.mkdirs()
        }

        extractZip(inputStream, tempDir)

        var isInstalled = false
        tempDir.listFiles()?.forEach { f ->
            if (f.isDirectory && File(f, "manifest.json").exists()) {
                copyDirectory(f.parentFile!!, pluginsDir, context)
                isInstalled = true
            }
        }

        GlobalScope.launch(Dispatchers.IO) { tempDir.deleteRecursively() }

        return isInstalled
    }

    private fun copyDirectory(sourceDir: File, targetDir: File, context: Context) {
        if (!sourceDir.exists()) return
        if (!targetDir.exists()) targetDir.mkdirs()
        sourceDir.listFiles()?.forEach { file ->
            val newFile = File(targetDir, file.name)
            if (file.isDirectory) {
                copyDirectory(file, newFile, context)
            } else {
                try {
                    Files.copy(file.toPath(), newFile.toPath(), StandardCopyOption.REPLACE_EXISTING)
                } catch (e: IOException) {
                    e.printStackTrace()
                    Toast.makeText(context, e.message, Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private inline fun extractZip(inputStream: InputStream, tmpDir: File) {
        ZipInputStream(inputStream).use { zipInputStream ->
            var entry: ZipEntry? = zipInputStream.nextEntry
            while (entry != null) {
                val file = File(tmpDir, entry.name)
                if (entry.isDirectory) {
                    file.mkdirs()
                } else {
                    file.parentFile?.mkdirs()
                    FileOutputStream(file).use { outputStream ->
                        val buffer = ByteArray(1024)
                        var length: Int
                        while (zipInputStream.read(buffer).also { length = it } > 0) {
                            outputStream.write(buffer, 0, length)
                        }
                    }
                }
                zipInputStream.closeEntry()
                entry = zipInputStream.nextEntry
            }
        }
    }
}
