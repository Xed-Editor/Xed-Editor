package com.rk.settings.extension

import android.app.Activity
import android.net.Uri
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
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
import com.rk.App.Companion.extensionManager
import com.rk.DefaultScope
import com.rk.components.InfoBlock
import com.rk.components.compose.preferences.base.PreferenceGroup
import com.rk.components.compose.preferences.base.PreferenceScaffold
import com.rk.extension.InstallResult
import com.rk.extension.LocalExtension
import com.rk.extension.PluginRegistry
import com.rk.extension.github.GitHubApiException
import com.rk.extension.installExtension
import com.rk.extension.load
import com.rk.file.toFileObject
import com.rk.resources.getString
import com.rk.resources.strings
import com.rk.utils.LoadingPopup
import com.rk.utils.application
import com.rk.utils.errorDialog
import com.rk.utils.openUrl
import com.rk.utils.toast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

var selectedPlugin: LocalExtension? = null

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Extensions(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val activity = LocalActivity.current
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
        rememberLauncherForActivityResult(contract = ActivityResultContracts.OpenDocument()) { uri: Uri? ->
            var loading: LoadingPopup? = null

            GlobalScope.launch {
                runCatching {
                        if (uri == null) {
                            return@runCatching
                        }

                        val fileObject = uri.toFileObject(expectedIsFile = true)
                        val exists = fileObject.exists()
                        val canRead = fileObject.canRead()
                        val isZip = fileObject.getName().endsWith(".zip")

                        if (exists && canRead && isZip) {
                            loading = LoadingPopup(context as Activity, null).show()
                            loading.setMessage(strings.installing.getString())
                            DefaultScope.launch {
                                val result = extensionManager.installExtension(fileObject)
                                handleInstallResult(result, activity)
                            }

                            loading.hide()
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

        Column(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            InfoBlock(
                modifier = Modifier.clickable { activity?.openUrl("https://xed-editor.github.io/Xed-Docs/docs/plugins/") },
                icon = { Icon(imageVector = Icons.Outlined.Info, contentDescription = null) },
                text = stringResource(strings.info_ext),
            )

            if (isIndexing) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else {
                if (extensions.isNotEmpty()) {
                    LazyColumn(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                        items(extensions.map { it.value }.sortedBy { it.name }) { plugin ->
                            var installState by remember {
                                mutableStateOf(
                                    if (extensionManager.isInstalled(plugin.id)) {
                                        InstallState.Installed
                                    } else {
                                        InstallState.Idle
                                    }
                                )
                            }

                            var extension = extensionManager.localExtensions[plugin.id]
                            ExtensionCard(
                                plugin = plugin,
                                installState = installState,
                                onInstallClick = {
                                    installState = InstallState.Installing

                                    runCatching {
                                            val dir = context.cacheDir.resolve(plugin.id)
                                            PluginRegistry.downloadExtension(plugin.id, dir)
                                            dir
                                        }
                                        .onSuccess { dir ->
                                            val loadingPopup = LoadingPopup(context)
                                            loadingPopup.setMessage(strings.installing.getString())
                                            loadingPopup.show()

                                            val result = extensionManager.installExtensionFromDir(dir = dir)

                                            handleInstallResult(result, activity) { ext ->
                                                installState = InstallState.Installed
                                                extension = ext

                                                scope.launch(Dispatchers.Default) {
                                                    ext.load(application!!)
                                                        .onSuccess {
                                                            // success
                                                        }
                                                        .onFailure {
                                                            errorDialog(it.message ?: "Unexpected error", activity)
                                                        }
                                                }
                                            }

                                            delay(100)
                                            loadingPopup.hide()
                                        }
                                        .onFailure { err ->
                                            errorDialog(err, activity)
                                            installState = InstallState.Idle
                                        }
                                },
                                onUninstallClick = {
                                    extensionManager
                                        .uninstallExtension(plugin.id)
                                        .onSuccess {}
                                        .onFailure { errorDialog(it, activity) }
                                    installState = InstallState.Idle
                                },
                                onLongPress = {},
                            )
                        }

                        if (isFetching) {
                            item {
                                Spacer(modifier = Modifier.height(16.dp))

                                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                                    Text(text = stringResource(strings.loading), modifier = Modifier.padding(16.dp))
                                }
                            }
                        }
                    }
                } else {
                    PreferenceGroup { Text(text = stringResource(strings.no_ext), modifier = Modifier.padding(16.dp)) }
                }
            }
        }
    }
}

private fun handleInstallResult(result: InstallResult, activity: Activity?, onSuccess: (LocalExtension) -> Unit = {}) =
    when (result) {
        is InstallResult.AlreadyInstalled -> {
            errorDialog("Plugin already installed", activity)
        }

        is InstallResult.Error -> {
            errorDialog(result.message, activity)
        }

        is InstallResult.Success -> {
            toast(strings.installed)
            onSuccess(result.extension)
        }

        is InstallResult.ValidationFailed -> {
            errorDialog(result.error?.message ?: "Validation failed", activity)
        }
    }
