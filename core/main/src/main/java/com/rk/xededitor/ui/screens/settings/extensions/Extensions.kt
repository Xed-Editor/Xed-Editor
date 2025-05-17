package com.rk.xededitor.ui.screens.settings.extensions

import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
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
import androidx.compose.runtime.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.pm.PackageInfoCompat
import androidx.documentfile.provider.DocumentFile
import com.rk.components.compose.preferences.base.PreferenceGroup
import com.rk.components.compose.preferences.base.PreferenceLayout
import com.rk.components.compose.preferences.base.PreferenceTemplate
import com.rk.components.compose.preferences.switch.PreferenceSwitch
import com.rk.extension.Extension
import com.rk.extension.ExtensionManager
import com.rk.file_wrapper.UriWrapper
import com.rk.libcommons.DefaultScope
import com.rk.libcommons.LoadingPopup
import com.rk.libcommons.application
import com.rk.libcommons.dialog
import com.rk.libcommons.errorDialog
import com.rk.libcommons.safeLaunch
import com.rk.resources.getString
import com.rk.resources.strings
import com.rk.settings.Preference
import com.rk.xededitor.ui.components.BottomSheetContent
import com.rk.xededitor.ui.components.InfoBlock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

var selectedPlugin: Extension? = null

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Extensions(modifier: Modifier = Modifier) {
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
            val isApk = fileObject.getName().endsWith(".apk")

            if (exists && canRead && isApk) {
                loading = LoadingPopup(context as Activity, null).show()
                loading.setMessage(strings.installing.getString())
                DefaultScope.launch {
                    ExtensionManager.installPlugin(activity!!, fileObject)
                    ExtensionManager.indexPlugins(application!!)
                }

                loading.hide()
            } else {
                errorDialog("Install criteria failed \nis_apk = $isApk\ncan_read = $canRead\n exists = $exists\nuri = ${fileObject.getAbsolutePath()}")
            }
        }.onFailure {
            loading?.hide()
            errorDialog(it)
        }

    }

    PreferenceLayout(label = stringResource(strings.ext), backArrowVisible = true, fab = {
        ExtendedFloatingActionButton(
            onClick = { filePickerLauncher.launch(arrayOf("application/vnd.android.package-archive")) },
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
                ExtensionManager.indexPlugins(application!!)
            }
        }


        InfoBlock(
            modifier = Modifier.clickable {
                context.startActivity(
                    Intent(
                        Intent.ACTION_VIEW,
                        Uri.parse("https://github.com/Xed-Editor/pluginTemplate")
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
            if (isLoaded.value) {
                if (extensions.isEmpty()) {
                    Text(
                        text = stringResource(strings.no_ext), modifier = Modifier.padding(16.dp)
                    )
                } else {
                    extensions.keys.forEach { plugin ->
                        var state by remember {
                            mutableStateOf(
                                Preference.getBoolean(
                                    key = "ext_" + plugin.packageName,
                                    false
                                )
                            )
                        }


                        val sideEffect: (Boolean) -> Unit = {
                            if (it) {
                                scope.safeLaunch {
                                    val loadingPopup = LoadingPopup(context)
                                    loadingPopup.setMessage(strings.installing.getString())
                                    loadingPopup.show()

                                    val pm = context.packageManager
                                    val info = pm.getPackageArchiveInfo(
                                        plugin.apkFile.absolutePath,
                                        PackageManager.GET_META_DATA or PackageManager.GET_ACTIVITIES
                                    )!!
                                    info.applicationInfo!!.sourceDir = plugin.apkFile.absolutePath
                                    info.applicationInfo!!.publicSourceDir =
                                        plugin.apkFile.absolutePath

                                    val appInfo = info.applicationInfo!!

                                    val metadata = appInfo.metaData

                                    val minSdkVersion = metadata.getInt("minXedVersionCode", -1)
                                    val targetSdkVersion =
                                        metadata.getInt("targetXedVersionCode", -1)
                                    val xedVersionCode = PackageInfoCompat.getLongVersionCode(
                                        context.packageManager.getPackageInfo(
                                            context.packageName,
                                            0
                                        )
                                    )


                                    if (minSdkVersion != -1 && targetSdkVersion != -1 && minSdkVersion <= xedVersionCode && targetSdkVersion <= xedVersionCode) {
                                        Preference.setBoolean(
                                            key = "ext_" + plugin.packageName,
                                            true
                                        )
                                        state = true
                                    } else {
                                        val reason: String =
                                            if (minSdkVersion > xedVersionCode && minSdkVersion != -1 && targetSdkVersion != -1) {
                                                "Xed-Editor is outdated minimum version code required is $minSdkVersion while current version code is $xedVersionCode"
                                            } else if (targetSdkVersion < xedVersionCode && minSdkVersion != -1 && targetSdkVersion != -1) {
                                                "Plugin ${plugin.name} was made for an older version of Xed-Editor, ask the plugin developer to update the plugin"
                                            } else if (minSdkVersion == -1 || targetSdkVersion == -1) {
                                                "Undefined minXedVersionCode or targetXedVersionCode"
                                            } else {
                                                "Unknown error while parsing Xed Version code info from plugin"
                                            }

                                        dialog(
                                            context = activity,
                                            title = strings.failed.getString(),
                                            msg = "Enabling plugin ${plugin.name} failed \nreason: \n$reason",
                                            onOk = {})
                                    }

                                    delay(500)
                                    loadingPopup.hide()
                                }
                            } else {
                                Preference.setBoolean(key = "ext_" + plugin.packageName, false)
                                state = false
                            }
                        }
                        PreferenceSwitch(checked = state,
                            onCheckedChange = {
                                sideEffect(it)
                            },
                            onLongClick = {
                                selectedPlugin = plugin
                                showPluginOptionSheet.value = true
                            },
                            label = plugin.name,
                            modifier = modifier,
                            onClick = {
                                sideEffect(state.not())
                            })
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
