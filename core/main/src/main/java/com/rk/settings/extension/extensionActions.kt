package com.rk.settings.extension

import android.app.Activity
import android.content.Context
import android.net.Uri
import androidx.appcompat.app.AppCompatActivity
import com.rk.App.Companion.extensionManager
import com.rk.activities.settings.SettingsActivity
import com.rk.crashhandler.CrashActivity
import com.rk.extension.Extension
import com.rk.extension.ExtensionError
import com.rk.extension.InstallResult
import com.rk.extension.LocalExtension
import com.rk.extension.StoreExtension
import com.rk.extension.UpdatableExtension
import com.rk.extension.loader.installExtensionFromZip
import com.rk.extension.loader.load
import com.rk.file.toFileObject
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

suspend fun runExtensionInstallAction(
    extension: Extension,
    updateInstallState: (InstallState) -> Unit,
    context: Context,
    activity: AppCompatActivity?,
) = withContext(Dispatchers.IO) {
    withContext(Dispatchers.Main) {
        updateInstallState(InstallState.Installing)
    }
    var loading: LoadingPopup? = null
    withContext(Dispatchers.Main) {
        loading = LoadingPopup(activity).show()
        loading.setMessage(strings.installing.getString())
    }

    val result = extensionManager.installStoreExtension(context, extension as StoreExtension).getOrElse {
        withContext(Dispatchers.Main) {
            loading?.hide()
            errorDialog(activity, msg = it.message ?: strings.unknown_error.getString())
            updateInstallState(InstallState.Idle)
        }
        return@withContext
    }

    if (result is InstallResult.Success) {
        extensionManager.setExtensionDisabled(result.extension.id, false)
        result.extension.load(application!!, true).onFailure { error ->
            extensionManager.setExtensionDisabled(result.extension.id, true)
            withContext(Dispatchers.Main) {
                activity?.let {
                    CrashActivity.start(it, result.extension, error)
                } ?: run {
                    errorDialog(activity, msg = error.message ?: strings.unknown_error.getString())
                }
            }
        }
    }

    withContext(Dispatchers.Main) {
        handleInstallResult(result, activity, { updateInstallState(InstallState.Idle) }) { ext ->
            updateInstallState(InstallState.Installed)
        }
        loading?.hide()
    }
}

suspend fun runExtensionUpdateAction(
    extension: UpdatableExtension,
    updateInstallState: (InstallState) -> Unit,
    context: Context,
    activity: AppCompatActivity?,
) = withContext(Dispatchers.IO) {
    withContext(Dispatchers.Main) {
        updateInstallState(InstallState.Updating)
    }
    var loading: LoadingPopup? = null
    withContext(Dispatchers.Main) {
        loading = LoadingPopup(activity).show()
        loading.setMessage(strings.updating.getString())
    }

    val result = extensionManager.installStoreExtension(context, extension.store).getOrElse {
        withContext(Dispatchers.Main) {
            loading?.hide()
            errorDialog(activity, msg = it.message ?: strings.unknown_error.getString())
            updateInstallState(InstallState.Idle)
        }
        return@withContext
    }

    if (result is InstallResult.Success) {
        extensionManager.setExtensionDisabled(result.extension.id, false)
        result.extension.load(application!!).onFailure { error ->
            extensionManager.setExtensionDisabled(result.extension.id, true)
            withContext(Dispatchers.Main) {
                activity?.let {
                    CrashActivity.start(it, result.extension, error)
                } ?: run {
                    errorDialog(activity, msg = error.message ?: strings.unknown_error.getString())
                }
            }
        }
    }

    withContext(Dispatchers.Main) {
        handleInstallResult(result, activity, { updateInstallState(InstallState.Idle) }) { ext ->
            updateInstallState(InstallState.Installed)
        }
        loading?.hide()
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
                    extensionManager.setExtensionDisabled(result.extension.id, false)
                    val initialInstallation = !result.performedUpdate
                    result.extension.load(application!!, initialInstallation).onFailure { error ->
                        extensionManager.setExtensionDisabled(result.extension.id, true)
                        withContext(Dispatchers.Main) {
                            activity?.let {
                                CrashActivity.start(it, result.extension, error)
                            } ?: run {
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
                        msg = "Install criteria failed \nis_zip = $isZip\ncan_read = $canRead\n exists = $exists\nuri = ${fileObject.getAbsolutePath()}",
                    )
                }
            }
        }.onFailure { error ->
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
