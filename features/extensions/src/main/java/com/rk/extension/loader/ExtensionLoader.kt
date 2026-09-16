package com.rk.extension.loader

import android.app.Activity
import android.app.Application
import androidx.core.content.pm.PackageInfoCompat
import com.rk.DefaultScope
import com.rk.crashhandler.CrashActivity
import com.rk.events.Events
import com.rk.extension.ExtensionAPI
import com.rk.extension.ExtensionContext
import com.rk.extension.ExtensionEvent
import com.rk.extension.InstallResult
import com.rk.extension.LocalExtension
import com.rk.extension.api.logWarn
import com.rk.extension.apkFile
import com.rk.extension.extensionManager
import com.rk.extension.manager.ExtensionManager
import com.rk.extension.manager.LoadedExtension
import com.rk.extension.scanner.ExtensionScanner
import com.rk.extension.scanner.RestrictedApiIndex
import com.rk.utils.application
import com.rk.utils.isMainThread
import com.rk.utils.logError
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.lang.reflect.InvocationTargetException

enum class LoadScenario {
    INSTALL,
    UPDATE,
    NONE,
}

suspend fun LocalExtension.load(
    application: Application,
    loadScenario: LoadScenario,
): Result<ExtensionAPI> {
    if (isMainThread()) {
        return Result.failure(
            IllegalStateException(
                "Attempted to load extension '${manifest.name}' on the main thread. Extension loading must be performed on a background thread."
            )
        )
    }

    return runCatching {
        scanForRestrictedApis(application)
        verifyCompatibility(application)

        val classLoader = createClassLoader(application)
        val mainClass = loadMainClass(classLoader)

        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO + CoroutineName("Extension: $id"))
        val instance = instantiateAPI(mainClass, application, scope)

        if (loadScenario == LoadScenario.INSTALL) {
            instance.onInstalled()
            extensionManager.invalidateSize(this)
        } else if (loadScenario == LoadScenario.UPDATE) {
            instance.afterUpdate()
            extensionManager.invalidateSize(this)
        }
        instance.onExtensionLoaded()

        extensionManager.registerLoadedExtension(this, LoadedExtension(instance, scope))

        DefaultScope.launch {
            Events.publish(ExtensionEvent.Loaded(this@load))
        }

        instance
    }
}

suspend fun LocalExtension.loadAfterInstall(result: InstallResult.Success, activity: Activity?): Result<ExtensionAPI> {
    extensionManager.setExtensionCrashed(this, false)
    val loadScenario = if (result.performedUpdate) LoadScenario.UPDATE else LoadScenario.INSTALL
    return load(activity?.application ?: application!!, loadScenario).onFailure { error ->
        extensionManager.setExtensionCrashed(this, true)
        withContext(Dispatchers.Main) {
            logError(error, "Failed to load extension '$name'")
            activity?.let {
                CrashActivity.start(
                    context = it,
                    extensionId = id,
                    extensionName = name,
                    extensionVersion = version,
                    extensionAuthor = author.toString(),
                    repository = repository,
                    error = error,
                )
            }
        }
    }
}

private const val MAX_REPORTED_FINDINGS = 10


private suspend fun LocalExtension.scanForRestrictedApis(application: Application) {
    val findings = ExtensionScanner().scan(this, RestrictedApiIndex.load(application))
    if (findings.isEmpty()) {
        return
    }

    findings.filterNot { it.isBlocking }.forEach { finding ->
        val location = "${finding.className}${finding.methodName?.let { "#$it" } ?: ""}"
        id.logWarn("[scanner] ${finding.severity} ${finding.category}: ${finding.message} at $location")
    }

    val blocking = findings.filter { it.isBlocking }
    if (blocking.isNotEmpty()) {
        throw SecurityException(
            buildString {
                append("Extension '${manifest.name}' was blocked because it uses restricted APIs:\n")
                blocking.take(MAX_REPORTED_FINDINGS).forEach { append("• ${it.message}\n") }
                if (blocking.size > MAX_REPORTED_FINDINGS) {
                    append("…and ${blocking.size - MAX_REPORTED_FINDINGS} more")
                }
            }
                .trim()
        )
    }
}


private fun LocalExtension.verifyCompatibility(application: Application) {
    val xedVersionCode =
        PackageInfoCompat.getLongVersionCode(application.packageManager.getPackageInfo(application.packageName, 0))

    val minAppVersion = manifest.minAppVersion
    if (minAppVersion != null && xedVersionCode < minAppVersion) {
        throw IllegalStateException(
            "Extension '${manifest.name}' (${manifest.version}) is not compatible with this version of Xed-Editor (min: $minAppVersion, current: $xedVersionCode)"
        )
    }
}


private fun LocalExtension.createClassLoader(application: Application): ClassLoader {
    return try {
        ExtensionClassLoader(extension = this, parent = application.classLoader)
    } catch (err: Exception) {
        throw IllegalStateException(
            "Failed to create ClassLoader for extension '${manifest.name}'. Details: ${err.message}",
            err,
        )
    }
}

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


suspend fun ExtensionManager.loadAllExtensions() =
    withContext(Dispatchers.IO) {
        for ((_, extension) in installedExtensions.value) {
            if (isExtensionCrashed(extension)) {
                continue
            }
            // The directory may have been removed outside the app (or a previous uninstall did not finish). Treat the
            // extension as no longer installed instead of failing to load and showing a crash screen.
            if (!File(extension.installPath).isDirectory) {
                extension.id.logWarn("Extension directory not found, removing stale entry: ${extension.installPath}")
                forgetExtension(extension.id)
                continue
            }
            launch(Dispatchers.IO) {
                extension.load(application!!, LoadScenario.NONE).onFailure { error ->
                    if (!File(extension.installPath).isDirectory) {
                        extension.id.logWarn("Extension directory not found, removing stale entry: ${extension.installPath}")
                        forgetExtension(extension.id)
                        return@onFailure
                    }
                    setExtensionCrashed(extension, true)
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


fun ExtensionManager.unloadAllExtensions() {
    loadedExtensions.value.values.forEach { loaded ->
        runCatching {
            loaded?.api?.onDispose()
            loaded?.scope?.cancel()
        }
    }
    clearLoadedExtensions()
    cancel()
}
