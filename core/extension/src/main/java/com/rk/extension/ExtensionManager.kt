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

    internal fun validateExtensionDir(dir: File): Result<ExtensionInfo> {
        val extensionJson = dir.resolve("manifest.json")
        if (!extensionJson.exists()) {
            return Result.failure(Exception("Missing manifest.json"))
        }
        val extensionInfo =
            runCatching { Gson().fromJson(extensionJson.readText(), ExtensionInfo::class.java) }
                .getOrElse {
                    return Result.failure(Exception("Invalid manifest.json", it))
                }

        val hasApk = dir.listFiles()?.any { it.extension == "apk" } == true
        if (!hasApk) {
            return Result.failure(Exception("Missing APK file"))
        }

        return Result.success(extensionInfo)
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

            val extensionInfo = validation.getOrThrow()
            val targetDir = context.extensionDir.resolve(extensionInfo.id)

            if (targetDir.exists()) {
                uninstallExtension(extensionInfo.id)
                // return@withContext InstallResult.AlreadyInstalled(extensionInfo.id)
            }

            val pm = context.packageManager
            val xedVersionCode = PackageInfoCompat.getLongVersionCode(pm.getPackageInfo(context.packageName, 0))

            if (extensionInfo.minAppVersion != -1 && xedVersionCode < extensionInfo.minAppVersion) {
                return@withContext InstallResult.Error(ExtensionError.OUTDATED_CLIENT)
            } else if (extensionInfo.targetAppVersion != -1 && xedVersionCode > extensionInfo.targetAppVersion) {
                return@withContext InstallResult.Error(ExtensionError.OUTDATED_EXTENSION)
            }

            dir.copyRecursively(targetDir, overwrite = true)

            val extension = LocalExtension(info = extensionInfo, installPath = targetDir.absolutePath)
            localExtensions[extensionInfo.id] = extension

            InstallResult.Success(extension)
        }

    suspend fun uninstallExtension(extensionId: ExtensionId) =
        withContext(Dispatchers.IO) {
            try {
                val extension =
                    localExtensions[extensionId] ?: return@withContext Result.failure(Exception("Extension not found"))

                loadedExtensions[extension]?.onUninstalled(extension)

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
                    val extensionJson = dir.resolve("manifest.json")
                    if (extensionJson.exists()) {
                        runCatching {
                            val extensionInfo = Gson().fromJson(extensionJson.readText(), ExtensionInfo::class.java)
                            val extension = LocalExtension(info = extensionInfo, installPath = dir.absolutePath)
                            localExtensions[extensionInfo.id] = extension
                        }
                    }
                }
            }
        }

    suspend fun indexStoreExtensions() =
        withContext(Dispatchers.IO) {
            val extensions = ExtensionRegistry.fetchExtensions()
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
