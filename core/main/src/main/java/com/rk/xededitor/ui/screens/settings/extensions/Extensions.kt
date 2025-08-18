package com.rk.xededitor.ui.screens.settings.extensions

import android.app.Activity
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.core.net.toUri
import androidx.documentfile.provider.DocumentFile
import com.rk.DefaultScope
import com.rk.components.compose.preferences.base.PreferenceGroup
import com.rk.components.compose.preferences.base.PreferenceScaffold
import com.rk.components.compose.preferences.base.PreferenceTemplate
import com.rk.extension.InstallResult
import com.rk.extension.LocalExtension
import com.rk.extension.LocalExtensionManager
import com.rk.extension.PluginRegistry
import com.rk.extension.github.GitHubApiException
import com.rk.extension.internal.installExtension
import com.rk.extension.internal.load
import com.rk.file.UriWrapper
import com.rk.libcommons.LoadingPopup
import com.rk.libcommons.application
import com.rk.libcommons.errorDialog
import com.rk.libcommons.toast
import com.rk.resources.getString
import com.rk.resources.strings
import com.rk.settings.Preference
import com.rk.xededitor.ui.components.BottomSheetContent
import com.rk.xededitor.ui.components.InfoBlock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

var selectedPlugin: LocalExtension? = null

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Extensions(modifier: Modifier = Modifier) {
    val extensionManager = LocalExtensionManager.current
    val context = LocalContext.current
    val activity = LocalActivity.current
    val scope = rememberCoroutineScope()

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        var loading: LoadingPopup? = null
        runCatching {
            if (uri == null) {
                return@runCatching
            }

            val fileObject = UriWrapper(DocumentFile.fromSingleUri(context, uri)!!)
            val exists = fileObject.exists()
            val canRead = fileObject.canRead()
            val isZip = fileObject.getName().endsWith(".zip")

            if (exists && canRead && isZip) {
                loading = LoadingPopup(context as Activity, null).show()
                loading.setMessage(strings.installing.getString())
                DefaultScope.launch {
                    val result = extensionManager.installExtension(fileObject, true)
                    handleInstallResult(result, activity)
                }

                loading.hide()
            } else {
                errorDialog(
                    "Install criteria failed \nis_zip = $isZip\ncan_read = $canRead\n exists = $exists\nuri = ${fileObject.getAbsolutePath()}",
                    activity
                )
            }
        }.onFailure {
            loading?.hide()
            errorDialog(it, activity)
        }
    }

    PreferenceScaffold(
        label = stringResource(strings.ext),
        isExpandedScreen = false,
        backArrowVisible = true,
        fab = {
            ExtendedFloatingActionButton(
                onClick = { filePickerLauncher.launch(arrayOf("application/zip")) },
                icon = {
                    Icon(
                        imageVector = Icons.Outlined.Add, contentDescription = null
                    )
                },
                text = { Text(stringResource(strings.install_from_storage)) },
            )
        }
    ) { innerPadding ->
        var showPluginOptionSheet by remember { mutableStateOf(false) }

        val extensions by remember {
            derivedStateOf {
                extensionManager.localExtensions + extensionManager.storeExtension
            }
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

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            InfoBlock(
                modifier = Modifier.clickable {
                    context.startActivity(
                        Intent(
                            Intent.ACTION_VIEW,
                            "https://github.com/Xed-Editor/pluginTemplate".toUri()
                        )
                    )
                },
                icon = {
                    Icon(
                        imageVector = Icons.Outlined.Info, contentDescription = null
                    )
                },
                text = stringResource(strings.info_ext),
            )

            if (isIndexing) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            } else {
                if (extensions.isNotEmpty()) {
                    LazyColumn(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                    ) {
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
                                    }.onSuccess { dir ->
                                        val loadingPopup = LoadingPopup(context)
                                        loadingPopup.setMessage(strings.installing.getString())
                                        loadingPopup.show()

                                        val result = extensionManager.installExtensionFromDir(
                                            dir = dir,
                                            isDev = false
                                        )

                                        handleInstallResult(result, activity) { ext ->
                                            installState = InstallState.Installed
                                            extension = ext

                                            scope.launch(Dispatchers.Default) {
                                                ext.load(application!!).onSuccess {
                                                    // success
                                                }.onFailure {
                                                    errorDialog(it.message ?: "Unexpected error", activity)
                                                }

                                                // it maybe called on extension load success
                                                // I am calling here because extension load always
                                                // fails in latest version due to API change (com.rk.pluginApi.PluginApi)
                                                Preference.setBoolean(
                                                    key = "ext_" + plugin.id,
                                                    true
                                                )
                                            }
                                        }

                                        delay(100)
                                        loadingPopup.hide()
                                    }.onFailure { err ->
                                        errorDialog(err, activity)
                                        installState = InstallState.Idle
                                    }
                                },
                                onUninstallClick = {
                                    extensionManager
                                        .uninstallExtension(plugin.id)
                                        .onSuccess {
                                            toast("Uninstalled")
                                            Preference.setBoolean(
                                                key = "ext_" + plugin.id,
                                                false
                                            )
                                        }
                                        .onFailure { errorDialog(it, activity) }
                                    installState = InstallState.Idle
                                },
                                onLongPress = {
                                    if (extension != null) {
                                        selectedPlugin = extension
                                        showPluginOptionSheet = true
                                    }
                                }
                            )
                        }

                        if (isFetching) {
                            item {
                                Spacer(modifier = Modifier.height(16.dp))

                                Box(
                                    modifier = Modifier.fillMaxWidth(),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = stringResource(strings.loading),
                                        modifier = Modifier.padding(16.dp)
                                    )
                                }
                            }
                        }
                    }
                } else {
                    PreferenceGroup {
                        Text(
                            text = stringResource(strings.no_ext),
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                }
            }

            val bottomSheetState = rememberModalBottomSheetState()

            if (showPluginOptionSheet) {
                ModalBottomSheet(
                    onDismissRequest = { showPluginOptionSheet = false },
                    sheetState = bottomSheetState
                ) {
                    BottomSheetContent(buttons = {}) {
                        PreferenceGroup {
                            PreferenceTemplate(
                                modifier = modifier.clickable {
                                    showPluginOptionSheet = false
                                    DefaultScope.launch(Dispatchers.Main) {
                                        val loading = LoadingPopup(context as Activity, null).show()

                                        withContext(Dispatchers.Default) {
                                            selectedPlugin?.let {
                                                extensionManager
                                                    .uninstallExtension(it.id)
                                                    .onSuccess {
                                                        toast("Uninstalled")
                                                        extensionManager.indexLocalExtensions()
                                                    }
                                            }
                                        }

                                        selectedPlugin = null
                                        delay(300)
                                        loading.hide()
                                    }
                                },
                                contentModifier = Modifier.fillMaxHeight(),
                                title = { Text(text = stringResource(strings.delete)) },
                                enabled = true,
                                applyPaddings = true,
                                startWidget = {
                                    Icon(
                                        imageVector = Icons.Outlined.Delete,
                                        contentDescription = stringResource(strings.delete)
                                    )
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun handleInstallResult(
    result: InstallResult,
    activity: Activity?,
    onSuccess: (LocalExtension) -> Unit = {}
) = when (result) {
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
