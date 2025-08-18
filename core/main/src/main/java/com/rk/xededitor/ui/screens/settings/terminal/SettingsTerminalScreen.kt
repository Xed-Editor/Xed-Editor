package com.rk.xededitor.ui.screens.settings.terminal

import android.view.LayoutInflater
import android.view.View
import android.widget.EditText
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.rk.App
import com.rk.DocumentProvider
import com.rk.components.compose.preferences.base.PreferenceGroup
import com.rk.components.compose.preferences.base.PreferenceLayout
import com.rk.components.compose.preferences.switch.PreferenceSwitch
import com.rk.file.UriWrapper
import com.rk.file.child
import com.rk.file.sandboxDir
import com.rk.libcommons.LoadingPopup
import com.rk.libcommons.PathUtils.toPath
import com.rk.libcommons.dpToPx
import com.rk.libcommons.toast
import com.rk.resources.strings
import com.rk.settings.Settings
import com.rk.xededitor.R
import com.rk.xededitor.ui.activities.main.MainActivity
import com.rk.xededitor.ui.activities.settings.SettingsActivity
import com.rk.xededitor.ui.components.SettingsToggle
import com.rk.xededitor.ui.components.ValueSlider
import com.rk.xededitor.ui.screens.terminal.terminalView
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
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

                val fileObject = UriWrapper(uri,false)

                val tempFile = App.getTempDir().child("terminal-backup.tar.gz")

                fileObject.getInputStream().use { inputStream ->
                    FileOutputStream(tempFile).use { outputStream ->
                        inputStream.copyTo(outputStream)
                    }
                }


                if (fileObject.canRead().not()) {
                    toast(strings.invalid_path)
                    return@rememberLauncherForActivityResult
                }

                val loading = LoadingPopup(context, null)
                loading.show()

                GlobalScope.launch(Dispatchers.IO) {
                    sandboxDir().deleteRecursively()
                    sandboxDir().mkdirs()

                    val result =
                        getRuntime().exec("tar -xf ${tempFile.absolutePath} -C ${sandboxDir()}")
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

            SettingsToggle(
                label = stringResource(strings.backup),
                description = "${stringResource(strings.terminal)} ${stringResource(strings.backup)}",
                showSwitch = false,
                default = false,
                sideEffect = {

                    SettingsActivity.instance!!.fileManager.selectDirForNewFileLaunch(fileName = "terminal-backup.tar.gz"){ fileObject ->
                        if (fileObject != null){
                            val targetFile = App.getTempDir().child("terminal-backup.tar.gz")

                            fileObject.getInputStream().use { inputStream ->
                                FileOutputStream(targetFile).use { outputStream ->
                                    inputStream.copyTo(outputStream)
                                }
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
                                FileInputStream(targetFile).use { inputStream ->
                                    fileObject.getOutPutStream(false).use { outputStream ->
                                        inputStream.copyTo(outputStream)
                                    }
                                }

                            }
                        }
                    }
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