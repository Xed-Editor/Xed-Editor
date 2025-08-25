package com.rk.xededitor.ui.screens.settings.developer_options

//import com.rk.xededitor.MainActivity.file.ProjectManager
import android.app.ActivityManager
import android.content.Context
import android.content.Intent
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.rk.LogcatService
import com.rk.components.compose.preferences.base.PreferenceGroup
import com.rk.components.compose.preferences.base.PreferenceLayout
import com.rk.components.compose.preferences.switch.PreferenceSwitch
import com.rk.libcommons.editor.textmateSources
import com.rk.libcommons.toast
import com.rk.resources.strings
import com.rk.settings.Settings
import com.rk.xededitor.BuildConfig
import com.rk.tabs.lsp_connections
import com.rk.xededitor.ui.activities.settings.SettingsRoutes
import com.rk.xededitor.ui.components.SettingsToggle


private fun getMemoryUsage(context: Context): Int {
    val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
    val memoryInfo = activityManager.getProcessMemoryInfo(intArrayOf(android.os.Process.myPid()))
    //total memory in mb
    return memoryInfo[0].totalPss / 1024
}

@Composable
fun DeveloperOptions(modifier: Modifier = Modifier, navController: NavController) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    val memoryUsage = remember { mutableIntStateOf(-1) }

    LaunchedEffect("DebugOptions") {
        memoryUsage.intValue = getMemoryUsage(context)
    }

    PreferenceLayout(label = "Debug Options") {
        PreferenceGroup {
            SettingsToggle(
                label = "Force Crash",
                description = "Throw a runtime exception",
                showSwitch = false,
                default = false,
                sideEffect = {
                    MaterialAlertDialogBuilder(context).apply {
                        setTitle("Force Crash")
                        setMessage("Force Crash the app? app may freeze")
                        setNegativeButton(strings.cancel, null)
                        setPositiveButton(strings.ok) { _, _ ->
                            Thread {
                                throw RuntimeException("Force Crash")
                            }.start()
                        }
                        show()
                    }

                }
            )
            SettingsToggle(
                label = "Memory Usage",
                description = "${memoryUsage.intValue} MB",
                showSwitch = false,
                default = false,
                sideEffect = {
                    memoryUsage.intValue = getMemoryUsage(context)
                }
            )

            var state by remember {
                mutableStateOf(Settings.strict_mode || BuildConfig.DEBUG)
            }
            PreferenceSwitch(checked = state,
                onCheckedChange = {
                    state = it || BuildConfig.DEBUG
                    Settings.strict_mode = state
                },
                label = "Strict Mode",
                description = "Detect disk or network access on the main thread",
                modifier = modifier,
                onClick = {
                    state = !state || BuildConfig.DEBUG
                    Settings.strict_mode = state
                })


            var state1 by remember {
                mutableStateOf(Settings.anr_watchdog || BuildConfig.DEBUG)
            }
            PreferenceSwitch(checked = state1,
                onCheckedChange = {
                    state1 = it || BuildConfig.DEBUG
                    Settings.anr_watchdog = state1
                },
                label = "ANR Watchdog",
                description = "Terminate Xed-Editor if it becomes unresponsive for more than 5 seconds",
                modifier = modifier,
                onClick = {
                    state1 = !state1 || BuildConfig.DEBUG
                    Settings.anr_watchdog = state1
                })


            SettingsToggle(
                label = "Verbose Errors",
                description = "Include stacktrace in error dialogs",
                showSwitch = true,
                default = Settings.verbose_error,
                sideEffect = {
                    Settings.verbose_error = it
                }
            )

            SettingsToggle(
                label = "Debugger",
                description = "Beanshell",
                showSwitch = false,
                default = false,
                sideEffect = {
                    if (BuildConfig.DEBUG) {
                        navController.navigate(SettingsRoutes.BeanshellREPL.route)
                    } else {
                        toast("Debugger is not allowed on release builds")
                    }
                }
            )


            SettingsToggle(
                label = stringResource(strings.capture_logcat),
                description = stringResource(strings.capture_logcat_desc),
                showSwitch = false,
                default = false,
                sideEffect = {
                    context.startService(Intent(context, LogcatService::class.java))
                    toast("capturing logcat")
                }
            )

            var showDialog by remember { mutableStateOf(false) }
            if (showDialog) {
                PortAndExtensionDialog(
                    onDismiss = { showDialog = false },
                    onConfirm = { port, extension ->
                       if (textmateSources[extension] == null){
                           toast("Unsupported file extension")
                           return@PortAndExtensionDialog
                       }
                        if (port.toIntOrNull() == null){
                            toast("Invalid port")
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
                description = "Connect to external lsp server",
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
        title = { Text("Enter Port & Extension") },
        text = {
            Column {
                OutlinedTextField(
                    value = port,
                    onValueChange = { port = it },
                    label = { Text("Port number") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = extension,
                    onValueChange = { extension = it },
                    label = { Text("File extension (e.g. py)") },
                    singleLine = true
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onConfirm(port, extension)
                onDismiss()
            }) {
                Text("OK")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
