package com.rk.extension

import android.content.Context
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.core.content.pm.PackageInfoCompat
import com.google.gson.Gson
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.util.zip.ZipFile

private val Context.localDir
    get() = filesDir.parentFile!!.resolve("local").apply {
        if (!exists()) mkdirs()
    }

val Context.extensionDir
    get() = localDir.resolve("extensions").apply {
        if (!exists()) mkdirs()
    }

val Context.devExtensionDir
    get() = localDir.resolve("dev_extensions").apply {
        if (!exists()) mkdirs()
    }

internal fun Context.compiledDexDir(forDevExtension: Boolean = false) = run {
    if (forDevExtension) devExtensionDir.resolve("oat") else extensionDir.resolve("oat")
}

open class ExtensionManager(
    private val context: Context
) : CoroutineScope by CoroutineScope(Dispatchers.IO) {

    private val mutex = Mutex()
    private val localExtensions = mutableStateMapOf<ExtensionId, LocalExtension>()

    val installedExtensions by derivedStateOf { localExtensions.values }

    init {
        launch {
            indexLocalExtensions()
        }
    }

    internal suspend fun validateExtensionZip(zipFile: File) = withContext(Dispatchers.IO) {
        try {
            ZipFile(zipFile).use { zip ->
                var hasPluginJson = false
                var hasApk = false
                var pluginInfo: PluginInfo? = null

                zip.entries().asSequence().forEach { entry ->
                    when {
                        entry.name == "plugin.json" -> {
                            hasPluginJson = true
                            val content = zip
                                .getInputStream(entry)
                                .readBytes()
                                .toString(Charsets.UTF_8)
                            pluginInfo = Gson().fromJson(content, PluginInfo::class.java)
                        }

                        entry.name.endsWith(".apk") && !entry.isDirectory -> hasApk = true
                    }
                }

                when {
                    !hasPluginJson -> Result.failure(Exception("Missing plugin.json"))
                    !hasApk -> Result.failure(Exception("Missing APK file"))
                    pluginInfo == null -> Result.failure(Exception("Invalid plugin.json"))
                    else -> Result.success(pluginInfo)
                }
            }
        } catch (err: Exception) {
            Result.failure(Exception("Failed to validate zip: ${err.message}", err))
        }
    }

    suspend fun installExtension(
        zipFile: File,
        isDev: Boolean = false
    ) = withContext(Dispatchers.IO) {
        try {
            val validation = validateExtensionZip(zipFile)
            if (validation.isFailure) {
                println(validation.exceptionOrNull())
                return@withContext InstallResult.ValidationFailed(validation.exceptionOrNull())
            }

            val pluginInfo = validation.getOrThrow()
            val extensionDir = with(context) {
                if (isDev) {
                    devExtensionDir.resolve(pluginInfo.id)
                } else {
                    extensionDir.resolve(pluginInfo.id)
                }
            }

            if (extensionDir.exists()) {
                return@withContext InstallResult.AlreadyInstalled(pluginInfo.id)
            }

            val pm = context.applicationContext.packageManager
            val xedVersionCode = PackageInfoCompat.getLongVersionCode(
                pm.getPackageInfo(context.packageName, 0)
            )

            if (xedVersionCode < pluginInfo.minAppVersion) {
                return@withContext InstallResult.Error(
                    "Xed-Editor is outdated minimum version code required " +
                            "is ${pluginInfo.minAppVersion} while current version code is $xedVersionCode"
                )
            } else if (xedVersionCode > pluginInfo.targetAppVersion) {
                return@withContext InstallResult.Error(
                    "Plugin ${pluginInfo.name} was made for an older version of Xed-Editor, " +
                            "ask the plugin developer to update the plugin"
                )
            }

            extensionDir.mkdirs()

            ZipFile(zipFile).use { zip ->
                zip.entries().asSequence().forEach { entry ->
                    if (!entry.isDirectory) {
                        val targetFile = extensionDir.resolve(entry.name)
                        targetFile.parentFile?.mkdirs()
                        zip.getInputStream(entry).use { input ->
                            targetFile.outputStream().use { output ->
                                input.copyTo(output)
                            }
                        }
                    }
                }
            }

            val extension = LocalExtension(
                info = pluginInfo,
                installPath = extensionDir.absolutePath,
                isDevExtension = isDev
            )

            localExtensions[pluginInfo.id] = extension

            val apkPkgName = extension.getApkPackageInfo(context).packageName
            if (apkPkgName != pluginInfo.id) {
                uninstallExtension(pluginInfo.id)
                return@withContext InstallResult.Error("APK package name ($apkPkgName) does not match plugin ID (${pluginInfo.id})")
            }

            InstallResult.Success(extension)
        } catch (err: Exception) {
            InstallResult.Error("Failed to install extension: ${err.message}")
        }
    }

    suspend fun uninstallExtension(extensionId: ExtensionId) = withContext(Dispatchers.IO) {
        try {
            val extension = installedExtensions.find { it.id == extensionId }
                ?: return@withContext Result.failure(Exception("Extension not found"))

            val extensionDir = File(extension.installPath)
            if (!extensionDir.exists()) {
                return@withContext Result.failure(Exception("Extension directory not found"))
            }

            extensionDir.deleteRecursively()
            localExtensions.remove(extensionId)
            context.compiledDexDir(extension.isDevExtension)
                .deleteWithPackageName(extension.info.id)

            Result.success(Unit)
        } catch (err: Exception) {
            Result.failure(Exception("Failed to uninstall extension: ${err.message}", err))
        }
    }

    private fun File.deleteWithPackageName(pkgName: String) {
        if (isDirectory) {
            listFiles()?.forEach { it.deleteWithPackageName(pkgName) }
            delete()
        } else {
            if (name.startsWith(pkgName)) {
                delete()
            }
        }
    }

    fun isInstalled(extensionId: ExtensionId) = installedExtensions.any { it.id == extensionId }
    fun isDevExtension(extensionId: ExtensionId) = installedExtensions.any {
        it.id == extensionId && it.isDevExtension
    }

    fun getExtensionInfo(extensionId: ExtensionId) = installedExtensions.find {
        it.id == extensionId
    }?.info

    suspend fun indexLocalExtensions() = mutex.withLock {
        indexExtensionsInDir(context.extensionDir)
        indexExtensionsInDir(context.devExtensionDir)
    }

    private suspend fun indexExtensionsInDir(extensionDir: File) = withContext(Dispatchers.IO) {
        extensionDir.listFiles()?.forEach { file ->
            if (file.exists() && file.isDirectory) {
                val pluginJson = file.resolve("plugin.json")
                if (pluginJson.exists()) {
                    val content = pluginJson.readText()
                    val pluginInfo = Gson().fromJson(content, PluginInfo::class.java)
                    val extension = LocalExtension(
                        info = pluginInfo,
                        installPath = file.absolutePath,
                        isDevExtension = false
                    )
                    localExtensions[pluginInfo.id] = extension
                }
            }
        }
    }
}
