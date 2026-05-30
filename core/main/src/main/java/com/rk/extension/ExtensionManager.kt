package com.rk.extension

import android.app.Application
import android.content.Context
import androidx.compose.runtime.mutableStateMapOf
import androidx.core.content.pm.PackageInfoCompat
import com.rk.file.child
import com.rk.resources.getString
import com.rk.resources.strings
import com.rk.utils.errorDialog
import java.io.File
import java.util.zip.ZipFile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json

private val Context.localDir: File
    get() = filesDir.parentFile!!.resolve("local").apply { if (!exists()) mkdirs() }

val Context.extensionDir: File
    get() = localDir.resolve("extensions").apply { if (!exists()) mkdirs() }

internal fun Context.compiledDexDir() = extensionDir.resolve("oat")

data class LoadedExtension(val api: ExtensionAPI, val scope: CoroutineScope)

open class ExtensionManager(private val context: Application) : CoroutineScope by CoroutineScope(Dispatchers.IO) {
    private val mutex = Mutex()
    val localExtensions = mutableStateMapOf<ExtensionId, LocalExtension>()
    val storeExtension = mutableStateMapOf<ExtensionId, StoreExtension>()
    val json = Json { ignoreUnknownKeys = true }

    val loadedExtensions = mutableStateMapOf<LocalExtension, LoadedExtension?>()

    init {
        launch(Dispatchers.IO) {
            runCatching {
                indexLocalExtensions()
                indexStoreExtensions()
            }
        }
    }

    fun isInstalled(extensionId: ExtensionId) = localExtensions.containsKey(extensionId)

    fun getExtension(extensionId: ExtensionId): Extension? {
        val local = localExtensions[extensionId]
        val store = storeExtension[extensionId]

        return when {
            local != null && store != null -> UpdatableExtension(local, store)
            local != null -> local
            store != null -> store
            else -> null
        }
    }

    fun getSyncedExtensions(): List<Extension> {
        val allIds = localExtensions.keys + storeExtension.keys
        return allIds.mapNotNull { id -> getExtension(id) }
    }

    fun getLocalExtensions(): List<Extension> {
        return getSyncedExtensions().filterIsInstance<LocalExtension>()
    }

    fun getStoreExtensions(): List<Extension> {
        return getSyncedExtensions().filter { it is StoreExtension || it is UpdatableExtension }
    }

    suspend fun indexLocalExtensions() =
        mutex.withLock {
            localExtensions.clear()

            withContext(Dispatchers.IO) {
                val extensionFolders = context.extensionDir.listFiles()?.filter { it.isDirectory }
                extensionFolders?.forEach { dir ->
                    val extensionJson = dir.resolve("manifest.json")
                    if (extensionJson.exists()) {
                        runCatching {
                            val extensionManifest = json.decodeFromString<ExtensionManifest>(extensionJson.readText())
                            val extension = LocalExtension(manifest = extensionManifest, installPath = dir.absolutePath)
                            localExtensions[extensionManifest.id] = extension
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

    suspend fun installStoreExtension(context: Context, extension: StoreExtension) = runCatching {
        val dir = context.cacheDir.child("${extension.id}.zip")
        ExtensionRegistry.downloadZip(extension.manifest, dir)
        installExtensionFromZip(dir)
    }

    @OptIn(ExperimentalSerializationApi::class)
    internal fun validateExtensionDir(dir: File): Result<ExtensionManifest> {
        val extensionJson = dir.resolve("manifest.json")
        if (!extensionJson.exists()) {
            return Result.failure(Exception("Missing manifest.json"))
        }
        val extensionManifest =
            runCatching { json.decodeFromString<ExtensionManifest>(extensionJson.readText()) }
                .getOrElse { e ->
                    return Result.failure(e)
                }

        val hasApk = dir.listFiles()?.any { it.extension == "apk" } == true
        if (!hasApk) {
            return Result.failure(Exception("Missing APK file"))
        }

        return Result.success(extensionManifest)
    }

    suspend fun installExtensionFromZip(zipFile: File): InstallResult =
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
            } catch (e: Exception) {
                e.printStackTrace()
                errorDialog(e)
                InstallResult.ValidationFailed(e)
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
                uninstallExtension(extensionInfo.id, update = true)
            }

            val pm = context.packageManager
            val xedVersionCode = PackageInfoCompat.getLongVersionCode(pm.getPackageInfo(context.packageName, 0))

            if (extensionInfo.minAppVersion != null && xedVersionCode < extensionInfo.minAppVersion) {
                return@withContext InstallResult.Error(ExtensionError.OUTDATED_CLIENT)
            } else if (extensionInfo.maxAppVersion != null && xedVersionCode > extensionInfo.maxAppVersion) {
                return@withContext InstallResult.Error(ExtensionError.OUTDATED_EXTENSION)
            }

            dir.copyRecursively(targetDir, overwrite = true)

            val extension = LocalExtension(manifest = extensionInfo, installPath = targetDir.absolutePath)
            localExtensions[extensionInfo.id] = extension

            InstallResult.Success(extension)
        }

    suspend fun uninstallExtension(extensionId: ExtensionId, update: Boolean = false) =
        withContext(Dispatchers.IO) {
            try {
                val extension =
                    localExtensions[extensionId] ?: return@withContext Result.failure(Exception("Extension not found"))

                val loadedExtension = loadedExtensions[extension]
                runCatching {
                        if (update) {
                            loadedExtension?.api?.onUpdated()
                        } else {
                            loadedExtension?.api?.onUninstalled()
                        }
                    }
                    .onFailure { errorDialog(title = strings.ext_cleanup_failed.getString(), throwable = it) }
                loadedExtensions[extension]?.scope?.cancel()

                val extensionDir = File(extension.installPath)
                if (!extensionDir.exists()) {
                    return@withContext Result.failure(Exception("Extension directory not found"))
                }

                extensionDir.deleteRecursively()
                localExtensions.remove(extensionId)
                context.compiledDexDir().deleteWithPackageName(extension.manifest.id)

                Result.success(Unit)
            } catch (err: Exception) {
                Result.failure(Exception("Failed to uninstall extension: ${err.message}", err))
            }
        }

    private fun File.deleteWithPackageName(pkgName: String) {
        if (isDirectory) {
            listFiles()?.forEach { it.deleteWithPackageName(pkgName) }
            delete()
        } else if (name.startsWith(pkgName)) delete()
    }
}
