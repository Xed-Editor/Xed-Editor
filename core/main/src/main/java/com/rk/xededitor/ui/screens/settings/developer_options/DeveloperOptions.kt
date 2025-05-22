package com.rk.xededitor.ui.screens.settings.developer_options

//import com.rk.xededitor.MainActivity.file.ProjectManager
import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material3.Icon
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
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.rk.LogcatService
import com.rk.components.compose.preferences.base.PreferenceGroup
import com.rk.components.compose.preferences.base.PreferenceLayout
import com.rk.components.compose.preferences.switch.PreferenceSwitch
import com.rk.libcommons.toast
import com.rk.resources.strings
import com.rk.settings.Settings
import com.rk.xededitor.BuildConfig
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

    LaunchedEffect("DeveloperOptions") {
        memoryUsage.intValue = getMemoryUsage(context)
    }

    PreferenceLayout(label = "Developer Options") {
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
                description = "${memoryUsage.intValue} MB (click to refresh)",
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
                description = "Detects bad development practices like disk or network access on the main thread",
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
                description = "Force crash Xed-Editor if it becomes unresponsive for more than 5 seconds",
                modifier = modifier,
                onClick = {
                    state1 = !state1 || BuildConfig.DEBUG
                    Settings.anr_watchdog = state1
                })


            SettingsToggle(
                label = stringResource(strings.manage_storage),
                description = stringResource(strings.manage_storage),
                isEnabled = Build.VERSION.SDK_INT > Build.VERSION_CODES.Q,
                showSwitch = false,
                default = false,
                endWidget = {
                    Icon(
                        modifier = Modifier.padding(16.dp),
                        imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                        contentDescription = null
                    )
                },
                sideEffect = {
                    if (Build.VERSION.SDK_INT > Build.VERSION_CODES.Q) {
                        val intent =
                            Intent(android.provider.Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
                        intent.data = Uri.parse("package:${context.packageName}")
                        context.startActivity(intent)
                    }
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
                        toast("Debugger is not allowed on release builds for safety reasons")
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
                }
            )

            SettingsToggle(
                label = "LSP",
                description = "Connect to external lsp server",
                showSwitch = false,
                default = false,
                sideEffect = {
                    toast(strings.ni)
                }
            )


        }
    }

}