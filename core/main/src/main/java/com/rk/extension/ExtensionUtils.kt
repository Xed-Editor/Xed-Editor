package com.rk.extension

import android.app.Application
import androidx.core.content.pm.PackageInfoCompat
import com.rk.App
import com.rk.file.FileObject
import com.rk.file.copyToTempDir
import com.rk.utils.application
import com.rk.utils.errorDialog
import com.rk.utils.isMainThread
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

fun LocalExtension.load(application: Application, initialInstallation: Boolean = false) = run {
    if (isMainThread()) {
        return@run Result.failure(
            RuntimeException(
                "Attempted to load extension '${manifest.name}' on the main thread. Extension loading must be performed on a background thread."
            )
        )
    }

    val classLoader =
        try {
            classLoader(application.classLoader)
        } catch (err: Exception) {
            return@run Result.failure(
                RuntimeException(
                    "Failed to create ClassLoader for extension '${manifest.name}'. Details: ${err.message}",
                    err,
                )
            )
        }

    val minAppVersion = manifest.minAppVersion
    val maxAppVersion = manifest.maxAppVersion

    val xedVersionCode =
        PackageInfoCompat.getLongVersionCode(application.packageManager.getPackageInfo(application.packageName, 0))

    if (
        (minAppVersion != null && xedVersionCode < minAppVersion) ||
            (maxAppVersion != null && xedVersionCode > maxAppVersion)
    ) {
        return@run Result.failure(
            RuntimeException(
                "Extension '${manifest.name}' (${manifest.version}) is not compatible with this version of Xed-Editor (min: $minAppVersion, max: $maxAppVersion, Xed-Editor: $xedVersionCode)"
            )
        )
    }

    val mainClassInstance =
        try {
            classLoader.loadClass(manifest.mainClass)
        } catch (err: Throwable) {
            return@run Result.failure(err)
        }

    if (ExtensionAPI::class.java.isAssignableFrom(mainClassInstance)) {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO + CoroutineName("Extension: $id"))
        val extContext = ExtensionContext(extension = this, appContext = application, scope = scope)
        val instance =
            try {
                mainClassInstance.getDeclaredConstructor(ExtensionContext::class.java).newInstance(extContext)
                    as? ExtensionAPI
                    ?: return@run Result.failure(
                        RuntimeException(
                            "Failed to instantiate main class '${mainClassInstance.name}' for extension '${manifest.name}'. Ensure the class implements the ExtensionAPI interface and declares a public constructor accepting ExtensionContext."
                        )
                    )
            } catch (err: Throwable) {
                val realError = if (err is java.lang.reflect.InvocationTargetException) err.cause ?: err else err
                return@run Result.failure(realError)
            }

        if (initialInstallation) {
            try {
                instance.onInstalled()
            } catch (err: Throwable) {
                return@run Result.failure(err)
            }
        }

        try {
            instance.onExtensionLoaded()
        } catch (err: Throwable) {
            return@run Result.failure(err)
        }

        App.extensionManager.loadedExtensions[this] = LoadedExtension(instance, scope)
        Result.success(instance)
    } else {
        Result.failure(
            RuntimeException(
                "The main class '${manifest.mainClass}' of extension '${manifest.name}' does not implement the ExtensionAPI interface. Please ensure the main class correctly implements this interface."
            )
        )
    }
}

suspend fun ExtensionManager.installExtensionFromZip(fileObject: FileObject) = run {
    val file = fileObject.copyToTempDir()
    installExtensionFromZip(file).also { file.delete() }
}

suspend fun ExtensionManager.loadAllExtensions() =
    withContext(Dispatchers.IO) {
        for ((_, extension) in localExtensions) {
            if (isExtensionDisabled(extension.id)) {
                continue
            }
            launch(Dispatchers.IO) {
                extension.load(application!!).onFailure { error ->
                    setExtensionDisabled(extension.id, true)
                    withContext(Dispatchers.Main) {
                        com.rk.crashhandler.CrashActivity.start(application!!, extension, error)
                    }
                }
            }
        }
    }
