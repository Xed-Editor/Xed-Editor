package com.rk.xededitor.ui.screens.settings.terminal

import android.content.Context
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Switch
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.jaredrummler.ktsh.Shell
import com.rk.settings.PreferencesData
import com.rk.settings.PreferencesData.getBoolean
import com.rk.settings.PreferencesKeys
import com.rk.xededitor.R
import com.rk.xededitor.rkUtils
import com.rk.xededitor.ui.components.InputDialog
import java.io.File
import java.nio.file.Files
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import org.robok.engine.core.components.compose.preferences.base.PreferenceLayout
import org.robok.engine.core.components.compose.preferences.category.PreferenceCategory

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
    PreferenceLayout(label = stringResource(id = R.string.terminal), backArrowVisible = true) {
        val context = LocalContext.current

        var failSafe by remember { mutableStateOf(getBoolean(PreferencesKeys.FAIL_SAFE, false)) }

        var ctrlWorkAround by remember {
            mutableStateOf(getBoolean(PreferencesKeys.CTRL_WORKAROUND, false))
        }

        var forceChar by remember { mutableStateOf(getBoolean(PreferencesKeys.FORCE_CHAR, false)) }

        var link2sym by remember { mutableStateOf(getBoolean(PreferencesKeys.LINK2SYMLINK, true)) }

        var ashememFd by remember { mutableStateOf(getBoolean(PreferencesKeys.ASHMEM_MEMFD, true)) }

        var sysvipc by remember { mutableStateOf(getBoolean(PreferencesKeys.SYSVIPC, true)) }

        var killOnExit by remember {
            mutableStateOf(getBoolean(PreferencesKeys.KILL_ON_EXIT, true))
        }

        var showVirtualKeyboard by remember {
            mutableStateOf(getBoolean(PreferencesKeys.SHOW_VIRTUAL_KEYBOARD, true))
        }

        PreferenceCategory(
            label = stringResource(id = R.string.fail_safe),
            description = stringResource(id = R.string.failsafe_desc),
            iconResource = R.drawable.android,
            onNavigate = {
                failSafe = !failSafe
                PreferencesData.setBoolean(PreferencesKeys.FAIL_SAFE, failSafe)
            },
            endWidget = {
                Switch(
                    modifier = Modifier.padding(12.dp).height(24.dp),
                    checked = failSafe,
                    onCheckedChange = null,
                )
            },
        )

        PreferenceCategory(
            label = stringResource(id = R.string.show_virtual_keyboard),
            description = stringResource(id = R.string.show_virtual_keyboard_desc),
            iconResource = R.drawable.edit,
            onNavigate = {
                showVirtualKeyboard = !showVirtualKeyboard
                PreferencesData.setBoolean(
                    PreferencesKeys.SHOW_VIRTUAL_KEYBOARD,
                    showVirtualKeyboard,
                )
            },
            endWidget = {
                Switch(
                    modifier = Modifier.padding(12.dp).height(24.dp),
                    checked = showVirtualKeyboard,
                    onCheckedChange = null,
                )
            },
        )

        var showLShellDialog by remember { mutableStateOf(false) }
        PreferenceCategory(
            label = stringResource(id = R.string.Lshell),
            description = stringResource(id = R.string.Lshell_desc),
            iconResource = R.drawable.terminal,
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
                title = stringResource(id = R.string.Lshell),
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
                                "echo \"$shell\" > ${context.filesDir.parentFile.absolutePath}/shell"
                            )
                        } else {
                            rkUtils.toast(rkUtils.getString(R.string.file_exist_not))
                        }
                    } else {
                        rkUtils.toast(rkUtils.getString(R.string.cannot_be_empty))
                    }
                },
                onDismiss = { showLShellDialog = false },
            )
        }

        var showTextSizeDialog by remember { mutableStateOf(false) }
        PreferenceCategory(
            label = stringResource(id = R.string.terminal_text_size),
            description = stringResource(id = R.string.terminal_text_size_desc),
            iconResource = R.drawable.terminal,
            onNavigate = { showTextSizeDialog = true },
        )

        if (showTextSizeDialog) {
            var inputValue by remember {
                mutableStateOf(PreferencesData.getString(PreferencesKeys.TERMINAL_TEXT_SIZE, "14"))
            }
            InputDialog(
                title = stringResource(id = R.string.text_size),
                inputLabel = stringResource(R.string.terminal_text_size),
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
                                if (size != null && size > 32) rkUtils.getString(R.string.v_large)
                                else rkUtils.getString(R.string.v_small)
                            )
                        }
                    } else {
                        rkUtils.toast(rkUtils.getString(R.string.inavalid_v))
                    }
                },
                onDismiss = { showTextSizeDialog = false },
            )
        }

        PreferenceCategory(
            label = stringResource(id = R.string.useCtrlWorkaround),
            description = stringResource(id = R.string.useCtrlWorkaround_desc),
            iconResource = R.drawable.terminal,
            onNavigate = {
                ctrlWorkAround = !ctrlWorkAround
                PreferencesData.setBoolean(PreferencesKeys.CTRL_WORKAROUND, ctrlWorkAround)
            },
            endWidget = {
                Switch(
                    modifier = Modifier.padding(12.dp).height(24.dp),
                    checked = ctrlWorkAround,
                    onCheckedChange = null,
                )
            },
        )

        PreferenceCategory(
            label = stringResource(id = R.string.force_char),
            description = stringResource(id = R.string.force_char_desc),
            iconResource = R.drawable.edit,
            onNavigate = {
                forceChar = !forceChar
                PreferencesData.setBoolean(PreferencesKeys.FORCE_CHAR, forceChar)
            },
            endWidget = {
                Switch(
                    modifier = Modifier.padding(12.dp).height(24.dp),
                    checked = forceChar,
                    onCheckedChange = null,
                )
            },
        )

        PreferenceCategory(
            label = stringResource(id = R.string.sim_hard_links),
            description = stringResource(id = R.string.sim_hard_links_desc),
            iconResource = R.drawable.terminal,
            onNavigate = {
                link2sym = !link2sym
                PreferencesData.setBoolean(PreferencesKeys.LINK2SYMLINK, link2sym)
                updateProotArgs(context)
            },
            endWidget = {
                Switch(
                    modifier = Modifier.padding(12.dp).height(24.dp),
                    checked = link2sym,
                    onCheckedChange = null,
                )
            },
        )

        PreferenceCategory(
            label = stringResource(id = R.string.sim_ashmem),
            description = stringResource(id = R.string.sim_ashmem),
            iconResource = R.drawable.terminal,
            onNavigate = {
                ashememFd = !ashememFd
                PreferencesData.setBoolean(PreferencesKeys.ASHMEM_MEMFD, ashememFd)
                updateProotArgs(context)
            },
            endWidget = {
                Switch(
                    modifier = Modifier.padding(12.dp).height(24.dp),
                    checked = ashememFd,
                    onCheckedChange = null,
                )
            },
        )

        PreferenceCategory(
            label = stringResource(id = R.string.sysvipc),
            description = stringResource(id = R.string.sysvipc),
            iconResource = R.drawable.terminal,
            onNavigate = {
                sysvipc = !sysvipc
                PreferencesData.setBoolean(PreferencesKeys.SYSVIPC, sysvipc)
                updateProotArgs(context)
            },
            endWidget = {
                Switch(
                    modifier = Modifier.padding(12.dp).height(24.dp),
                    checked = sysvipc,
                    onCheckedChange = null,
                )
            },
        )

        PreferenceCategory(
            label = stringResource(id = R.string.kill_on_exit),
            description = stringResource(id = R.string.kill_on_exit_desc),
            iconResource = R.drawable.terminal,
            onNavigate = {
                killOnExit = !killOnExit
                PreferencesData.setBoolean(PreferencesKeys.KILL_ON_EXIT, killOnExit)
                updateProotArgs(context)
            },
            endWidget = {
                Switch(
                    modifier = Modifier.padding(12.dp).height(24.dp),
                    checked = killOnExit,
                    onCheckedChange = null,
                )
            },
        )
    }
}
