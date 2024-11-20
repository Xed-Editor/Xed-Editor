package com.rk.xededitor.ui.screens.settings.terminal

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.jaredrummler.ktsh.Shell
import com.rk.resources.drawable
import com.rk.resources.strings
import com.rk.settings.PreferencesData
import com.rk.settings.PreferencesData.getBoolean
import com.rk.settings.PreferencesKeys
import com.rk.xededitor.rkUtils
import com.rk.xededitor.ui.components.InputDialog
import com.rk.xededitor.ui.components.SettingsToggle
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import org.robok.engine.core.components.compose.preferences.base.PreferenceLayout
import org.robok.engine.core.components.compose.preferences.category.PreferenceCategory
import java.io.File
import java.nio.file.Files

@OptIn(DelicateCoroutinesApi::class)
fun updateProotArgs(context: Context): Boolean {
    GlobalScope.launch(Dispatchers.IO) {
        val link2sym = getBoolean(PreferencesKeys.LINK2SYMLINK, false)
        val ashmemfd = getBoolean(PreferencesKeys.ASHMEM_MEMFD, true)
        val sysvipc = getBoolean(PreferencesKeys.SYSVIPC, true)
        val killOnExit = getBoolean(PreferencesKeys.KILL_ON_EXIT, true)

        val sb = StringBuilder()
        if (link2sym) {
            sb.append(" --link2symlink")
        }
        if (ashmemfd) {
            sb.append(" --ashmem-memfd")
        }
        if (sysvipc) {
            sb.append(" --sysvipc")
        }
        if (killOnExit) {
            sb.append(" --kill-on-exit ")
        }

        Shell.SH.apply {
            run("echo $sb > ${File(context.filesDir.parentFile, "proot_args").absolutePath}")
            shutdown()
        }
    }
    return true
}

@Composable
fun SettingsTerminalScreen() {
    PreferenceLayout(label = stringResource(id = strings.terminal), backArrowVisible = true) {
        val context = LocalContext.current

        SettingsToggle(
            label = stringResource(id = strings.fail_safe),
            description = stringResource(id = strings.failsafe_desc),
            iconRes = drawable.android,
            key = PreferencesKeys.FAIL_SAFE,
            default = false
        )
        
        SettingsToggle(
            label = stringResource(id = strings.show_virtual_keyboard),
            description = stringResource(id = strings.show_virtual_keyboard_desc),
            iconRes = drawable.edit,
            key = PreferencesKeys.SHOW_VIRTUAL_KEYBOARD,
            default = true
        )
        
        SettingsToggle(
            label = stringResource(id = strings.useCtrlWorkaround),
            description = stringResource(id = strings.useCtrlWorkaround_desc),
            iconRes = drawable.terminal,
            key = PreferencesKeys.CTRL_WORKAROUND,
            default = false
        )
        
        SettingsToggle(
            label = stringResource(id = strings.force_char),
            description = stringResource(id = strings.force_char_desc),
            iconRes = drawable.edit,
            key = PreferencesKeys.FORCE_CHAR,
            default = true
        )
        
        SettingsToggle(
            label = stringResource(id = strings.sim_hard_links),
            description = stringResource(id = strings.sim_hard_links_desc),
            iconRes = drawable.terminal,
            key = PreferencesKeys.LINK2SYMLINK,
            default = true,
            sideEffect = {
                updateProotArgs(context)
            }
        )
        
        SettingsToggle(
            label = stringResource(id = strings.sim_ashmem),
            description = stringResource(id = strings.sim_ashmem),
            iconRes = drawable.terminal,
            key = PreferencesKeys.ASHMEM_MEMFD,
            default = true,
            sideEffect = {
                updateProotArgs(context)
            }
        )
        
        
        SettingsToggle(
            label = stringResource(id = strings.sysvipc),
            description = stringResource(id = strings.sysvipc),
            iconRes = drawable.terminal,
            key = PreferencesKeys.SYSVIPC,
            default = true,
            sideEffect = {
                updateProotArgs(context)
            }
        )
        
        SettingsToggle(
            label = stringResource(id = strings.kill_on_exit),
            description = stringResource(id = strings.kill_on_exit_desc),
            iconRes = drawable.terminal,
            key = PreferencesKeys.KILL_ON_EXIT,
            default = true,
            sideEffect = {
                updateProotArgs(context)
            }
        )
        
        
        
        
        var showLShellDialog by remember { mutableStateOf(false) }
        PreferenceCategory(
            label = stringResource(id = strings.Lshell),
            description = stringResource(id = strings.Lshell_desc),
            iconResource = drawable.terminal,
            onNavigate = { showLShellDialog = true },
        )

        if (showLShellDialog) {
            var inputValue by remember {
                mutableStateOf(
                    PreferencesData.getString(PreferencesKeys.SHELL, "/bin/sh")
                        .removePrefix(File(context.filesDir.parentFile, "rootfs/").absolutePath)
                )
            }
            InputDialog(
                title = stringResource(id = strings.Lshell),
                inputLabel = "eg. /bin/sh",
                inputValue = inputValue,
                onInputValueChange = { inputValue = it },
                onConfirm = {
                    val shell = inputValue
                    if (shell.isNotEmpty()) {
                        val absoluteShell = File(context.filesDir.parentFile, "rootfs/$shell")
                        if (
                            absoluteShell.exists() || Files.isSymbolicLink(absoluteShell.toPath())
                        ) {
                            PreferencesData.setString(
                                PreferencesKeys.SHELL,
                                absoluteShell.absolutePath,
                            )
                            Shell.SH.run(
                                "echo \"$shell\" > ${context.filesDir!!.parentFile!!.absolutePath}/shell"
                            )
                        } else {
                            rkUtils.toast(rkUtils.getString(strings.file_exist_not))
                        }
                    } else {
                        rkUtils.toast(rkUtils.getString(strings.cannot_be_empty))
                    }
                },
                onDismiss = { showLShellDialog = false },
            )
        }

        var showTextSizeDialog by remember { mutableStateOf(false) }
        PreferenceCategory(
            label = stringResource(id = strings.terminal_text_size),
            description = stringResource(id = strings.terminal_text_size_desc),
            iconResource = drawable.terminal,
            onNavigate = { showTextSizeDialog = true },
        )

        if (showTextSizeDialog) {
            var inputValue by remember {
                mutableStateOf(PreferencesData.getString(PreferencesKeys.TERMINAL_TEXT_SIZE, "14"))
            }
            InputDialog(
                title = stringResource(id = strings.text_size),
                inputLabel = stringResource(strings.terminal_text_size),
                inputValue = inputValue,
                onInputValueChange = { inputValue = it },
                onConfirm = {
                    val text = inputValue
                    if (text.all { it.isDigit() }) {
                        val size = text.toIntOrNull()
                        if (size != null && size in 8..32) {
                            PreferencesData.setString(PreferencesKeys.TERMINAL_TEXT_SIZE, text)
                        } else {
                            rkUtils.toast(
                                if (size != null && size > 32) rkUtils.getString(strings.v_large)
                                else rkUtils.getString(strings.v_small)
                            )
                        }
                    } else {
                        rkUtils.toast(rkUtils.getString(strings.inavalid_v))
                    }
                },
                onDismiss = { showTextSizeDialog = false },
            )
        }
    }
}
