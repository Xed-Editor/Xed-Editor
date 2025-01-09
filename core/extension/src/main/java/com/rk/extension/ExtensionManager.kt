package com.rk.extension

import android.app.Application
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import com.rk.libcommons.application
import com.rk.libcommons.postIO
import com.rk.settings.PreferencesData
import com.rk.settings.PreferencesKeys
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.util.Properties
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipInputStream

object ExtensionManager : ExtensionAPI() {
    val isLoaded = mutableStateOf(false)
    val extensions = mutableStateMapOf<Extension, ExtensionAPI?>()

    init {
        if (isLoaded.value.not() && isPluginEnabled()) {
            postIO {
                loadExistingPlugins(application!!)
                withContext(Dispatchers.Main) {
                    isLoaded.value = true
                }
            }
        }
    }

    suspend fun loadExistingPlugins(context: Application): Map<Extension, ExtensionAPI?> =
        withContext(Dispatchers.IO) {
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

                    val ext = Extension(
                        manifest = manifestFile,
                        dexFiles = dexFiles,
                        name = properties.getProperty("name") ?: "Unknown",
                        packageName = properties.getProperty("packageName"),
                        mainClass = properties.getProperty("mainClass") ?: "",
                        settingsClass = properties.getProperty("settingsClass"),
                        website = properties.getProperty("pluginWebsite"),
                        author = properties.getProperty("author"),
                        version = properties.getProperty("version") ?: "1.0",
                        versionCode = properties.getProperty("versionCode")?.toIntOrNull() ?: 1,
                        application = context
                    )

                    if (extensions[ext] == null) {
                        extensions[ext] = null
                    }
                }
            }

            extensions
        }

    private fun requiredByOthers(dex: File, except: Extension): Boolean {
        extensions.keys.forEach {
            if (it != except && it.dexFiles.contains(dex)) {
                return true
            }
        }
        return false
    }

    suspend fun deletePlugin(extension: Extension) = withContext(Dispatchers.IO) {
        extension.dexFiles.forEach { dex ->
            if (requiredByOthers(dex, extension).not()) {
                dex.delete()
            }
        }
        extension.manifest.parentFile!!.deleteRecursively()
        extensions.remove(extension)
        runCatching {
            PreferencesData.removeKey("ext_" + extension.packageName)
        }
    }

    @OptIn(DelicateCoroutinesApi::class)
    suspend fun installPlugin(context: Application, zipFile: File): Extension? =
        withContext(Dispatchers.IO) {

            if (isPluginEnabled().not()){
                return@withContext null
            }

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
                    val newExtension = Extension(
                        manifest = manifestFile,
                        dexFiles = dexFiles,
                        name = properties.getProperty("name") ?: "Unknown",
                        packageName = properties.getProperty("packageName"),
                        mainClass = properties.getProperty("mainClass") ?: "",
                        settingsClass = properties.getProperty("settingsClass"),
                        website = properties.getProperty("pluginWebsite"),
                        author = properties.getProperty("author"),
                        version = properties.getProperty("version") ?: "1.0",
                        versionCode = properties.getProperty("versionCode")?.toIntOrNull() ?: 1,
                        application = context
                    )
                    extensions[newExtension] = null
                    Extension.executeExtensions(context, GlobalScope)
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

    private fun isPluginEnabled():Boolean{
        return PreferencesData.getBoolean(PreferencesKeys.ENABLE_EXTENSIONS,false)
    }

    override fun onPluginLoaded() {
        if (isPluginEnabled().not()){
            return
        }

        postIO {
            extensions.forEach { (ext, instance) ->
                runCatching {
                    instance?.onPluginLoaded()
                }.onFailure { PluginException(ext,"Failed to call method onPluginLoaded() on plugin ${ext.name}",it) }
            }
        }

    }

    override fun onAppCreated() {
        if (isPluginEnabled().not()){
            return
        }

        postIO {
            extensions.forEach { (ext, instance) ->
                runCatching {
                    instance?.onAppCreated()
                }.onFailure { PluginException(ext,"Failed to call method onAppCreated() on plugin ${ext.name}",it) }
            }
        }
    }

    override fun onAppLaunched() {
        if (isPluginEnabled().not()){
            return
        }

        postIO {
            extensions.forEach { (ext, instance) ->
                runCatching {
                    instance?.onAppLaunched()
                }.onFailure { PluginException(ext,"Failed to call method onAppLaunched() on plugin ${ext.name}",it) }
            }
        }

    }

    override fun onAppPaused() {
        if (isPluginEnabled().not()){
            return
        }

        postIO {
            extensions.forEach { (ext, instance) ->
                runCatching {
                    instance?.onAppPaused()
                }.onFailure { PluginException(ext,"Failed to call method onAppPaused() on plugin ${ext.name}",it) }
            }
        }
    }

    override fun onAppResumed() {
        if (isPluginEnabled().not()){
            return
        }

        postIO {
            extensions.forEach { (ext, instance) ->
                runCatching {
                    instance?.onAppResumed()
                }.onFailure { PluginException(ext,"Failed to call method onAppResumed() on plugin ${ext.name}",it) }
            }
        }
    }

    override fun onAppDestroyed() {
        if (isPluginEnabled().not()){
            return
        }

        postIO {
            extensions.forEach { (ext, instance) ->
                runCatching {
                    instance?.onAppDestroyed()
                }.onFailure { PluginException(ext,"Failed to call method onAppDestroyed() on plugin ${ext.name}",it) }
            }
        }
    }

    override fun onLowMemory() {
        if (isPluginEnabled().not()){
            return
        }

        postIO {
            extensions.forEach { (ext, instance) ->
                runCatching {
                    instance?.onLowMemory()
                }.onFailure { PluginException(ext,"Failed to call method onLowMemory() on plugin ${ext.name}",it) }
            }
        }
    }
}