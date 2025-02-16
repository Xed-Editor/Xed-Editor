package com.rk.xededitor.ui.screens.settings.developer_options

import android.app.Activity
import android.app.ActivityManager
import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavController
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.rk.components.compose.preferences.base.PreferenceGroup
import com.rk.components.compose.preferences.base.PreferenceLayout
import com.rk.libcommons.toast
import com.rk.resources.strings
import com.rk.xededitor.BuildConfig
import com.rk.xededitor.ui.activities.settings.SettingsRoutes
import com.rk.xededitor.ui.components.SettingsToggle
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch


private fun getMemoryUsage(context: Context):Int{
    val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
    val memoryInfo = activityManager.getProcessMemoryInfo(intArrayOf(android.os.Process.myPid()))
    //total memory in mb
    return memoryInfo[0].totalPss / 1024
}

@Composable
fun DeveloperOptions(modifier: Modifier = Modifier,navController: NavController) {
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
                        setNegativeButton(strings.cancel,null)
                        setPositiveButton(strings.ok){ _,_ ->
                            scope.launch {
                                delay(100)
                                throw RuntimeException("Force Crash")
                            }
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

            SettingsToggle(
                label = "Debugger",
                description = "Beanshell",
                showSwitch = false,
                default = false,
                isEnabled = BuildConfig.DEBUG,
                sideEffect = {
                    navController.navigate(SettingsRoutes.BeanshellREPL.route)
                }
            )
        }
    }

}