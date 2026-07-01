package com.rk.settings.extension

import android.app.Activity
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.net.Uri
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.NotificationCompat
import com.rk.DefaultScope
import com.rk.activities.settings.SettingsActivity
import com.rk.crashhandler.CrashActivity
import com.rk.extension.EXTENSION_API_BASE
import com.rk.extension.Extension
import com.rk.extension.ExtensionError
import com.rk.extension.ICONPACKS_API_BASE
import com.rk.extension.InstallResult
import com.rk.extension.LocalExtension
import com.rk.extension.StoreExtension
import com.rk.extension.UpdatableExtension
import com.rk.extension.extensionManager
import com.rk.extension.loader.installExtensionFromZip
import com.rk.extension.loader.load
import com.rk.extension.manager.ExtensionRegistry
import com.rk.file.toFileObject
import com.rk.resources.drawables
import com.rk.resources.getFilledString
import com.rk.resources.getString
import com.rk.resources.strings
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

fun checkExtensionWarningAndRun(activity: AppCompatActivity?, onApproved: () -> Unit) {
    if (com.rk.settings.Settings.warn_extensions) {
        dialogRes(
            activity = activity,
            title = strings.attention.getString(),
            msg = strings.extension_warning_msg.getString(),
            okRes = strings.continue_action,
            cancelRes = strings.cancel,
            onOk = {
                com.rk.settings.Settings.warn_extensions = false
                onApproved()
            },
            onCancel = {},
        )
    } else {
        onApproved()
    }
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

    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
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
    }

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

fun runExtensionInstallAction(
    extension: Extension,
    updateInstallState: (InstallState) -> Unit,
    context: Context,
    activity: AppCompatActivity?,
) {
    val storeExt = extension as? StoreExtension ?: return
    val id = storeExt.id
    val name = storeExt.name

    ExtensionRegistry.activeInstalls[id] = InstallState.Installing
    ExtensionRegistry.downloadProgress[id] = 0f
    updateInstallState(InstallState.Installing)

    showDownloadNotification(context, id, name, 0f)

    DefaultScope.launch(Dispatchers.IO) {
        var success = false
        var errorMsg: String? = null
        val tempFile = File(context.cacheDir, "ext_download_${id}.zip")

        try {
            var lastNotificationTime = 0L

            val downloadSuccess =
                ExtensionRegistry.downloadFileWithProgress(
                    url = "$EXTENSION_API_BASE/$id/plugin.zip",
                    destFile = tempFile,
                    onProgress = { progress ->
                        DefaultScope.launch(Dispatchers.Main) {
                            ExtensionRegistry.downloadProgress[id] = progress
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

                val result = extensionManager.installExtensionFromZip(tempFile)
                if (result is InstallResult.Success) {
                    extensionManager.setExtensionCrashed(result.extension.id, false)
                    result.extension.load(application!!, true).onFailure { error ->
                        extensionManager.setExtensionCrashed(result.extension.id, true)
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
                } else if (result is InstallResult.Error) {
                    errorMsg =
                        when (result.error) {
                            ExtensionError.OUTDATED_CLIENT -> strings.outdated_client.getString(context)
                            ExtensionError.OUTDATED_EXTENSION -> strings.outdated_extension.getString(context)
                        }
                } else if (result is InstallResult.ValidationFailed) {
                    errorMsg = result.error?.message ?: "Validation failed"
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
                ExtensionRegistry.activeInstalls.remove(id)
                ExtensionRegistry.downloadProgress.remove(id)

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

fun runExtensionUpdateAction(
    extension: UpdatableExtension,
    updateInstallState: (InstallState) -> Unit,
    context: Context,
    activity: AppCompatActivity?,
) {
    val store = extension.store
    val id = store.id
    val name = store.name

    ExtensionRegistry.activeInstalls[id] = InstallState.Updating
    ExtensionRegistry.downloadProgress[id] = 0f
    updateInstallState(InstallState.Updating)

    showDownloadNotification(context, id, name, 0f)

    DefaultScope.launch(Dispatchers.IO) {
        var success = false
        var errorMsg: String? = null
        val tempFile = File(context.cacheDir, "ext_download_${id}.zip")

        try {
            var lastNotificationTime = 0L

            val downloadSuccess =
                ExtensionRegistry.downloadFileWithProgress(
                    url = "$EXTENSION_API_BASE/$id/plugin.zip",
                    destFile = tempFile,
                    onProgress = { progress ->
                        DefaultScope.launch(Dispatchers.Main) {
                            ExtensionRegistry.downloadProgress[id] = progress
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

                val result = extensionManager.installExtensionFromZip(tempFile)
                if (result is InstallResult.Success) {
                    extensionManager.setExtensionCrashed(result.extension.id, false)
                    result.extension.load(application!!).onFailure { error ->
                        extensionManager.setExtensionCrashed(result.extension.id, true)
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
                } else if (result is InstallResult.Error) {
                    errorMsg =
                        when (result.error) {
                            ExtensionError.OUTDATED_CLIENT -> strings.outdated_client.getString(context)
                            ExtensionError.OUTDATED_EXTENSION -> strings.outdated_extension.getString(context)
                        }
                } else if (result is InstallResult.ValidationFailed) {
                    errorMsg = result.error?.message ?: "Validation failed"
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
                ExtensionRegistry.activeInstalls.remove(id)
                ExtensionRegistry.downloadProgress.remove(id)

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

fun runIconPackInstallAction(
    id: String,
    name: String,
    context: Context,
    activity: AppCompatActivity?,
) {
    ExtensionRegistry.activeInstalls[id] = InstallState.Installing
    ExtensionRegistry.downloadProgress[id] = 0f

    showDownloadNotification(context, id, name, 0f)

    DefaultScope.launch(Dispatchers.IO) {
        var success = false
        var errorMsg: String? = null
        val tempFile = File(context.cacheDir, "iconpack_${id}.zip")

        try {
            var lastNotificationTime = 0L

            val downloadSuccess =
                ExtensionRegistry.downloadFileWithProgress(
                    url = "$ICONPACKS_API_BASE/$id/iconpack.zip",
                    destFile = tempFile,
                    onProgress = { progress ->
                        DefaultScope.launch(Dispatchers.Main) {
                            ExtensionRegistry.downloadProgress[id] = progress
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
                    com.rk.App.Companion.iconPackManager.installIconPack(tempFile)
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
                ExtensionRegistry.activeInstalls.remove(id)
                ExtensionRegistry.downloadProgress.remove(id)

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

fun installExtensionFromUri(scope: CoroutineScope, uri: Uri?, activity: AppCompatActivity?) {
    var loading: LoadingPopup? = null

    scope.launch(Dispatchers.IO) {
        runCatching {
            if (uri == null) return@runCatching

            val fileObject = uri.toFileObject(expectedIsFile = true)
            val exists = fileObject.exists()
            val canRead = fileObject.canRead()
            val isZip = fileObject.getName().endsWith(".zip")

            if (exists && canRead && isZip) {
                withContext(Dispatchers.Main) {
                    loading = LoadingPopup(activity).show()
                    loading.setMessage(strings.installing.getString())
                }

                val result = extensionManager.installExtensionFromZip(fileObject)

                if (result is InstallResult.Success) {
                    extensionManager.setExtensionCrashed(result.extension.id, false)
                    val initialInstallation = !result.performedUpdate
                    result.extension.load(application!!, initialInstallation).onFailure { error ->
                        extensionManager.setExtensionCrashed(result.extension.id, true)
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
                                    errorDialog(activity, msg = error.message ?: strings.unknown_error.getString())
                                }
                        }
                    }
                }

                withContext(Dispatchers.Main) {
                    handleInstallResult(result, activity)
                    loading?.hide()
                }
            } else {
                withContext(Dispatchers.Main) {
                    errorDialog(
                        activity,
                        msg =
                            "Install criteria failed \nis_zip = $isZip\ncan_read = $canRead\n exists = $exists\nuri = ${fileObject.getAbsolutePath()}",
                    )
                }
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
                ExtensionError.OUTDATED_EXTENSION ->
                    errorDialog(activity, strings.install_failed.getString(), strings.outdated_extension.getString())
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
