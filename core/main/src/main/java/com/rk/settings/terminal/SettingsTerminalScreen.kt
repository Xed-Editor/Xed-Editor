package com.rk.settings.terminal

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
import com.rk.App
import com.rk.DocumentProvider
import com.rk.components.compose.preferences.base.PreferenceGroup
import com.rk.components.compose.preferences.base.PreferenceLayout
import com.rk.components.compose.preferences.switch.PreferenceSwitch
import com.rk.file.child
import com.rk.file.localBinDir
import com.rk.file.localLibDir
import com.rk.file.sandboxDir
import com.rk.file.toFileObject
import com.rk.utils.LoadingPopup
import com.rk.utils.dialog
import com.rk.utils.dpToPx
import com.rk.utils.toast
import com.rk.resources.getString
import com.rk.resources.strings
import com.rk.settings.Settings
import com.rk.activities.main.MainActivity
import com.rk.activities.settings.SettingsActivity
import com.rk.components.SettingsToggle
import com.rk.components.ValueSlider
import com.rk.file.createFileIfNot
import com.rk.file.localDir
import com.rk.settings.app.InbuiltFeatures
import com.rk.terminal.terminalView
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
        val activity = LocalActivity.current

        if (InbuiltFeatures.debugMode.state.value){
            PreferenceGroup {
                SettingsToggle(
                    label = stringResource(strings.failsafe_mode),
                    description = stringResource(strings.failsafe_mode_desc),
                    default = !Settings.sandbox,
                    sideEffect = {
                        Settings.sandbox = !it
                    }
                )
            }
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
            var seccomp by remember { mutableStateOf(Settings.seccomp) }

            SettingsToggle(
                label = "SECCOMP",
                default = seccomp,
                description = stringResource(strings.seccomp_desc),
                sideEffect = {
                    Settings.seccomp = it
                    seccomp = it
                },
                showSwitch = true,
            )

            val restore = rememberLauncherForActivityResult(
                ActivityResultContracts.GetContent()
            ) { uri ->
                if (uri == null) {
                    return@rememberLauncherForActivityResult
                }

                val loading = LoadingPopup(context, null)
                loading.show()

                GlobalScope.launch(Dispatchers.IO) {
                    val fileObject = uri.toFileObject(expectedIsFile = true)

                    val tempFile = App.getTempDir().child("terminal-backup.tar.gz")

                    fileObject.getInputStream().use { inputStream ->
                        FileOutputStream(tempFile).use { outputStream ->
                            inputStream.copyTo(outputStream)
                        }
                    }


                    if (fileObject.canRead().not()) {
                        toast(strings.permission_denied)
                        loading.hide()
                        return@launch
                    }

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

                    localDir().child(".terminal_setup_ok_DO_NOT_REMOVE").createFileIfNot()
                }
            }

            SettingsToggle(
                label = stringResource(strings.backup),
                description = "${stringResource(strings.terminal)} ${stringResource(strings.backup)}",
                showSwitch = false,
                default = false,
                sideEffect = {
                    val fileManager = if (SettingsActivity.instance != null){
                        SettingsActivity.instance!!.fileManager
                    }else{
                        MainActivity.instance!!.fileManager
                    }


                    fileManager.createNewFile(mimeType = "application/octet-stream", title = "terminal-backup.tar.gz"){ fileObject ->
                        GlobalScope.launch {
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

            SettingsToggle(
                label = stringResource(strings.uninstall),
                default = false,
                description = stringResource(strings.uninstall_terminal),
                showSwitch = false,
                sideEffect = {
                    dialog(context = activity, title = strings.attention.getString(), msg = strings.uninstall_terminal_warning.getString(), onCancel = {}, okString = strings.delete, onOk = {
                        GlobalScope.launch(Dispatchers.IO){
                            val loading = LoadingPopup(context, null)
                            loading.show()
                            runCatching {
                                localBinDir().deleteRecursively()
                                localLibDir().deleteRecursively()
                                sandboxDir().deleteRecursively()
                                localDir().child(".terminal_setup_ok_DO_NOT_REMOVE").delete()
                            }
                            loading.hide()
                        }
                    })
                },
            )
        }

        PreferenceGroup {
            SettingsToggle(
                label = stringResource(strings.project_as_wk), description = stringResource(strings.project_as_wk_desc),
                default = Settings.project_as_pwd,
                sideEffect = {
                    Settings.project_as_pwd = it
                },
                showSwitch = true,
            )

            var exposeHomeDirState by remember { mutableStateOf(Settings.expose_home_dir) }
            PreferenceSwitch(
                checked = exposeHomeDirState,
                onCheckedChange = {
                    if (it) {
                        dialog(
                            context = activity,
                            title = strings.attention.getString(),
                            msg = strings.saf_expose_warning.getString(),
                            okString = strings.continue_action,
                            onCancel = {},
                            onOk = {
                                Settings.expose_home_dir = true
                                DocumentProvider.setDocumentProviderEnabled(context, true)
                                exposeHomeDirState = true
                            }
                        )
                    } else {
                        Settings.expose_home_dir = false
                        exposeHomeDirState = false
                        DocumentProvider.setDocumentProviderEnabled(context, false)
                    }
                },
                label = stringResource(strings.expose_saf),
                description = stringResource(strings.expose_saf_desc)
            )
        }
    }
}