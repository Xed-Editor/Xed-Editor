package com.rk.settings.developer_options

import androidx.activity.compose.LocalActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavController
import com.rk.components.compose.preferences.base.PreferenceGroup
import com.rk.components.compose.preferences.base.PreferenceLayout
import com.rk.components.compose.preferences.switch.PreferenceSwitch
import com.rk.utils.dialog
import com.rk.resources.getString
import com.rk.resources.strings
import com.rk.settings.Settings
import com.rk.components.SettingsToggle
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private var flipperJob: Job? = null

@OptIn(DelicateCoroutinesApi::class)
@Composable
fun DeveloperOptions(modifier: Modifier = Modifier, navController: NavController) {
    val context = LocalContext.current
    val activity = LocalActivity.current

    val memoryUsage = remember { mutableStateOf("Unknown") }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO){
            while (isActive){
                delay(300)
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


            SettingsToggle(
                label = stringResource(strings.desktop_mode),
                description = stringResource(strings.desktop_mode_desc),
                showSwitch = true,
                default = Settings.desktopMode,
                sideEffect = {
                    Settings.desktopMode = it
                }
            )


            SettingsToggle(
                label = "Theme Flipper",
                description = "Constantly change the theme mode every 7s",
                showSwitch = true,
                default = Settings.themeFlipper,
                sideEffect = {
                    Settings.themeFlipper = it
                    if (it){
                        startThemeFlipperIfNotRunning()
                    }
                }
            )







        }
    }

}


fun startThemeFlipperIfNotRunning(){
    if (flipperJob == null || flipperJob?.isActive?.not() == true){
        flipperJob = GlobalScope.launch {
            runCatching {
                while (isActive && Settings.themeFlipper){
                    delay(7000)

                    val mode = if (Settings.default_night_mode == AppCompatDelegate.MODE_NIGHT_NO){
                        AppCompatDelegate.MODE_NIGHT_YES
                    }else{
                        AppCompatDelegate.MODE_NIGHT_NO
                    }


                    Settings.default_night_mode = mode

                    withContext(Dispatchers.Main){
                        AppCompatDelegate.setDefaultNightMode(mode)
                    }

                }
            }.onFailure { it.printStackTrace() }
        }
    }
}