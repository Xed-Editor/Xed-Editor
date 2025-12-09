package com.rk.extension

import android.app.Application
import android.content.Context
import androidx.compose.runtime.mutableStateMapOf
import androidx.core.content.pm.PackageInfoCompat
import com.google.gson.Gson
import java.io.File
import java.util.zip.ZipFile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

private val Context.localDir: File
    get() = filesDir.parentFile!!.resolve("local").apply { if (!exists()) mkdirs() }

val Context.extensionDir: File
    get() = localDir.resolve("extensions").apply { if (!exists()) mkdirs() }

internal fun Context.compiledDexDir() = extensionDir.resolve("oat")

open class ExtensionManager(private val context: Application) : CoroutineScope by CoroutineScope(Dispatchers.IO) {

    private val mutex = Mutex()
    val localExtensions = mutableStateMapOf<ExtensionId, LocalExtension>()
    val storeExtension = mutableStateMapOf<ExtensionId, StoreExtension>()

    init {
        launch(Dispatchers.IO) {
            runCatching {
                indexLocalExtensions()
                indexStoreExtensions()
            }
        }
    }

    internal fun validateExtensionDir(dir: File): Result<PluginInfo> {
        var pluginJson = dir.resolve("plugin.json")
        if (!pluginJson.exists()) {
            if (dir.resolve("manifest.json").exists()) {
                pluginJson = dir.resolve("manifest.json")
            } else {
                return Result.failure(Exception("Missing manifest.json"))
            }
        }
        val pluginInfo =
            runCatching { Gson().fromJson(pluginJson.readText(), PluginInfo::class.java) }
                .getOrElse {
                    return Result.failure(Exception("Invalid plugin.json", it))
                }

        val hasApk = dir.listFiles()?.any { it.extension == "apk" } == true
        if (!hasApk) {
            return Result.failure(Exception("Missing APK file"))
        }

        return Result.success(pluginInfo)
    }

    suspend fun installExtension(zipFile: File): InstallResult =
        withContext(Dispatchers.IO) {
            // Extract to temp dir first
            val tempDir = File(context.cacheDir, "ext_temp_${System.currentTimeMillis()}")
            tempDir.mkdirs()

            try {
                ZipFile(zipFile).use { zip ->
                    zip.entries().asSequence().forEach { entry ->
                        if (!entry.isDirectory) {
                            val target = tempDir.resolve(entry.name)
                            target.parentFile?.mkdirs()
                            zip.getInputStream(entry).use { input ->
                                target.outputStream().use { output -> input.copyTo(output) }
                            }
                        }
                    }
                }
                installExtensionFromDir(tempDir)
            } finally {
                tempDir.deleteRecursively()
            }
        }

    suspend fun installExtensionFromDir(dir: File): InstallResult =
        withContext(Dispatchers.IO) {
            val validation = validateExtensionDir(dir)
            if (validation.isFailure) {
                return@withContext InstallResult.ValidationFailed(validation.exceptionOrNull())
            }

            val pluginInfo = validation.getOrThrow()
            val targetDir = context.extensionDir.resolve(pluginInfo.id)

            if (targetDir.exists()) {
                return@withContext InstallResult.AlreadyInstalled(pluginInfo.id)
            }

            val pm = context.packageManager
            val xedVersionCode = PackageInfoCompat.getLongVersionCode(pm.getPackageInfo(context.packageName, 0))

            if (pluginInfo.minAppVersion != -1 && xedVersionCode < pluginInfo.minAppVersion) {
                return@withContext InstallResult.Error(
                    "Xed-Editor is outdated. Requires >= ${pluginInfo.minAppVersion}, current $xedVersionCode"
                )
            } else if (pluginInfo.targetAppVersion != -1 && xedVersionCode > pluginInfo.targetAppVersion) {
                return@withContext InstallResult.Error(
                    "Plugin ${pluginInfo.name} was made for older Xed-Editor. Ask developer to update."
                )
            }

            dir.copyRecursively(targetDir, overwrite = true)

            val extension = LocalExtension(info = pluginInfo, installPath = targetDir.absolutePath)
            localExtensions[pluginInfo.id] = extension

            InstallResult.Success(extension)
        }

    suspend fun uninstallExtension(extensionId: ExtensionId) =
        withContext(Dispatchers.IO) {
            try {
                val extension =
                    localExtensions[extensionId] ?: return@withContext Result.failure(Exception("Extension not found"))

                val extensionDir = File(extension.installPath)
                if (!extensionDir.exists()) {
                    return@withContext Result.failure(Exception("Extension directory not found"))
                }

                extensionDir.deleteRecursively()
                localExtensions.remove(extensionId)
                context.compiledDexDir().deleteWithPackageName(extension.info.id)

                Result.success(Unit)
            } catch (err: Exception) {
                Result.failure(Exception("Failed to uninstall extension: ${err.message}", err))
            }
        }

    suspend fun indexLocalExtensions() =
        mutex.withLock {
            localExtensions.clear()
            indexExtensionsInDir(context.extensionDir)
        }

    private suspend fun indexExtensionsInDir(baseDir: File) =
        withContext(Dispatchers.IO) {
            baseDir.listFiles()?.forEach { dir ->
                if (dir.isDirectory) {
                    var pluginJson = dir.resolve("plugin.json")
                    if (pluginJson.exists().not()) {
                        pluginJson = dir.resolve("manifest.json")
                    }
                    if (pluginJson.exists()) {
                        runCatching {
                            val pluginInfo = Gson().fromJson(pluginJson.readText(), PluginInfo::class.java)
                            val extension = LocalExtension(info = pluginInfo, installPath = dir.absolutePath)
                            localExtensions[pluginInfo.id] = extension
                        }
                    }
                }
            }
        }

    suspend fun indexStoreExtensions() =
        withContext(Dispatchers.IO) {
            val extensions = PluginRegistry.fetchExtensions()
            storeExtension.clear()
            storeExtension.putAll(extensions.associate { it.id to StoreExtension(it) })
        }

    private fun File.deleteWithPackageName(pkgName: String) {
        if (isDirectory) {
            listFiles()?.forEach { it.deleteWithPackageName(pkgName) }
            delete()
        } else if (name.startsWith(pkgName)) delete()
    }

    fun isInstalled(extensionId: ExtensionId) = localExtensions.containsKey(extensionId)

    fun getExtensionInfo(extensionId: ExtensionId) =
        localExtensions[extensionId]?.info ?: storeExtension[extensionId]?.info
}
