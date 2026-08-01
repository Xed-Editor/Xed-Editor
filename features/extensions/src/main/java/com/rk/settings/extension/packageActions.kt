package com.rk.settings.extension

import android.app.Activity
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.net.Uri
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.core.app.NotificationCompat
import com.rk.App
import com.rk.DefaultScope
import com.rk.activities.settings.SettingsActivity
import com.rk.common.PackageType
import com.rk.common.XedPackage
import com.rk.crashhandler.CrashActivity
import com.rk.extension.EXTENSION_API_BASE
import com.rk.extension.Extension
import com.rk.extension.ExtensionError
import com.rk.extension.ICONPACKS_API_BASE
import com.rk.extension.InstallResult
import com.rk.extension.InstallState
import com.rk.extension.LocalExtension
import com.rk.extension.StoreExtension
import com.rk.extension.THEMES_API_BASE
import com.rk.extension.UpdatableExtension
import com.rk.extension.extensionManager
import com.rk.extension.loader.LoadScenario
import com.rk.extension.loader.load
import com.rk.extension.manager.StoreManager
import com.rk.extension.model.ExtensionId
import com.rk.extension.model.Package
import com.rk.file.child
import com.rk.file.copyToTempDir
import com.rk.file.themeDir
import com.rk.file.toFileObject
import com.rk.icons.pack.UpdatableIconPack
import com.rk.resources.drawables
import com.rk.resources.getFilledString
import com.rk.resources.getString
import com.rk.resources.strings
import com.rk.settings.Settings
import com.rk.theme.ThemeManager
import com.rk.theme.UpdatableTheme
import com.rk.utils.LoadingPopup
import com.rk.utils.application
import com.rk.utils.dialogRes
import com.rk.utils.errorDialog
import com.rk.utils.toast
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.MissingFieldException
import java.io.File

fun getMissingDependencies(extension: Extension): List<ExtensionId> {
    val missing = linkedSetOf<ExtensionId>()
    val visited = mutableSetOf<ExtensionId>()

    fun collect(ext: Extension) {
        for (depId in ext.dependencies) {
            if (!extensionManager.isInstalled(depId)) {
                val depExt = extensionManager.getExtension(depId)
                if (depExt != null && visited.add(depId)) {
                    collect(depExt)
                }
                missing.add(depId)
            }
        }
    }

    collect(extension)
    return missing.toList()
}

fun getRecommendations(extension: Extension): List<ExtensionId> {
    return extension.recommendations.filter { !extensionManager.isInstalled(it) }
}

fun runExtensionUninstallAction(
    extension: Extension,
    updateInstallState: (InstallState) -> Unit,
    scope: CoroutineScope,
    activity: AppCompatActivity?,
) {
    dialogRes(
        activity = activity,
        title = strings.uninstall_ext_dialog.getString(),
        msg = strings.uninstall_ext_dialog_desc.getFilledString(extension.name),
        okRes = strings.uninstall,
        onOk = {
            scope.launch(Dispatchers.IO) {
                extensionManager.uninstallExtension(extension.id).onFailure { error ->
                    withContext(Dispatchers.Main) {
                        errorDialog(activity, error)
                    }
                    return@launch
                }
                withContext(Dispatchers.Main) {
                    updateInstallState(InstallState.Idle)
                }
            }
        },
        onCancel = {},
    )
}

private fun showDownloadNotification(
    context: Context,
    id: String,
    title: String,
    progress: Float,
    isFinished: Boolean = false,
    errorMessage: String? = null,
) {
    val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    val channelId = "store_downloads"

    val channel =
        NotificationChannel(
                channelId,
                "Store Downloads",
                NotificationManager.IMPORTANCE_LOW,
            )
            .apply {
                description = "Notifications for store downloads and installations"
            }
    notificationManager.createNotificationChannel(channel)

    val builder =
        NotificationCompat.Builder(context, channelId).setSmallIcon(drawables.extension).setOnlyAlertOnce(true)

    if (isFinished) {
        if (errorMessage != null) {
            builder
                .setContentTitle(strings.install_failed.getString(context))
                .setContentText(errorMessage)
                .setOngoing(false)
                .setAutoCancel(true)
        } else {
            builder
                .setContentTitle(strings.installed.getString(context))
                .setContentText(title)
                .setOngoing(false)
                .setAutoCancel(true)
        }
    } else {
        builder.setContentTitle(title).setOngoing(true)
        if (progress >= 0f) {
            val percent = (progress * 100).toInt()
            builder.setContentText("$percent%").setProgress(100, percent, false)
        } else {
            builder.setContentText(strings.installing.getString(context)).setProgress(100, 0, true)
        }
    }

    runCatching {
        notificationManager.notify(id.hashCode(), builder.build())
    }
}

suspend fun installExtensionSequentially(
    extension: Extension,
    context: Context,
    activity: AppCompatActivity?,
): Boolean =
    withContext(Dispatchers.IO) {
        val storeExt =
            extension as? StoreExtension ?: (extension as? UpdatableExtension)?.store ?: return@withContext false
        val id = storeExt.id
        val name = storeExt.name

        withContext(Dispatchers.Main) {
            StoreManager.activeInstalls[id] = InstallState.Installing
            StoreManager.downloadProgress[id] = 0f
            showDownloadNotification(context, id, name, 0f)
        }

        var success = false
        var errorMsg: String? = null
        val tempFile = File(context.cacheDir, "ext_download_${id}.xed")

        try {
            var lastNotificationTime = 0L

            val downloadSuccess =
                StoreManager.downloadFileWithProgress(
                    url = "$EXTENSION_API_BASE/$id/plugin.zip",
                    destFile = tempFile,
                    onProgress = { progress ->
                        DefaultScope.launch(Dispatchers.Main) {
                            StoreManager.downloadProgress[id] = progress
                        }
                        val now = System.currentTimeMillis()
                        if (now - lastNotificationTime > 300) {
                            lastNotificationTime = now
                            showDownloadNotification(context, id, name, progress)
                        }
                    },
                )

            if (downloadSuccess) {
                showDownloadNotification(context, id, name, 1f)

                when (val result = extensionManager.installExtensionFromZip(tempFile)) {
                    is InstallResult.Success -> {
                        extensionManager.setExtensionCrashed(result.extension, false)
                        val resultLoad = result.extension.load(application!!, LoadScenario.INSTALL)
                        if (resultLoad.isFailure) {
                            val error = resultLoad.exceptionOrNull()!!
                            extensionManager.setExtensionCrashed(result.extension, true)
                            errorMsg = error.message ?: "Failed to load extension"
                            withContext(Dispatchers.Main) {
                                activity?.let {
                                    CrashActivity.start(
                                        context = it,
                                        extensionId = result.extension.id,
                                        extensionName = result.extension.name,
                                        extensionVersion = result.extension.version,
                                        extensionAuthor = result.extension.author.toString(),
                                        repository = result.extension.repository,
                                        error = error,
                                    )
                                }
                                    ?: run {
                                        errorDialog(null, msg = errorMsg)
                                    }
                            }
                        }
                        success = errorMsg == null
                    }

                    is InstallResult.Error -> {
                        errorMsg =
                            when (result.error) {
                                ExtensionError.OUTDATED_CLIENT -> strings.outdated_client.getString(context)
                            }
                    }

                    is InstallResult.ValidationFailed -> {
                        errorMsg = result.error?.message ?: "Validation failed"
                    }
                }
            } else {
                errorMsg = "Download failed"
            }
        } catch (e: Exception) {
            e.printStackTrace()
            errorMsg = e.message ?: "Unknown error"
        } finally {
            if (tempFile.exists()) {
                tempFile.delete()
            }

            withContext(Dispatchers.Main) {
                StoreManager.activeInstalls.remove(id)
                StoreManager.downloadProgress.remove(id)

                if (success) {
                    showDownloadNotification(context, id, name, 1f, isFinished = true)
                } else {
                    showDownloadNotification(context, id, name, 0f, isFinished = true, errorMessage = errorMsg)
                    errorDialog(activity, msg = errorMsg ?: "Unknown error")
                }
            }
        }
        success
    }

fun runExtensionInstallAction(
    extension: Extension,
    updateInstallState: (InstallState) -> Unit,
    context: Context,
    activity: AppCompatActivity?,
) {
    updateInstallState(InstallState.Installing)
    DefaultScope.launch {
        val success = installExtensionSequentially(extension, context, activity)
        withContext(Dispatchers.Main) {
            if (success) {
                updateInstallState(InstallState.Installed)
            } else {
                updateInstallState(InstallState.Idle)
            }
        }
    }
}

suspend fun batchInstallExtensions(
    ids: List<ExtensionId>,
    context: Context,
    activity: AppCompatActivity?,
): Boolean {
    for (id in ids) {
        val extension = extensionManager.getExtension(id) ?: continue
        if (!extensionManager.isInstalled(id)) {
            val success = installExtensionSequentially(extension, context, activity)
            if (!success) return false
        }
    }
    return true
}

fun runExtensionUpdateAction(
    extension: UpdatableExtension,
    updateInstallState: (InstallState) -> Unit,
    context: Context,
    activity: AppCompatActivity?,
) {
    val store = extension.store
    val id = store.id
    val name = store.name

    StoreManager.activeInstalls[id] = InstallState.Updating
    StoreManager.downloadProgress[id] = 0f
    updateInstallState(InstallState.Updating)

    showDownloadNotification(context, id, name, 0f)

    DefaultScope.launch(Dispatchers.IO) {
        var success = false
        var errorMsg: String? = null
        val tempFile = File(context.cacheDir, "ext_download_${id}.xed")

        try {
            var lastNotificationTime = 0L

            val downloadSuccess =
                StoreManager.downloadFileWithProgress(
                    url = "$EXTENSION_API_BASE/$id/plugin.zip",
                    destFile = tempFile,
                    onProgress = { progress ->
                        DefaultScope.launch(Dispatchers.Main) {
                            StoreManager.downloadProgress[id] = progress
                        }
                        val now = System.currentTimeMillis()
                        if (now - lastNotificationTime > 300) {
                            lastNotificationTime = now
                            showDownloadNotification(context, id, name, progress)
                        }
                    },
                )

            if (downloadSuccess) {
                showDownloadNotification(context, id, name, 1f)

                when (val result = extensionManager.installExtensionFromZip(tempFile)) {
                    is InstallResult.Success -> {
                        extensionManager.setExtensionCrashed(result.extension, false)
                        result.extension.load(application!!, LoadScenario.UPDATE).onFailure { error ->
                            extensionManager.setExtensionCrashed(result.extension, true)
                            errorMsg = error.message ?: "Failed to load extension"
                            withContext(Dispatchers.Main) {
                                activity?.let {
                                    CrashActivity.start(
                                        context = it,
                                        extensionId = result.extension.id,
                                        extensionName = result.extension.name,
                                        extensionVersion = result.extension.version,
                                        extensionAuthor = result.extension.author.toString(),
                                        repository = result.extension.repository,
                                        error = error,
                                    )
                                }
                                    ?: run {
                                        errorDialog(activity, msg = errorMsg)
                                    }
                            }
                        }
                        success = errorMsg == null
                    }

                    is InstallResult.Error -> {
                        errorMsg =
                            when (result.error) {
                                ExtensionError.OUTDATED_CLIENT -> strings.outdated_client.getString(context)
                            }
                    }

                    is InstallResult.ValidationFailed -> {
                        errorMsg = result.error?.message ?: "Validation failed"
                    }
                }
            } else {
                errorMsg = "Download failed"
            }
        } catch (e: Exception) {
            e.printStackTrace()
            errorMsg = e.message ?: "Unknown error"
        } finally {
            if (tempFile.exists()) {
                tempFile.delete()
            }

            withContext(Dispatchers.Main) {
                StoreManager.activeInstalls.remove(id)
                StoreManager.downloadProgress.remove(id)

                if (success) {
                    showDownloadNotification(context, id, name, 1f, isFinished = true)
                    updateInstallState(InstallState.Installed)
                } else {
                    showDownloadNotification(context, id, name, 0f, isFinished = true, errorMessage = errorMsg)
                    updateInstallState(InstallState.Idle)
                    errorDialog(activity, msg = errorMsg ?: "Unknown error")
                }
            }
        }
    }
}

fun runThemeInstallAction(
    id: String,
    name: String,
    context: Context,
    activity: AppCompatActivity?,
) {
    StoreManager.activeInstalls[id] = InstallState.Installing
    StoreManager.downloadProgress[id] = 0f

    showDownloadNotification(context, id, name, 0f)

    DefaultScope.launch(Dispatchers.IO) {
        var success = false
        var errorMsg: String? = null
        val tempFile = File(context.cacheDir, "theme_${id}.xed")

        try {
            var lastNotificationTime = 0L

            val downloadSuccess =
                StoreManager.downloadFileWithProgress(
                    url = "$THEMES_API_BASE/$id/theme.xed",
                    destFile = tempFile,
                    onProgress = { progress ->
                        DefaultScope.launch(Dispatchers.Main) {
                            StoreManager.downloadProgress[id] = progress
                        }
                        val now = System.currentTimeMillis()
                        if (now - lastNotificationTime > 300) {
                            lastNotificationTime = now
                            showDownloadNotification(context, id, name, progress)
                        }
                    },
                )

            if (downloadSuccess) {
                showDownloadNotification(context, id, name, 1f)
                runCatching {
                    ThemeManager.installTheme(tempFile)
                }
                    .onSuccess {
                        success = true
                    }
                    .onFailure {
                        errorMsg = it.message ?: "Failed to install theme"
                    }
            } else {
                errorMsg = "Download failed"
            }
        } catch (e: Exception) {
            e.printStackTrace()
            errorMsg = e.message ?: "Unknown error"
        } finally {
            if (tempFile.exists()) {
                tempFile.delete()
            }

            withContext(Dispatchers.Main) {
                StoreManager.activeInstalls.remove(id)
                StoreManager.downloadProgress.remove(id)

                if (success) {
                    showDownloadNotification(context, id, name, 1f, isFinished = true)
                } else {
                    showDownloadNotification(context, id, name, 0f, isFinished = true, errorMessage = errorMsg)
                    errorDialog(activity, msg = errorMsg ?: "Unknown error")
                }
            }
        }
    }
}

fun runIconPackInstallAction(
    id: String,
    name: String,
    context: Context,
    activity: AppCompatActivity?,
) {
    StoreManager.activeInstalls[id] = InstallState.Installing
    StoreManager.downloadProgress[id] = 0f

    showDownloadNotification(context, id, name, 0f)

    DefaultScope.launch(Dispatchers.IO) {
        var success = false
        var errorMsg: String? = null
        val tempFile = File(context.cacheDir, "iconpack_${id}.xed")

        try {
            var lastNotificationTime = 0L

            val downloadSuccess =
                StoreManager.downloadFileWithProgress(
                    url = "$ICONPACKS_API_BASE/$id/iconpack.zip",
                    destFile = tempFile,
                    onProgress = { progress ->
                        DefaultScope.launch(Dispatchers.Main) {
                            StoreManager.downloadProgress[id] = progress
                        }
                        val now = System.currentTimeMillis()
                        if (now - lastNotificationTime > 300) {
                            lastNotificationTime = now
                            showDownloadNotification(context, id, name, progress)
                        }
                    },
                )

            if (downloadSuccess) {
                showDownloadNotification(context, id, name, 1f)
                runCatching {
                    App.iconPackManager.installIconPack(tempFile)
                }
                    .onSuccess {
                        success = true
                    }
                    .onFailure {
                        errorMsg = it.message ?: "Failed to install icon pack"
                    }
            } else {
                errorMsg = "Download failed"
            }
        } catch (e: Exception) {
            e.printStackTrace()
            errorMsg = e.message ?: "Unknown error"
        } finally {
            if (tempFile.exists()) {
                tempFile.delete()
            }

            withContext(Dispatchers.Main) {
                StoreManager.activeInstalls.remove(id)
                StoreManager.downloadProgress.remove(id)

                if (success) {
                    showDownloadNotification(context, id, name, 1f, isFinished = true)
                } else {
                    showDownloadNotification(context, id, name, 0f, isFinished = true, errorMessage = errorMsg)
                    errorDialog(activity, msg = errorMsg ?: "Unknown error")
                }
            }
        }
    }
}

fun runPackageInstallAction(
    pkg: Package,
    updateInstallState: (InstallState) -> Unit,
    context: Context,
    activity: AppCompatActivity?,
    dialogManager: ExtensionDialogManager,
) {
    if (pkg.type == PackageType.EXTENSION) {
        val extension = pkg as? Extension ?: return
        val action = {
            val missing = getMissingDependencies(extension)
            if (missing.isNotEmpty()) {
                dialogManager.showDependencies(extension, missing) {
                    runExtensionInstallAction(extension, updateInstallState, context, activity)
                }
            } else {
                runExtensionInstallAction(extension, updateInstallState, context, activity)
            }
        }

        if (Settings.warn_extensions) {
            dialogManager.showWarning(action)
        } else {
            action()
        }
    } else if (pkg.type == PackageType.THEME) {
        runThemeInstallAction(pkg.id, pkg.name, context, activity)
    } else if (pkg.type == PackageType.ICON_PACK) {
        runIconPackInstallAction(pkg.id, pkg.name, context, activity)
    }
}

fun runPackageUninstallAction(
    pkg: Package,
    updateInstallState: (InstallState) -> Unit,
    scope: CoroutineScope,
    activity: AppCompatActivity?,
) {
    if (pkg.type == PackageType.EXTENSION) {
        runExtensionUninstallAction(pkg as Extension, updateInstallState, scope, activity)
    } else if (pkg.type == PackageType.THEME) {
        dialogRes(
            activity = activity,
            title = strings.uninstall_theme_dialog.getString(),
            msg = strings.uninstall_theme_dialog_desc.getFilledString(pkg.name),
            okRes = strings.uninstall,
            onOk = {
                scope.launch(Dispatchers.IO) {
                    themeDir().child(pkg.id).deleteRecursively()
                    withContext(Dispatchers.Main) {
                        ThemeManager.localThemes.remove(pkg.id)
                        updateInstallState(InstallState.Idle)
                    }
                }
            },
        )
    } else if (pkg.type == PackageType.ICON_PACK) {
        dialogRes(
            activity = activity,
            title = strings.uninstall_icon_pack_dialog.getString(),
            msg = strings.uninstall_icon_pack_dialog_desc.getFilledString(pkg.name),
            okRes = strings.uninstall,
            onOk = {
                scope.launch(Dispatchers.IO) {
                    App.iconPackManager.uninstallIconPack(pkg.id)
                    withContext(Dispatchers.Main) {
                        updateInstallState(InstallState.Idle)
                    }
                }
            },
        )
    }
}

fun runPackageUpdateAction(
    pkg: Package,
    updateInstallState: (InstallState) -> Unit,
    context: Context,
    activity: AppCompatActivity?,
    dialogManager: ExtensionDialogManager,
) {
    if (pkg.type == PackageType.EXTENSION) {
        val extension = pkg as? UpdatableExtension ?: return
        val missing = getMissingDependencies(extension)
        if (missing.isNotEmpty()) {
            dialogManager.showDependencies(extension, missing) {
                runExtensionUpdateAction(extension, updateInstallState, context, activity)
            }
        } else {
            runExtensionUpdateAction(extension, updateInstallState, context, activity)
        }
    } else if (pkg.type == PackageType.THEME) {
        runThemeInstallAction(pkg.id, pkg.name, context, activity)
    } else if (pkg.type == PackageType.ICON_PACK) {
        runIconPackInstallAction(pkg.id, pkg.name, context, activity)
    }
}

fun installAutoDetect(scope: CoroutineScope, uri: Uri?, activity: AppCompatActivity?) {
    var loading: LoadingPopup? = null

    scope.launch(Dispatchers.IO) {
        runCatching {
            if (uri == null) return@runCatching

            val fileObject = uri.toFileObject(expectedIsFile = true)
            if (!fileObject.exists() || !fileObject.canRead()) {
                withContext(Dispatchers.Main) {
                    errorDialog(activity, msg = "Cannot read file: ${fileObject.getAbsolutePath()}")
                }
                return@runCatching
            }

            withContext(Dispatchers.Main) {
                loading = LoadingPopup(activity).show()
                loading.setMessage(strings.installing.getString())
            }

            if (fileObject.getExtension() == "json") {
                fileObject.copyToTempDir().also {
                    ThemeManager.installTheme(it)
                    withContext(Dispatchers.Main) {
                        toast(strings.installed)
                        loading?.hide()
                    }
                }
                return@launch
            }

            val tempDir = File(application!!.cacheDir, "install_temp_${System.currentTimeMillis()}")
            tempDir.mkdirs()

            try {
                val localFile = File(tempDir, "package.xed")
                fileObject.getInputStream().use { input ->
                    localFile.outputStream().use { output -> input.copyTo(output) }
                }

                XedPackage.extract(localFile, tempDir)
                val type = XedPackage.detectPackageType(tempDir)

                when (type) {
                    PackageType.EXTENSION -> {
                        val result = extensionManager.installExtensionFromDir(tempDir)
                        if (result is InstallResult.Success) {
                            extensionManager.setExtensionCrashed(result.extension, false)
                            val loadScenario = if (result.performedUpdate) LoadScenario.UPDATE else LoadScenario.INSTALL
                            result.extension.load(application!!, loadScenario).onFailure { error ->
                                extensionManager.setExtensionCrashed(result.extension, true)
                                withContext(Dispatchers.Main) {
                                    activity?.let {
                                        CrashActivity.start(
                                            context = it,
                                            extensionId = result.extension.id,
                                            extensionName = result.extension.name,
                                            extensionVersion = result.extension.version,
                                            extensionAuthor = result.extension.author.toString(),
                                            repository = result.extension.repository,
                                            error = error,
                                        )
                                    }
                                        ?: run {
                                            errorDialog(
                                                activity,
                                                msg = error.message ?: strings.unknown_error.getString(),
                                            )
                                        }
                                }
                            }
                        }
                        withContext(Dispatchers.Main) {
                            handleInstallResult(result, activity)
                        }
                    }
                    PackageType.THEME -> {
                        ThemeManager.installTheme(localFile)
                        withContext(Dispatchers.Main) { toast(strings.installed) }
                    }
                    PackageType.ICON_PACK -> {
                        App.iconPackManager.installIconPack(localFile)
                        withContext(Dispatchers.Main) { toast(strings.installed) }
                    }
                    null -> {
                        withContext(Dispatchers.Main) {
                            errorDialog(activity, msg = "Unknown package type")
                        }
                    }
                }
            } finally {
                tempDir.deleteRecursively()
                withContext(Dispatchers.Main) { loading?.hide() }
            }
        }
            .onFailure { error ->
                withContext(Dispatchers.Main) {
                    loading?.hide()
                    errorDialog(activity, error)
                }
            }
    }
}

@OptIn(ExperimentalSerializationApi::class)
private fun handleInstallResult(
    result: InstallResult,
    activity: Activity?,
    onError: () -> Unit = {},
    onSuccess: (LocalExtension) -> Unit = {},
) =
    when (result) {
        is InstallResult.Error -> {
            when (result.error) {
                ExtensionError.OUTDATED_CLIENT ->
                    errorDialog(activity, strings.install_failed.getString(), strings.outdated_client.getString())
            }
            onError()
        }

        is InstallResult.Success -> {
            toast(strings.installed)
            onSuccess(result.extension)
        }

        is InstallResult.ValidationFailed -> {
            val e = result.error
            if (e is MissingFieldException) {
                val fields = e.missingFields.joinToString("\n") { "• $it" }
                dialogRes(
                    SettingsActivity.instance,
                    strings.extension_validation_failed.getString(),
                    strings.manifest_missing_fields.getFilledString(fields),
                    cancelable = false,
                )
                onError()
            } else {
                errorDialog(
                    activity,
                    e?.localizedMessage ?: strings.unknown_error.getString(),
                    strings.extension_validation_failed.getString(),
                )
                onError()
            }
        }
    }

@Composable
fun rememberPackageInstallState(pkg: Package): InstallState {
    val id = pkg.id
    return remember(pkg, StoreManager.activeInstalls[id]) {
        val active = StoreManager.activeInstalls[id]
        if (active != null) return@remember active

        when (pkg.type) {
            PackageType.EXTENSION -> {
                if (extensionManager.isInstalled(id)) {
                    val ext = extensionManager.getExtension(id)
                    if (ext is UpdatableExtension && ext.hasUpdate()) InstallState.Updatable else InstallState.Installed
                } else InstallState.Idle
            }
            PackageType.THEME -> {
                if (ThemeManager.isInstalled(id)) {
                    val theme = ThemeManager.getTheme(id)
                    if (theme is UpdatableTheme && theme.hasUpdate()) InstallState.Updatable else InstallState.Installed
                } else InstallState.Idle
            }
            PackageType.ICON_PACK -> {
                if (App.iconPackManager.isInstalled(id)) {
                    val pack = App.iconPackManager.getIconPackPackage(id)
                    if (pack is UpdatableIconPack && pack.hasUpdate()) InstallState.Updatable
                    else InstallState.Installed
                } else InstallState.Idle
            }
        }
    }
}

@Composable fun rememberInstallState(extension: Extension): InstallState = rememberPackageInstallState(extension)
