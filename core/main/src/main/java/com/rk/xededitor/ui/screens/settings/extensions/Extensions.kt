package com.rk.xededitor.ui.screens.settings.extensions

import android.app.Activity
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.documentfile.provider.DocumentFile
import com.rk.DefaultScope
import com.rk.components.compose.preferences.base.PreferenceGroup
import com.rk.components.compose.preferences.base.PreferenceLayout
import com.rk.components.compose.preferences.base.PreferenceTemplate
import com.rk.components.compose.preferences.switch.PreferenceSwitch
import com.rk.extension.InstallResult
import com.rk.extension.LocalExtension
import com.rk.extension.LocalExtensionManager
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
                    when (val result = extensionManager.installExtension(fileObject, true)) {
                        is InstallResult.AlreadyInstalled -> {
                            errorDialog("Plugin already installed", activity)
                        }

                        is InstallResult.Error -> {
                            errorDialog(result.message, activity)
                        }

                        is InstallResult.Success -> {
                            toast(strings.installed)
                        }

                        is InstallResult.ValidationFailed -> {
                            errorDialog(result.error?.message ?: "Validation failed", activity)
                        }
                    }
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

    PreferenceLayout(label = stringResource(strings.ext), backArrowVisible = true, fab = {
        ExtendedFloatingActionButton(
            onClick = { filePickerLauncher.launch(arrayOf("application/zip")) },
            icon = {
                Icon(
                    imageVector = Icons.Outlined.Add, contentDescription = null
                )
            },
            text = { Text(stringResource(strings.install_from_storage)) },
        )
    }) {
        val showPluginOptionSheet = remember { mutableStateOf(false) }
        var isIndexing by remember { mutableStateOf(false) }

        LaunchedEffect("refreshPlugins") {
            isIndexing = true
            launch {
                extensionManager.indexLocalExtensions()
                isIndexing = false
            }
        }

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

        PreferenceGroup {
            if (!isIndexing) {
                if (extensionManager.installedExtensions.isEmpty()) {
                    Text(
                        text = stringResource(strings.no_ext), modifier = Modifier.padding(16.dp)
                    )
                } else {
                    extensionManager.installedExtensions.forEach { plugin ->
                        var state by remember {
                            mutableStateOf(
                                Preference.getBoolean(
                                    key = "ext_" + plugin.id,
                                    false
                                )
                            )
                        }

                        val sideEffect = { isChecked: Boolean ->
                            if (isChecked) {
                                scope.launch(Dispatchers.Default) {
                                    val loadingPopup = LoadingPopup(context)
                                    loadingPopup.setMessage(strings.installing.getString())
                                    loadingPopup.show()

                                    plugin.load(application!!).onFailure {
                                        errorDialog(it.message ?: "Unknown error", activity)
                                    }.onSuccess {
                                        Preference.setBoolean(
                                            key = "ext_" + plugin.id,
                                            true
                                        )
                                        state = true
                                    }

                                    delay(500)
                                    loadingPopup.hide()
                                }
                            } else {
                                Preference.setBoolean(key = "ext_" + plugin.id, false)
                                state = false
                            }
                        }

                        PreferenceSwitch(
                            checked = state,
                            onCheckedChange = { sideEffect(it) },
                            onLongClick = {
                                selectedPlugin = plugin
                                showPluginOptionSheet.value = true
                            },
                            label = plugin.name,
                            modifier = modifier,
                            onClick = { sideEffect(state.not()) }
                        )
                    }
                }
            } else {
                Text(
                    text = stringResource(strings.loading), modifier = Modifier.padding(16.dp)
                )
            }
        }

        val bottomSheetState = rememberModalBottomSheetState()

        if (showPluginOptionSheet.value) {
            ModalBottomSheet(
                onDismissRequest = { showPluginOptionSheet.value = false },
                sheetState = bottomSheetState
            ) {
                BottomSheetContent(buttons = {}) {
                    PreferenceGroup {
                        PreferenceTemplate(
                            modifier = modifier.clickable {
                                showPluginOptionSheet.value = false
                                DefaultScope.launch(Dispatchers.Main) {
                                    val loading = LoadingPopup(context as Activity, null).show()

                                    withContext(Dispatchers.Default) {
                                        selectedPlugin?.let {
                                            extensionManager.uninstallExtension(it.id)
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
