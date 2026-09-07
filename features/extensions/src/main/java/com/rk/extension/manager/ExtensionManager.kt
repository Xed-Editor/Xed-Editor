package com.rk.extension.manager

import android.app.Application
import android.content.Context
import androidx.compose.runtime.mutableStateMapOf
import androidx.core.content.edit
import androidx.core.content.pm.PackageInfoCompat
import com.rk.DefaultScope
import com.rk.common.XedPackage
import com.rk.events.Events
import com.rk.extension.Extension
import com.rk.extension.ExtensionAPI
import com.rk.extension.ExtensionError
import com.rk.extension.ExtensionEvent
import com.rk.extension.InstallResult
import com.rk.extension.LocalExtension
import com.rk.extension.StoreExtension
import com.rk.extension.UpdatableExtension
import com.rk.extension.model.ExtensionId
import com.rk.extension.model.ExtensionManifest
import com.rk.extension.model.PackageCache
import com.rk.file.FileOperations
import com.rk.file.FileWrapper
import com.rk.resources.getString
import com.rk.resources.strings
import com.rk.utils.errorDialog
import com.rk.utils.logError
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import java.io.File

private val Context.localDir: File
    get() = filesDir.parentFile!!.resolve("local").apply { if (!exists()) mkdirs() }

val Context.extensionDir: File
    get() = localDir.resolve("extensions").apply { if (!exists()) mkdirs() }

internal fun Context.compiledDexDir() = extensionDir.resolve("oat")

data class LoadedExtension(val api: ExtensionAPI, val scope: CoroutineScope)

open class ExtensionManager(private val context: Application) : CoroutineScope by CoroutineScope(Dispatchers.IO) {
    private val mutex = Mutex()
    val installedExtensions = mutableStateMapOf<ExtensionId, LocalExtension>()
    val storeExtension = mutableStateMapOf<ExtensionId, StoreExtension>()
    val json = Json {
        ignoreUnknownKeys = true
        allowTrailingComma = true
    }

    val loadedExtensions = mutableStateMapOf<LocalExtension, LoadedExtension?>()

    private val disabledPrefs by lazy {
        context.getSharedPreferences("disabled_extensions", Context.MODE_PRIVATE)
    }

    fun isExtensionCrashed(extension: Extension): Boolean {
        return disabledPrefs.getBoolean(extension.id, false)
    }

    fun setExtensionCrashed(extension: Extension, disabled: Boolean) {
        disabledPrefs.edit { putBoolean(extension.id, disabled) }
        if (disabled) {
            launch {
                Events.publish(ExtensionEvent.Crashed(extension))
            }
        }
    }

    fun isInstalled(extensionId: ExtensionId) = installedExtensions.containsKey(extensionId)

    fun getExtension(extensionId: ExtensionId): Extension? {
        val local = installedExtensions[extensionId]
        val store = storeExtension[extensionId]

        return when {
            local != null && store != null -> UpdatableExtension(local, store)
            local != null -> local
            store != null -> store
            else -> null
        }
    }

    fun getSyncedExtensions(): List<Extension> {
        val allIds = installedExtensions.keys + storeExtension.keys
        return allIds.mapNotNull { id -> getExtension(id) }
    }

    fun getLocalExtensions(): List<Extension> {
        return getSyncedExtensions().filterIsInstance<LocalExtension>()
    }

    fun getStoreExtensions(): List<Extension> {
        return getSyncedExtensions().filter { it is StoreExtension || it is UpdatableExtension }
    }

    private suspend fun calcSize(dir: File): Long {
        return FileOperations.calculateContent(FileWrapper(dir)).totalSize
    }

    private fun resolveCache(dir: File): PackageCache {
        val cacheFile = dir.resolve("cache.json")

        if (!cacheFile.exists() || !cacheFile.isFile) {
            return PackageCache()
        }

        return runCatching {
            json.decodeFromString<PackageCache>(cacheFile.readText())
        }
            .getOrElse {
                PackageCache()
            }
    }

    private fun writeCache(dir: File, cache: PackageCache) {
        val cacheFile = dir.resolve("cache.json")
        cacheFile.writeText(json.encodeToString(cache))
    }

    suspend fun invalidateSize(extension: Extension) {
        if (extension is StoreExtension) return

        withContext(Dispatchers.IO) {
            val dir = context.extensionDir.resolve(extension.id)
            val cache = resolveCache(dir)
            val newSize = calcSize(dir)
            writeCache(dir, cache.copy(size = newSize))

            withContext(Dispatchers.Main) {
                installedExtensions[extension.id]?.size = newSize
            }
        }
    }

    suspend fun indexLocalExtensions() = mutex.withLock {
        val newExtensions =
            withContext(Dispatchers.IO) {
                val map = mutableMapOf<ExtensionId, LocalExtension>()
                val extensionFolders = context.extensionDir.listFiles()?.filter { it.isDirectory }
                extensionFolders?.forEach { dir ->
                    val extensionJson = dir.resolve("manifest.json")

                    if (extensionJson.exists()) {
                        runCatching {
                            val extensionManifest = json.decodeFromString<ExtensionManifest>(extensionJson.readText())
                            val extensionCache = resolveCache(dir)
                            val size =
                                extensionCache.size
                                    ?: calcSize(dir).also {
                                        writeCache(dir, extensionCache.copy(size = it))
                                    }
                            val extension =
                                LocalExtension(
                                    manifest = extensionManifest,
                                    installPath = dir.absolutePath,
                                    initSize = size,
                                    createdAt = extensionCache.createdAt,
                                    updatedAt = extensionCache.updatedAt,
                                )
                            map[extensionManifest.id] = extension
                        }
                            .onFailure {
                                logError(it)
                            }
                    }
                }
                map
            }
        withContext(Dispatchers.Main) {
            val toRemove = installedExtensions.keys.filter { it !in newExtensions }
            toRemove.forEach { installedExtensions.remove(it) }
            installedExtensions.putAll(newExtensions)
        }
    }

    suspend fun indexStoreExtensions() =
        withContext(Dispatchers.IO) {
            val extensions =
                runCatching {
                    StoreManager.fetchExtensions()
                }
                    .getOrNull() ?: return@withContext
            val newExtensions = extensions.associate { it.id to StoreExtension(it) }
            withContext(Dispatchers.Main) {
                val toRemove = storeExtension.keys.filter { it !in newExtensions }
                toRemove.forEach { storeExtension.remove(it) }
                storeExtension.putAll(newExtensions)
            }
        }

    @OptIn(ExperimentalSerializationApi::class)
    internal fun validateExtensionDir(dir: File): Result<ExtensionManifest> {
        val extensionJson = dir.resolve("manifest.json")
        if (!extensionJson.exists()) {
            return Result.failure(Exception("Missing manifest.json"))
        }
        val extensionManifest = runCatching {
            json.decodeFromString<ExtensionManifest>(extensionJson.readText())
        }
            .getOrElse { e ->
                return Result.failure(e)
            }

        val hasApk = dir.listFiles()?.any { it.extension == "apk" } == true
        if (!hasApk) {
            return Result.failure(Exception("Missing APK file"))
        }

        return Result.success(extensionManifest)
    }

    suspend fun installExtensionFromZip(xedFile: File): InstallResult =
        withContext(Dispatchers.IO) {
            // Extract to temp dir first
            val tempDir = File(context.cacheDir, "ext_temp_${System.currentTimeMillis()}")
            tempDir.mkdirs()

            try {
                XedPackage.extract(xedFile, tempDir)
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

            var performedUpdate = false
            var oldCreatedAt: Long? = null
            if (targetDir.exists()) {
                oldCreatedAt = resolveCache(targetDir).createdAt
                uninstallExtension(extensionInfo.id, update = true)
                performedUpdate = true
            }

            val pm = context.packageManager
            val xedVersionCode = PackageInfoCompat.getLongVersionCode(pm.getPackageInfo(context.packageName, 0))

            val minAppVersion = extensionInfo.minAppVersion
            if (minAppVersion != null && xedVersionCode < minAppVersion) {
                return@withContext InstallResult.Error(ExtensionError.OUTDATED_CLIENT)
            }

            dir.copyRecursively(targetDir, overwrite = true)

            val size = calcSize(targetDir)
            val newExtensionCache =
                PackageCache(
                        createdAt = oldCreatedAt ?: System.currentTimeMillis(),
                        updatedAt = System.currentTimeMillis(),
                        size = size,
                    )
                    .also {
                        writeCache(targetDir, it)
                    }

            val extension =
                LocalExtension(
                    manifest = extensionInfo,
                    installPath = targetDir.absolutePath,
                    initSize = size,
                    createdAt = newExtensionCache.createdAt,
                    updatedAt = newExtensionCache.updatedAt,
                )
            installedExtensions[extensionInfo.id] = extension

            Events.publish(ExtensionEvent.Installed(extension))

            InstallResult.Success(extension, performedUpdate)
        }

    suspend fun uninstallExtension(extensionId: ExtensionId, update: Boolean = false) =
        withContext(Dispatchers.IO) {
            try {
                val extension =
                    installedExtensions[extensionId]
                        ?: return@withContext Result.failure(Exception("Extension not found"))

                val loadedExtension = loadedExtensions[extension]
                runCatching {
                    loadedExtension?.api?.onDispose()
                    if (update) {
                        loadedExtension?.api?.beforeUpdate()
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
                installedExtensions.remove(extensionId)

                DefaultScope.launch { Events.publish(ExtensionEvent.Uninstalled(extension, update)) }

                context.compiledDexDir().deleteWithPackageName(extension.manifest.id)
                disabledPrefs.edit { remove(extensionId) }

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
