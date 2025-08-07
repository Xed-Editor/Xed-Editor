package com.rk.xededitor.ui.screens.settings.terminal

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.widget.EditText
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.rk.DocumentProvider
import com.rk.components.compose.preferences.base.PreferenceGroup
import com.rk.components.compose.preferences.base.PreferenceLayout
import com.rk.components.compose.preferences.base.PreferenceTemplate
import com.rk.components.compose.preferences.switch.PreferenceSwitch
import com.rk.isTermuxCompatible
import com.rk.isTermuxInstalled
import com.rk.libcommons.DefaultScope
import com.rk.libcommons.LoadingPopup
import com.rk.libcommons.PathUtils.toPath
import com.rk.libcommons.sandboxDir
import com.rk.libcommons.child
import com.rk.libcommons.dpToPx
import com.rk.libcommons.toast
import com.rk.resources.strings
import com.rk.settings.Settings
import com.rk.testExecPermission
import com.rk.xededitor.MainActivity.MainActivity
import com.rk.xededitor.R
import com.rk.xededitor.ui.components.BottomSheetContent
import com.rk.xededitor.ui.components.SettingsToggle
import com.rk.xededitor.ui.components.ValueSlider
import com.rk.xededitor.ui.screens.terminal.terminalView
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.lang.Runtime.getRuntime

@OptIn(DelicateCoroutinesApi::class)
@Composable
fun SettingsTerminalScreen() {
    PreferenceLayout(label = stringResource(id = strings.terminal), backArrowVisible = true) {
        val context = LocalContext.current

        PreferenceGroup {
            SettingsToggle(label = "FailSafe Mode", description = "Start terminal in maintenance mode", default = !Settings.sandbox, sideEffect = {
                Settings.sandbox = !it
            })
        }

        ValueSlider(
            label = {
                Text(stringResource(strings.text_size))
            },
            min = 10,
            max = 20,
            onValueChanged = {
                Settings.terminal_font_size = it
                terminalView.get()?.setTextSize(dpToPx(it.toFloat(), context))
            }
        )

        PreferenceGroup {
            val context = LocalContext.current
            val activity = LocalActivity.current

            val restore = rememberLauncherForActivityResult(
                ActivityResultContracts.GetContent()
            ) { uri ->
                if (uri == null) {
                    toast(strings.invalid_path)
                    return@rememberLauncherForActivityResult
                }

                val filePath = File(uri.toPath())

                if (filePath.exists().not() ||
                    filePath.canRead().not() ||
                    filePath.isFile.not() ||
                    filePath.canWrite().not()
                ) {
                    toast(strings.invalid_path)
                    return@rememberLauncherForActivityResult
                }

                val loading = LoadingPopup(context, null)
                loading.show()

                GlobalScope.launch(Dispatchers.IO) {
                    sandboxDir().deleteRecursively()
                    sandboxDir().mkdirs()

                    val result =
                        getRuntime().exec("tar -xf ${filePath.absolutePath} -C ${sandboxDir()}")
                            .waitFor()
                    withContext(Dispatchers.Main) {
                        loading.hide()
                        if (result == 0) {
                            toast(strings.success)
                        } else {
                            toast(strings.failed)
                        }
                    }
                }


            }

            val backup = rememberLauncherForActivityResult(
                ActivityResultContracts.OpenDocumentTree()
            ) { uri ->
                if (uri == null) {
                    toast(strings.invalid_path)
                    return@rememberLauncherForActivityResult
                }

                val path = File(uri.toPath())

                if (path.exists().not() ||
                    path.canRead().not() ||
                    path.isDirectory.not() ||
                    path.canWrite().not()
                ) {
                    toast(strings.invalid_path)
                    return@rememberLauncherForActivityResult
                }


                MaterialAlertDialogBuilder(activity ?: context).apply {
                    setTitle(strings.file_name)
                    val popupView: View = LayoutInflater.from(MainActivity.activityRef.get()!!)
                        .inflate(R.layout.popup_new, null)
                    val editText = popupView.findViewById<EditText>(R.id.name)
                    editText.setText("terminal-backup.tar.gz")
                    setView(popupView)
                    setNeutralButton(strings.cancel, null)
                    setPositiveButton(strings.backup) { _, _ ->
                        val text = editText.text.toString()
                        if (text.isBlank()) {
                            toast(strings.inavalid_v)
                            return@setPositiveButton
                        }

                        val targetFile = path.child(text)
                        if (targetFile.exists()) {
                            toast(strings.already_exists)
                            return@setPositiveButton
                        }

                        val loading = LoadingPopup(context, null)
                        loading.show()

                        GlobalScope.launch(Dispatchers.IO) {
                            try {
                                val sandboxDir = sandboxDir().absolutePath
                                val targetPath = targetFile.absolutePath

                                val processBuilder = ProcessBuilder(
                                    "tar",
                                    "-czf",
                                    targetPath,
                                    ".",
                                    "--exclude=dev",
                                    "--exclude=sys",
                                    "--exclude=proc",
                                    "--exclude=system",
                                    "--exclude=apex",
                                    "--exclude=vendor",
                                    "--exclude=data",
                                    "--exclude=home",
                                    "--exclude=root",
                                    "--exclude=var/cache",
                                    "--exclude=var/tmp",
                                    "--exclude=lost+found",
                                    "--exclude=storage",
                                    "--exclude=system_ext",
                                    "--exclude=tmp",
                                    "--exclude=vendor",
                                    "--exclude=sdcard",
                                    "--exclude=storage"
                                ).apply {
                                    directory(File(sandboxDir))
                                    redirectErrorStream(true)
                                }

                                processBuilder.start().waitFor()

                                withContext(Dispatchers.Main) {
                                    loading.hide()
                                }
                            } catch (e: Exception) {
                                withContext(Dispatchers.Main) {
                                    loading.hide()
                                    toast("Error: ${e.message}")
                                }
                            }

                        }

                    }
                    show()
                }


            }

            SettingsToggle(
                label = stringResource(strings.backup),
                description = "${stringResource(strings.terminal)} ${stringResource(strings.backup)}",
                showSwitch = false,
                default = false,
                sideEffect = {
                    backup.launch(null)
                }
            )

            SettingsToggle(
                label = stringResource(strings.restore),
                description = "${stringResource(strings.restore)} ${stringResource(strings.terminal)} ${
                    stringResource(
                        strings.backup
                    )
                }",
                showSwitch = false,
                default = false,
                sideEffect = {
                    restore.launch("application/gzip")
                }
            )
        }

        PreferenceGroup {
            SettingsToggle(
                label = "Use Project as working directory", description = "Use Project as working directory in terminal",
                default = Settings.project_as_pwd,
                sideEffect = {
                    Settings.project_as_pwd = it
                },
                showSwitch = true,
            )
        }


        PreferenceGroup {
            var state by remember { mutableStateOf(Settings.expose_home_dir) }
            val sideEffect: (Boolean) -> Unit = {
                if (it) {
                    MaterialAlertDialogBuilder(context).apply {
                        setTitle(strings.attention)
                        setMessage(strings.saf_expose_warning)
                        setPositiveButton(strings.ok) { _, _ ->
                            Settings.expose_home_dir = true
                            DocumentProvider.setDocumentProviderEnabled(context, true)
                            state = true
                        }
                        setNegativeButton(strings.cancel, null)
                        show()
                    }
                } else {
                    Settings.expose_home_dir = false
                    state = false
                    DocumentProvider.setDocumentProviderEnabled(context, false)
                }
            }

            PreferenceSwitch(
                checked = state,
                onCheckedChange = { sideEffect(it) },
                label = stringResource(strings.expose_saf),
                description = stringResource(strings.expose_saf_desc),
                onClick = { sideEffect(!state) })

        }

    }
}