package com.rk.settings.extension

import android.app.Activity
import android.content.Context
import android.net.Uri
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.rk.App.Companion.extensionManager
import com.rk.activities.settings.SettingsRoutes
import com.rk.components.InfoBlock
import com.rk.components.compose.preferences.base.PreferenceGroup
import com.rk.components.compose.preferences.base.PreferenceLazyColumn
import com.rk.components.compose.preferences.base.PreferenceScaffold
import com.rk.extension.Extension
import com.rk.extension.ExtensionError
import com.rk.extension.InstallResult
import com.rk.extension.LocalExtension
import com.rk.extension.StoreExtension
import com.rk.extension.github.GitHubApiException
import com.rk.extension.installExtensionFromZip
import com.rk.extension.load
import com.rk.file.toFileObject
import com.rk.resources.getString
import com.rk.resources.strings
import com.rk.utils.LoadingPopup
import com.rk.utils.application
import com.rk.utils.errorDialog
import com.rk.utils.openUrl
import com.rk.utils.toast
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Extensions(navController: NavController) {
    val context = LocalContext.current
    val activity = LocalActivity.current as? AppCompatActivity
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        launch(Dispatchers.IO) {
            runCatching {
                extensionManager.indexLocalExtensions()
                extensionManager.indexStoreExtensions()
            }
        }
    }

    val filePickerLauncher =
        rememberLauncherForActivityResult(contract = ActivityResultContracts.OpenDocument()) { uri ->
            installExtensionFromUri(scope, uri, activity)
        }

    PreferenceScaffold(
        label = stringResource(strings.ext),
        isExpandedScreen = false,
        backArrowVisible = true,
        fab = {
            ExtendedFloatingActionButton(
                onClick = { filePickerLauncher.launch(arrayOf("application/zip")) },
                icon = { Icon(imageVector = Icons.Outlined.Add, contentDescription = null) },
                text = { Text(stringResource(strings.install_from_storage)) },
            )
        },
    ) { innerPadding ->
        val extensions by remember {
            derivedStateOf { extensionManager.localExtensions + extensionManager.storeExtension }
        }

        var isIndexing by remember { mutableStateOf(false) }
        var isFetching by remember { mutableStateOf(false) }

        LaunchedEffect(Unit) {
            isIndexing = true
            extensionManager.indexLocalExtensions()
            isIndexing = false
        }

        LaunchedEffect(Unit) {
            try {
                isFetching = true
                extensionManager.indexStoreExtensions()
            } catch (err: GitHubApiException) {
                val message = buildString {
                    appendLine(err.message)
                    appendLine("Response Code: ${err.statusCode}")

                    if (err.response.isNotBlank()) {
                        appendLine("Response: ${err.response}")
                    }
                }

                toast(message)
            } finally {
                isFetching = false
            }
        }

        PreferenceLazyColumn(contentPadding = innerPadding) {
            item {
                InfoBlock(
                    modifier =
                        Modifier.clickable {
                            activity?.openUrl("https://xed-editor.github.io/Xed-Docs/docs/extensions/")
                        },
                    icon = { Icon(imageVector = Icons.Outlined.Info, contentDescription = null) },
                    text = stringResource(strings.info_ext),
                )
            }

            if (extensions.isNotEmpty() || isIndexing || isFetching) {
                items(extensions.map { it.value }.sortedBy { it.name }) { extension ->
                    var installState by remember {
                        mutableStateOf(
                            if (extensionManager.isInstalled(extension.id)) {
                                InstallState.Installed
                            } else {
                                InstallState.Idle
                            }
                        )
                    }

                    ExtensionCard(
                        modifier = Modifier.padding(top = 16.dp, start = 16.dp, end = 16.dp),
                        extension = extension,
                        installState = installState,
                        onInstallClick = {
                            runExtensionInstallAction(extension, { installState = it }, scope, context, activity)
                        },
                        onUninstallClick = { runExtensionUninstallAction(extension, { installState = it }, activity) },
                        onClick = { navController.navigate("${SettingsRoutes.ExtensionDetail.route}/${it.id}") },
                    )
                }

                if (isIndexing || isFetching) {
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                            horizontalArrangement =
                                Arrangement.spacedBy(16.dp, alignment = Alignment.CenterHorizontally),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp))
                            Text(text = stringResource(strings.loading))
                        }
                    }
                }
            } else {
                item {
                    PreferenceGroup(modifier = Modifier.padding(top = 8.dp)) {
                        Text(text = stringResource(strings.no_ext), modifier = Modifier.padding(16.dp))
                    }
                }
            }
        }
    }
}

suspend fun runExtensionUninstallAction(
    extension: Extension,
    updateInstallState: (InstallState) -> Unit,
    activity: AppCompatActivity?,
) {
    extensionManager.uninstallExtension(extension.id).onFailure {
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
                extensionManager.installStoreExtension(context, extension).getOrElse {
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

                    val result = extensionManager.installExtensionFromZip(fileObject)

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
            errorDialog(
                result.error?.localizedMessage ?: strings.unknown_err.getString(),
                activity,
                strings.extension_validation_failed.getString(),
            )
            onError()
        }
    }
