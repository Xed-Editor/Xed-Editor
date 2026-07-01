package com.rk.extension.loader

import android.app.Application
import androidx.core.content.pm.PackageInfoCompat
import com.rk.crashhandler.CrashActivity
import com.rk.extension.ExtensionAPI
import com.rk.extension.ExtensionContext
import com.rk.extension.LocalExtension
import com.rk.extension.apkFile
import com.rk.extension.extensionManager
import com.rk.extension.manager.ExtensionManager
import com.rk.extension.manager.LoadedExtension
import com.rk.file.FileObject
import com.rk.file.copyToTempDir
import com.rk.utils.application
import com.rk.utils.isMainThread
import dalvik.system.PathClassLoader
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.lang.reflect.InvocationTargetException

/**
 * Loads a locally installed extension.
 *
 * This function performs compatibility checks, instantiates the extension's main class, initializes the extension
 * lifecycle, and caches the result.
 *
 * @param application The main Android [Application] instance.
 * @param initialInstallation True if this is the first time the extension is installed/loaded.
 * @return A [Result] enclosing the loaded [com.rk.extension.ExtensionAPI] instance, or a failure exception.
 */
fun LocalExtension.load(
    application: Application,
    initialInstallation: Boolean = false,
): Result<ExtensionAPI> {
    if (isMainThread()) {
        return Result.failure(
            IllegalStateException(
                "Attempted to load extension '${manifest.name}' on the main thread. Extension loading must be performed on a background thread."
            )
        )
    }

    return runCatching {
        verifyCompatibility(application)

        val classLoader = createClassLoader(application)
        val mainClass = loadMainClass(classLoader)

        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO + CoroutineName("Extension: $id"))
        val instance = instantiateAPI(mainClass, application, scope)

        if (initialInstallation) {
            instance.onInstalled()
        }
        instance.onExtensionLoaded()

        extensionManager.loadedExtensions[this] = LoadedExtension(instance, scope)
        instance
    }
}

/**
 * Verifies if the extension is compatible with the running version of the editor. Throws an [IllegalStateException] if
 * the app version does not satisfy the extension's requirements.
 */
private fun LocalExtension.verifyCompatibility(application: Application) {
    val minAppVersion = manifest.minAppVersion
    val maxAppVersion = manifest.maxAppVersion

    val xedVersionCode =
        PackageInfoCompat.getLongVersionCode(application.packageManager.getPackageInfo(application.packageName, 0))

    val isBelowMin = minAppVersion != null && xedVersionCode < minAppVersion
    val isAboveMax = maxAppVersion != null && xedVersionCode > maxAppVersion

    if (isBelowMin || isAboveMax) {
        throw IllegalStateException(
            "Extension '${manifest.name}' (${manifest.version}) is not compatible with this version of Xed-Editor (min: $minAppVersion, max: $maxAppVersion, Xed-Editor: $xedVersionCode)"
        )
    }
}

/**
 * Creates a class loader specifically configured for this extension's APK/package file. Uses a child-first delegation
 * strategy so extension-specific libraries take precedence.
 */
private fun LocalExtension.createClassLoader(application: Application): ClassLoader {
    return try {
        PathClassLoader(apkFile.absolutePath, application.classLoader)
    } catch (err: Exception) {
        throw IllegalStateException(
            "Failed to create ClassLoader for extension '${manifest.name}'. Details: ${err.message}",
            err,
        )
    }
}

/** Loads the main entry point class of the extension and asserts that it implements [ExtensionAPI]. */
private fun LocalExtension.loadMainClass(classLoader: ClassLoader): Class<*> {
    val mainClass =
        try {
            classLoader.loadClass(manifest.mainClass)
        } catch (err: Throwable) {
            throw err
        }

    if (!ExtensionAPI::class.java.isAssignableFrom(mainClass)) {
        throw IllegalStateException(
            "The main class '${manifest.mainClass}' of extension '${manifest.name}' does not implement the ExtensionAPI interface. Please ensure the main class correctly implements this interface."
        )
    }

    return mainClass
}

/**
 * Instantiates the extension's main [ExtensionAPI] class by calling its public constructor that accepts an
 * [com.rk.extension.ExtensionContext].
 */
private fun LocalExtension.instantiateAPI(
    mainClassInstance: Class<*>,
    application: Application,
    scope: CoroutineScope,
): ExtensionAPI {
    val extContext = ExtensionContext(extension = this, appContext = application, scope = scope)
    return try {
        val constructor = mainClassInstance.getDeclaredConstructor(ExtensionContext::class.java)
        (constructor.newInstance(extContext) as? ExtensionAPI)
            ?: throw IllegalStateException(
                "Failed to instantiate main class '${mainClassInstance.name}' for extension '${manifest.name}'. Ensure the class implements the ExtensionAPI interface and declares a public constructor accepting ExtensionContext."
            )
    } catch (err: Throwable) {
        // Unpack Java reflection wrapping to show the real root exception if available
        val realError = if (err is InvocationTargetException) err.cause ?: err else err
        throw realError
    }
}

/** Installs an extension directly from a file object by copying it to a temporary directory first. */
suspend fun ExtensionManager.installExtensionFromZip(fileObject: FileObject) = run {
    val file = fileObject.copyToTempDir()
    installExtensionFromZip(file).also { file.delete() }
}

/**
 * Scans all local extensions and loads any that are not disabled. If an extension fails to load, it is marked as
 * disabled and a crash screen is shown.
 */
suspend fun ExtensionManager.loadAllExtensions() =
    withContext(Dispatchers.IO) {
        for ((_, extension) in localExtensions) {
            if (isExtensionCrashed(extension.id)) {
                continue
            }
            launch(Dispatchers.IO) {
                extension.load(application!!).onFailure { error ->
                    setExtensionCrashed(extension.id, true)
                    withContext(Dispatchers.Main) {
                        CrashActivity.start(
                            context = application!!,
                            extensionId = extension.id,
                            extensionName = extension.name,
                            extensionVersion = extension.version,
                            extensionAuthor = extension.author.toString(),
                            repository = extension.repository,
                            error = error,
                        )
                    }
                }
            }
        }
    }
