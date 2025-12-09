package com.rk.extension

import android.app.Application
import androidx.compose.runtime.mutableStateMapOf
import androidx.core.content.pm.PackageInfoCompat
import com.rk.file.FileObject
import com.rk.file.copyToTempDir
import com.rk.settings.Preference
import com.rk.utils.application
import com.rk.utils.errorDialog
import com.rk.utils.isMainThread
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.collections.iterator

internal val loadedExtensions = mutableStateMapOf<LocalExtension, ExtensionAPI>()

fun LocalExtension.load(application: Application, init: Boolean = false) = run {
    val classLoader =
        try {
            classLoader(application.classLoader)
        } catch (err: Exception) {
            return@run Result.failure(
                RuntimeException(
                    "Failed to create ClassLoader for extension '${info.name}'. Details: ${err.message}",
                    err,
                )
            )
        }

    if (isMainThread()) {
        return@run Result.failure(
            RuntimeException(
                "Attempted to load extension '${info.name}' on the main thread. Extension loading must be performed on a background thread."
            )
        )
    }

    val minAppVersion = info.minAppVersion
    val maxAppVersion = info.targetAppVersion

    val xedVersionCode =
        PackageInfoCompat.getLongVersionCode(application.packageManager.getPackageInfo(application.packageName, 0))

    if (!(minAppVersion <= xedVersionCode && maxAppVersion <= xedVersionCode)) {
        Preference.setBoolean("ext_${info.id}", false)
        return@run Result.failure(
            RuntimeException(
                "Extension '${info.name}' (${info.version}) is not compatible with this version of Xed-Editor (min: $minAppVersion, max: $maxAppVersion, Xed-Editor: $xedVersionCode)"
            )
        )
    }

    val mainClassInstance =
        try {
            classLoader.loadClass(info.mainClass)
        } catch (err: Exception) {
            return@run Result.failure(
                RuntimeException(
                    "Failed to load main class '${info.mainClass}' for extension '${info.name}'. Details: ${err.message}",
                    err,
                )
            )
        }

    if (ExtensionAPI::class.java.isAssignableFrom(mainClassInstance)) {
        val instance =
            mainClassInstance.getDeclaredConstructor().newInstance() as? ExtensionAPI
                ?: return@run Result.failure(
                    RuntimeException(
                        "Failed to instantiate main class '${mainClassInstance.name}' for extension '${info.name}'. The class could not be cast to ExtensionAPI. Ensure it implements the ExtensionAPI interface and has a public no-argument constructor."
                    )
                )

        try {
            instance.onPluginLoaded(this)
        } catch (err: ClassNotFoundException) {
            return@run Result.failure(
                RuntimeException(
                    "Failed to initialize plugin '${info.name}': A required class was not found. This might indicate a missing dependency or an issue with the extension's packaging. Details: ${err.message}",
                    err,
                )
            )
        } catch (err: NoClassDefFoundError) {
            return@run Result.failure(
                RuntimeException(
                    "Failed to initialize plugin '${info.name}': A class definition was not found. This usually means a class was available at compile time but is missing at runtime. Check the extension's dependencies. Details: ${err.message}",
                    err,
                )
            )
        } catch (err: Exception) {
            return@run Result.failure(
                RuntimeException(
                    "Failed to initialize plugin '${info.name}': An unexpected error occurred during the onPluginLoaded call. Details: ${err.message}",
                    err,
                )
            )
        }

        loadedExtensions[this] = instance
        Result.success(instance)
    } else {
        Result.failure(
            RuntimeException(
                "The main class '${info.mainClass}' of plugin '${info.name}' does not implement the ExtensionAPI interface. Please ensure the main class correctly implements this interface."
            )
        )
    }
}

suspend fun ExtensionManager.installExtension(fileObject: FileObject, isDev: Boolean = false) = run {
    val file = fileObject.copyToTempDir()
    installExtension(file, isDev).also { file.delete() }
}

suspend fun ExtensionManager.loadAllExtensions() =
    withContext(Dispatchers.Default) {
        for ((id, extension) in localExtensions) {
           launch(Dispatchers.IO) {
               if (Preference.getBoolean("ext_$id", false)) {
                   extension.load(application!!).onFailure {
                       errorDialog(it.message ?: "Failed to load extension '${extension.name}'")
                   }
               }
           }
        }
    }
