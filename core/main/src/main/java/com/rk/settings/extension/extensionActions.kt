package com.rk.settings.extension

import android.app.Activity
import android.content.Context
import android.net.Uri
import androidx.appcompat.app.AppCompatActivity
import com.rk.App
import com.rk.activities.settings.SettingsActivity
import com.rk.extension.Extension
import com.rk.extension.ExtensionError
import com.rk.extension.InstallResult
import com.rk.extension.LocalExtension
import com.rk.extension.StoreExtension
import com.rk.extension.installExtensionFromZip
import com.rk.extension.load
import com.rk.file.toFileObject
import com.rk.resources.getFilledString
import com.rk.resources.getString
import com.rk.resources.strings
import com.rk.utils.LoadingPopup
import com.rk.utils.application
import com.rk.utils.dialog
import com.rk.utils.errorDialog
import com.rk.utils.toast
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.MissingFieldException

suspend fun runExtensionUninstallAction(
    extension: Extension,
    updateInstallState: (InstallState) -> Unit,
    activity: AppCompatActivity?,
) {
    App.extensionManager.uninstallExtension(extension.id).onFailure {
        errorDialog(it, activity)
        return
    }
    updateInstallState(InstallState.Idle)
}

suspend fun runExtensionInstallAction(
    extension: Extension,
    updateInstallState: (InstallState) -> Unit,
    scope: CoroutineScope,
    context: Context,
    activity: AppCompatActivity?,
) {
    updateInstallState(InstallState.Installing)
    var loading: LoadingPopup? = null

    runCatching {
            val extension = extension as? StoreExtension ?: return

            loading = LoadingPopup(activity).show()
            loading.setMessage(strings.installing.getString())

            val result =
                App.extensionManager.installStoreExtension(context, extension).getOrElse {
                    loading.hide()
                    errorDialog(it.message ?: strings.unknown_error.getString(), activity)
                    updateInstallState(InstallState.Idle)
                    return@runCatching
                }

            handleInstallResult(result, activity, { updateInstallState(InstallState.Idle) }) { ext ->
                updateInstallState(InstallState.Installed)

                scope.launch(Dispatchers.Default) {
                    ext.load(application!!).onFailure {
                        errorDialog(it.message ?: strings.unknown_error.getString(), activity)
                    }
                }
            }
            loading.hide()
        }
        .onFailure {
            loading?.hide()
            errorDialog(it, activity)
            updateInstallState(InstallState.Idle)
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

                    val result = App.extensionManager.installExtensionFromZip(fileObject)

                    withContext(Dispatchers.Main) {
                        handleInstallResult(result, activity) { ext ->
                            scope.launch(Dispatchers.Default) {
                                ext.load(application!!).onFailure {
                                    errorDialog(it.message ?: strings.unknown_error.getString(), activity)
                                }
                            }
                        }

                        loading?.hide()
                    }
                } else {
                    errorDialog(
                        "Install criteria failed \nis_zip = $isZip\ncan_read = $canRead\n exists = $exists\nuri = ${fileObject.getAbsolutePath()}",
                        activity,
                    )
                }
            }
            .onFailure {
                loading?.hide()
                errorDialog(it, activity)
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
        is InstallResult.AlreadyInstalled -> {
            //            errorDialog("Extension already installed", activity)
        }

        is InstallResult.Error -> {
            when (result.error) {
                ExtensionError.OUTDATED_CLIENT ->
                    errorDialog(strings.outdated_client.getString(), activity, strings.install_failed.getString())
                ExtensionError.OUTDATED_EXTENSION ->
                    errorDialog(strings.outdated_extension.getString(), activity, strings.install_failed.getString())
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
                dialog(
                    SettingsActivity.instance,
                    strings.extension_validation_failed.getString(),
                    strings.manifest_missing_fields.getFilledString(fields),
                    cancelable = false,
                )
                onError()
            } else {
                errorDialog(
                    e?.localizedMessage ?: strings.unknown_error.getString(),
                    activity,
                    strings.extension_validation_failed.getString(),
                )
                onError()
            }
        }
    }
