package com.rk.extension

import android.content.Context
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import com.rk.libcommons.application
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.util.Properties
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipInputStream

object ExtensionManager {
    val isLoaded = mutableStateOf(false)
    val extensions = mutableStateMapOf<Extension,ExtensionAPI?>()

    init {
        if (isLoaded.value.not()){
            GlobalScope.launch {
                loadExistingPlugins(application!!)
                withContext(Dispatchers.Main){
                    isLoaded.value = true
                }
            }
        }
    }

    suspend fun loadExistingPlugins(context: Context): Map<Extension,ExtensionAPI?> =
        withContext(Dispatchers.IO) {
            extensions.clear()
            context.pluginDir.listFiles()?.forEach { pluginFolder ->
                val manifestFile = File(pluginFolder, "manifest.properties")
                val codeFolder = File(pluginFolder, "code")

                if (manifestFile.exists() && codeFolder.exists()) {
                    val dexFiles =
                        codeFolder.listFiles { _, name -> name.endsWith(".dex") }?.toList()
                            ?: emptyList()

                    val properties = Properties()
                    FileInputStream(manifestFile).use { inputStream ->
                        properties.load(inputStream)
                    }

                    extensions.put(
                        Extension(
                            manifest = manifestFile,
                            dexFiles = dexFiles,
                            name = properties.getProperty("name") ?: "Unknown",
                            packageName = properties.getProperty("packageName"),
                            mainClass = properties.getProperty("mainClass") ?: "",
                            settingsClass = properties.getProperty("settingsClass"),
                            website = properties.getProperty("pluginWebsite"),
                            author = properties.getProperty("author"),
                            version = properties.getProperty("version") ?: "1.0",
                            versionCode = properties.getProperty("versionCode")?.toIntOrNull()
                                ?: 1
                        ),null
                    )
                }
            }

            extensions
        }


    private fun requiredByOthers(dex:File,except:Extension):Boolean{
        extensions.keys.forEach {
            if (it != except && it.dexFiles.contains(dex)){
                return true
            }
        }
        return false
    }

    suspend fun deletePlugin(extension: Extension) = withContext(Dispatchers.IO){
        extension.dexFiles.forEach { dex ->
            if (requiredByOthers(dex,extension).not()){
                dex.delete()
            }
        }
        extension.manifest.parentFile!!.deleteRecursively()
        extensions.remove(extension)
    }

    suspend fun installPlugin(context: Context, zipFile: File): Extension? =
        withContext(Dispatchers.IO) {
            val properties = getProperties(zipFile)
            val pluginName = properties.getProperty("packageName")
            val destinationFolder = File(context.pluginDir, pluginName).apply { mkdirs() }

            try {
                ZipInputStream(FileInputStream(zipFile)).use { zipInputStream ->
                    var entry: ZipEntry?
                    while (zipInputStream.nextEntry.also { entry = it } != null) {
                        entry?.let { zipEntry ->
                            val outFile = File(destinationFolder, zipEntry.name)
                            if (zipEntry.isDirectory) {
                                outFile.mkdirs()
                            } else {
                                outFile.parentFile?.mkdirs()
                                outFile.outputStream().use { output ->
                                    zipInputStream.copyTo(output)
                                }
                            }
                        }
                    }
                }

                val manifestFile = File(destinationFolder, "manifest.properties")
                val codeFolder = File(destinationFolder, "code")
                val dexFiles = codeFolder.listFiles { _, name -> name.endsWith(".dex") }?.toList()
                    ?: emptyList()

                if (manifestFile.exists() && dexFiles.isNotEmpty()) {
                    val newExtension =  Extension(
                        manifest = manifestFile,
                        dexFiles = dexFiles,
                        name = properties.getProperty("name") ?: "Unknown",
                        packageName = properties.getProperty("packageName"),
                        mainClass = properties.getProperty("mainClass") ?: "",
                        settingsClass = properties.getProperty("settingsClass"),
                        website = properties.getProperty("pluginWebsite"),
                        author = properties.getProperty("author"),
                        version = properties.getProperty("version") ?: "1.0",
                        versionCode = properties.getProperty("versionCode")?.toIntOrNull()
                            ?: 1
                    )
                    extensions[newExtension] = null
                    return@withContext newExtension
                } else {
                    destinationFolder.deleteRecursively()
                    throw IllegalStateException("Invalid plugin structure in ${zipFile.name}")
                }
            } catch (e: Exception) {
                destinationFolder.deleteRecursively()
                e.printStackTrace()
            }
            return@withContext null
        }


    private suspend fun readFileFromZip(zipFile: File, fileName: String): String? {
        var content: String? = null
        withContext(Dispatchers.IO) {
            ZipFile(zipFile).use { zip ->
                val entry = zip.getEntry(fileName)
                if (entry != null) {
                    zip.getInputStream(entry).use { inputStream ->
                        content = inputStream.bufferedReader().readText()
                    }
                }
            }
        }
        return content
    }

    private suspend fun getProperties(zipFile: File): Properties = withContext(Dispatchers.IO) {
        val properties = Properties()

        ZipFile(zipFile).use { zip ->
            val entry = zip.getEntry("manifest.properties")
            if (entry != null) {
                zip.getInputStream(entry).use { inputStream ->
                    properties.load(inputStream)
                }
            }
        }

        return@withContext properties
    }
}