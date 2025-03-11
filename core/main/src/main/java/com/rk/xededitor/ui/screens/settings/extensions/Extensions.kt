package com.rk.xededitor.ui.screens.settings.extensions

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.webkit.MimeTypeMap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.rk.extension.Extension
import com.rk.extension.ExtensionManager
import com.rk.libcommons.DefaultScope
import com.rk.libcommons.LoadingPopup
import com.rk.libcommons.application
import com.rk.libcommons.toast
import com.rk.resources.getString
import com.rk.resources.strings
import com.rk.xededitor.App.Companion.getTempDir
import com.rk.xededitor.ui.components.BottomSheetContent
import com.rk.xededitor.ui.components.InfoBlock
import com.rk.xededitor.ui.components.SettingsToggle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.rk.components.compose.preferences.base.PreferenceGroup
import com.rk.components.compose.preferences.base.PreferenceLayout
import com.rk.components.compose.preferences.base.PreferenceTemplate
import com.rk.settings.Preference
import java.io.File
import java.io.FileOutputStream

var selectedPlugin: Extension? = null

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Extensions(modifier: Modifier = Modifier) {
    val context = LocalContext.current

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        var loading: LoadingPopup? = null
        runCatching {
            val fileExtension = MimeTypeMap.getFileExtensionFromUrl(uri.toString())
            if (fileExtension == "plugin") {
                loading = LoadingPopup(context as Activity, null).show()
                loading?.setMessage(strings.installing.getString())
                DefaultScope.launch {
                    val pluginFile = File(getTempDir(), "installPlugin.plugin")
                    application!!.contentResolver.openInputStream(uri!!).use {
                        FileOutputStream(pluginFile).use { outputStream ->
                            it!!.copyTo(outputStream)
                        }
                    }
                    ExtensionManager.installPlugin(application!!, pluginFile)
                    pluginFile.delete()
                    delay(900)
                    withContext(Dispatchers.Main) {
                       // loading?.hide()
                    }
                }
            } else {
                toast(strings.not_plugin_err.getString())
            }
        }.onFailure {
            loading?.hide()
            toast(it.message)
        }

    }

    PreferenceLayout(label = stringResource(strings.ext), backArrowVisible = true, fab = {
        ExtendedFloatingActionButton(
            onClick = { filePickerLauncher.launch(arrayOf("*/*")) },
            icon = {
                Icon(
                    imageVector = Icons.Outlined.Add, contentDescription = null
                )
            },
            text = { Text(stringResource(strings.install_from_storage)) },
        )
    }) {
        val extensions = ExtensionManager.extensions
        val isLoaded = ExtensionManager.isLoaded
        val showPluginOptionSheet = remember { mutableStateOf(false) }


        LaunchedEffect("refreshPlugins") {
            launch {
                ExtensionManager.loadExistingPlugins(application!!)
            }
        }


        InfoBlock(
            modifier = Modifier.clickable {
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/Xed-Editor/pluginTemplate")))
            },
            icon = {
                Icon(
                    imageVector = Icons.Outlined.Info, contentDescription = null
                )
            },
            text = stringResource(strings.info_ext),
        )

        PreferenceGroup {
            if (isLoaded.value) {
                if (extensions.isEmpty()) {
                    Text(
                        text = stringResource(strings.no_ext), modifier = Modifier.padding(16.dp)
                    )
                } else {
                    extensions.keys.forEach { plugin ->
                        SettingsToggle(
                            onLongClick = {
                                selectedPlugin = plugin
                                showPluginOptionSheet.value = true
                            },
                            label = plugin.name,
                            default = Preference.getBoolean(key = "ext_" + plugin.packageName,false),
                            sideEffect = {
                                Preference.setBoolean(key = "ext_" + plugin.packageName,it)
                            },
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
                                            ExtensionManager.deletePlugin(it)
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
