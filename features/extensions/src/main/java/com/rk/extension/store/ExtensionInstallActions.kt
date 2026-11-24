package com.rk.extension.store

import android.app.Activity
import com.rk.DefaultScope
import com.rk.activities.settings.SettingsActivity
import com.rk.extension.Extension
import com.rk.extension.ExtensionError
import com.rk.extension.InstallResult
import com.rk.extension.LocalExtension
import com.rk.extension.StoreExtension
import com.rk.extension.UpdatableExtension
import com.rk.extension.extensionManager
import com.rk.extension.loader.loadAfterInstall
import com.rk.extension.model.ExtensionId
import com.rk.extension.scanner.ScanApproval
import com.rk.packages.InstallState
import com.rk.packages.StoreManager
import com.rk.resources.getFilledString
import com.rk.resources.getString
import com.rk.resources.strings
import com.rk.settings.Settings
import com.rk.settings.extension.ExtensionDialogManager
import com.rk.settings.extension.asScanApproval
import com.rk.store.PackageActionContext
import com.rk.store.actions.InstallLoadingPopup
import com.rk.store.actions.downloadPackageFile
import com.rk.store.actions.showDownloadNotification
import com.rk.utils.dialogRes
import com.rk.utils.errorDialog
import com.rk.utils.logError
import com.rk.utils.toast
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.MissingFieldException

/** Missing dependencies of [extension] that are not installed yet. */
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

/** Recommended extensions of [extension] that are not installed yet. */
fun getRecommendations(extension: Extension): List<ExtensionId> {
    return extension.recommendations.filter { !extensionManager.isInstalled(it) }
}

/** Installs [extension], asking first for the extension warning and for any missing dependency. */
fun requestExtensionInstall(
    extension: Extension,
    action: PackageActionContext,
    dialogManager: ExtensionDialogManager,
) {
    action.updateInstallState(InstallState.Installing)
    DefaultScope.launch {
        val success = installExtension(extension, action, dialogManager, InstallState.Installing)
        withContext(Dispatchers.Main) {
            action.updateInstallState(if (success) InstallState.Installed else InstallState.Idle)
        }
    }
}

/** Updates [extension] to the store version. */
fun requestExtensionUpdate(
    extension: UpdatableExtension,
    action: PackageActionContext,
    dialogManager: ExtensionDialogManager,
) {
    action.updateInstallState(InstallState.Updating)
    DefaultScope.launch {
        val success = installExtension(extension, action, dialogManager, InstallState.Updating)
        withContext(Dispatchers.Main) {
            action.updateInstallState(if (success) InstallState.Installed else InstallState.Idle)
        }
    }
}

/** Asks for confirmation and uninstalls [extension]. */
fun requestExtensionUninstall(extension: Extension, action: PackageActionContext) {
    dialogRes(
        activity = action.activity,
        title = strings.uninstall_ext_dialog.getString(),
        msg = strings.uninstall_ext_dialog_desc.getFilledString(extension.name),
        okRes = strings.uninstall,
        onOk = {
            action.scope.launch(Dispatchers.IO) {
                extensionManager.uninstallExtension(extension.id).onFailure { error ->
                    withContext(Dispatchers.Main) { errorDialog(action.activity, error) }
                    return@launch
                }
                withContext(Dispatchers.Main) { action.updateInstallState(InstallState.Idle) }
            }
        },
        onCancel = {},
    )
}

/** Installs an extension package the user opened from storage. */
suspend fun installExtensionFromFile(
    file: File,
    action: PackageActionContext,
    dialogManager: ExtensionDialogManager,
): Boolean =
    withContext(Dispatchers.IO) {
        if (Settings.warn_extensions && !dialogManager.awaitWarning()) return@withContext false

        val loading = InstallLoadingPopup(action.activity)
        var success = false
        try {
            loading.show()
            val result = extensionManager.installExtensionFromZip(file, loading.around(dialogManager.asScanApproval()))
            loading.hide()

            withContext(Dispatchers.Main) { handleInstallResult(result, action.activity) }

            if (result is InstallResult.Success) {
                val loadResult = result.extension.loadAfterInstall(result, action.activity)
                success = loadResult.isSuccess
                loadResult.onFailure { errorMsg ->
                    withContext(Dispatchers.Main) {
                        errorDialog(action.activity, msg = errorMsg.message ?: "Failed to load extension")
                    }
                }
            }
        } finally {
            loading.hide()
        }
        success
    }

private suspend fun installExtension(
    extension: Extension,
    action: PackageActionContext,
    dialogManager: ExtensionDialogManager,
    installState: InstallState,
): Boolean =
    withContext(Dispatchers.IO) {
        val storeExtension =
            extension as? StoreExtension ?: (extension as? UpdatableExtension)?.store ?: return@withContext false
        val id = storeExtension.id
        val name = storeExtension.name

        if (Settings.warn_extensions && !dialogManager.awaitWarning()) return@withContext false

        val missing = getMissingDependencies(extension)
        if (missing.isNotEmpty()) {
            if (!dialogManager.awaitDependencies(extension, missing)) return@withContext false
            if (!installDependencies(missing, action, dialogManager, installState)) return@withContext false
        }

        var success = false
        var errorMsg: String? = null
        var downloadedFile: File? = null

        try {
            val file =
                downloadPackageFile(
                    context = action.context,
                    id = id,
                    name = name,
                    url = ExtensionStoreApi.getPluginDownloadUrl(id),
                    cacheFileName = "ext_download_$id.xed",
                    installState = installState,
                )

            downloadedFile = file
            if (file == null) {
                errorMsg = "Download failed"
                withContext(Dispatchers.Main) { errorDialog(action.activity, msg = errorMsg) }
                return@withContext false
            }

            val loading = InstallLoadingPopup(action.activity)
            try {
                loading.show()
                val scanApproval = loading.around(dialogManager.asScanApproval())
                val result = extensionManager.installExtensionFromZip(file, scanApproval)
                loading.hide()

                withContext(Dispatchers.Main) { handleInstallResult(result, action.activity) }

                when (result) {
                    is InstallResult.Success -> {
                        result.extension
                            .loadAfterInstall(result, action.activity)
                            .onFailure { errorMsg = it.message ?: "Failed to load extension" }
                            .onSuccess { success = true }
                    }

                    is InstallResult.Cancelled -> {}

                    else -> {
                        errorMsg = "Failed to install extension"
                    }
                }
            } finally {
                loading.hide()
            }
        } catch (e: Exception) {
            logError(e)
            errorMsg = e.message ?: strings.unknown_err.getString()
            withContext(Dispatchers.Main) { errorDialog(action.activity, msg = errorMsg) }
        } finally {
            downloadedFile?.delete()
            StoreManager.clearInstall(id)

            withContext(Dispatchers.Main) {
                if (success) {
                    showDownloadNotification(action.context, id, name, 1f, isFinished = true)
                } else {
                    showDownloadNotification(action.context, id, name, 0f, isFinished = true, errorMessage = errorMsg)
                }
            }
        }

        success
    }

private suspend fun installDependencies(
    ids: List<ExtensionId>,
    action: PackageActionContext,
    dialogManager: ExtensionDialogManager,
    installState: InstallState,
): Boolean {
    for (id in ids) {
        val extension = extensionManager.getExtension(id) ?: continue
        if (!extensionManager.isInstalled(id)) {
            if (!installExtension(extension, action, dialogManager, installState)) return false
        }
    }
    return true
}

private fun InstallLoadingPopup.around(approval: ScanApproval): ScanApproval = { name, findings ->
    hide()
    val approved = approval(name, findings)
    if (approved) {
        show()
    }
    approved
}

@OptIn(ExperimentalSerializationApi::class)
fun handleInstallResult(
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

        is InstallResult.ScanRejected -> {
            val details = result.findings.filter { it.isBlocking }.joinToString("\n") { "• ${it.message}" }
            errorDialog(
                activity,
                details.ifBlank { strings.security_scan_blocked.getString() },
                strings.security_scan.getString(),
            )
            onError()
        }

        is InstallResult.Cancelled -> {
            onError()
        }
    }
