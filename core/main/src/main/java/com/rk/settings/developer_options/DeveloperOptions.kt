package com.rk.settings.developer_options

import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.rk.components.compose.preferences.base.PreferenceGroup
import com.rk.components.compose.preferences.base.PreferenceLayout
import com.rk.components.compose.preferences.switch.PreferenceSwitch
import com.rk.utils.dialog
import com.rk.editor.textmateSources
import com.rk.utils.toast
import com.rk.resources.getString
import com.rk.resources.strings
import com.rk.settings.Settings
import com.rk.tabs.lsp_connections
import com.rk.components.SettingsToggle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext


@Composable
fun DeveloperOptions(modifier: Modifier = Modifier, navController: NavController) {
    val context = LocalContext.current
    val activity = LocalActivity.current

    val memoryUsage = remember { mutableStateOf("Unknown") }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO){
            while (isActive){
                delay(700)
                val runtime = Runtime.getRuntime()
                val usedMem = (runtime.totalMemory() - runtime.freeMemory()) / (1024 * 1024)
                memoryUsage.value = "${usedMem}/${runtime.maxMemory() / (1024 * 1024)}MB"
            }
        }
    }

    PreferenceLayout(label = stringResource(strings.debug_options)) {
        PreferenceGroup {
            SettingsToggle(
                label = stringResource(strings.force_crash),
                description = stringResource(strings.force_crash_desc),
                showSwitch = false,
                default = false,
                sideEffect = {
                    dialog(context = activity, title = strings.force_crash.getString(), msg = strings.force_crash_confirm.getString(), onCancel = {}, onOk = {
                        Thread {
                            throw RuntimeException("Force Crash")
                        }.start()
                    })
                }
            )
            SettingsToggle(
                label = stringResource(strings.memory_usage),
                description = memoryUsage.value,
                showSwitch = false,
                default = false,
            )

            var state by remember {
                mutableStateOf(Settings.strict_mode)
            }
            PreferenceSwitch(checked = state,
                onCheckedChange = {
                    state = it
                    Settings.strict_mode = state
                },
                label = stringResource(strings.strict_mode),
                description = stringResource(strings.strict_mode_desc),
                modifier = modifier,
                onClick = {
                    state = !state
                    Settings.strict_mode = state
                })


            var state1 by remember {
                mutableStateOf(Settings.anr_watchdog)
            }
            PreferenceSwitch(checked = state1,
                onCheckedChange = {
                    state1 = it
                    Settings.anr_watchdog = state1
                },
                label = stringResource(strings.anr_watchdog),
                description = stringResource(strings.anr_watchdog_desc),
                modifier = modifier,
                onClick = {
                    state1 = !state1
                    Settings.anr_watchdog = state1
                })


            SettingsToggle(
                label = stringResource(strings.verbose_errors),
                description = stringResource(strings.verbose_errors_desc),
                showSwitch = true,
                default = Settings.verbose_error,
                sideEffect = {
                    Settings.verbose_error = it
                }
            )

            var showDialog by remember { mutableStateOf(false) }
            if (showDialog) {
                PortAndExtensionDialog(
                    onDismiss = { showDialog = false },
                    onConfirm = { port, extension ->
                       if (textmateSources[extension] == null){
                           toast(strings.unsupported_file_ext)
                           return@PortAndExtensionDialog
                       }
                        if (port.toIntOrNull() == null){
                            toast(strings.invalid_port)
                            return@PortAndExtensionDialog
                        }

                        port.toIntOrNull()?.let { lsp_connections[extension] = it }
                        //lspPort = port.toIntOrNull()
                        //lspExt = extension
                    }
                )
            }

            SettingsToggle(
                label = "LSP",
                description = stringResource(strings.lsp_desc),
                showSwitch = false,
                default = false,
                sideEffect = {
                    showDialog = true
                }
            )


        }
    }

}

@Composable
fun PortAndExtensionDialog(
    onDismiss: () -> Unit,
    onConfirm: (port: String, extension: String) -> Unit
) {
    var port by remember { mutableStateOf("") }
    var extension by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(strings.lsp_header)) },
        text = {
            Column {
                OutlinedTextField(
                    value = port,
                    onValueChange = { port = it },
                    label = { Text(stringResource(strings.port_number)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = extension,
                    onValueChange = { extension = it },
                    label = { Text(stringResource(strings.file_ext_example)) },
                    singleLine = true
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onConfirm(port, extension)
                onDismiss()
            }) {
                Text(stringResource(strings.ok))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(strings.cancel))
            }
        }
    )
}
