@file:Suppress("ktlint:standard:filename")

package com.rk.settings.debugOptions

import androidx.activity.compose.LocalActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavController
import com.rk.components.SettingsToggle
import com.rk.components.compose.preferences.base.PreferenceGroup
import com.rk.components.compose.preferences.base.PreferenceLayout
import com.rk.resources.getString
import com.rk.resources.strings
import com.rk.settings.Settings
import com.rk.utils.dialog
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private var flipperJob: Job? = null

@Suppress("ktlint:standard:function-naming")
@OptIn(DelicateCoroutinesApi::class)
@Composable
fun DeveloperOptions(modifier: Modifier = Modifier, navController: NavController) {
    val context = LocalContext.current
    val activity = LocalActivity.current

    val memoryUsage = remember { mutableStateOf("Unknown") }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            while (isActive) {
                delay(300)
                val runtime = Runtime.getRuntime()
                val usedMem = (runtime.totalMemory() - runtime.freeMemory()) / (1024 * 1024)
                memoryUsage.value = "$usedMem/${runtime.maxMemory() / (1024 * 1024)}MB"
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
                    dialog(
                        context = activity,
                        title = strings.force_crash.getString(),
                        msg = strings.force_crash_confirm.getString(),
                        onCancel = {},
                        onOk = { Thread { throw HarmlessException("Force crash") }.start() },
                    )
                },
            )

            SettingsToggle(
                label = stringResource(strings.memory_usage),
                description = memoryUsage.value,
                showSwitch = false,
                default = false,
            )

            SettingsToggle(
                label = stringResource(strings.strict_mode),
                description = stringResource(strings.strict_mode_desc),
                showSwitch = true,
                default = Settings.strict_mode,
                sideEffect = { Settings.strict_mode = it },
            )

            SettingsToggle(
                label = stringResource(strings.anr_watchdog),
                description = stringResource(strings.anr_watchdog_desc),
                default = Settings.anr_watchdog,
                sideEffect = { Settings.anr_watchdog = it },
            )

            SettingsToggle(
                label = stringResource(strings.verbose_errors),
                description = stringResource(strings.verbose_errors_desc),
                showSwitch = true,
                default = Settings.verbose_error,
                sideEffect = { Settings.verbose_error = it },
            )

            SettingsToggle(
                label = stringResource(strings.desktop_mode),
                description = stringResource(strings.desktop_mode_desc),
                showSwitch = true,
                default = Settings.desktopMode,
                sideEffect = { Settings.desktopMode = it },
            )

            SettingsToggle(
                label = stringResource(strings.theme_flipper),
                description = stringResource(strings.theme_flipper_desc),
                showSwitch = true,
                default = Settings.themeFlipper,
                sideEffect = {
                    Settings.themeFlipper = it
                    if (it) {
                        startThemeFlipperIfNotRunning()
                    }
                },
            )
        }
    }
}

fun startThemeFlipperIfNotRunning() {
    if (flipperJob == null || flipperJob?.isActive?.not() == true) {
        flipperJob =
            GlobalScope.launch(Dispatchers.IO) {
                runCatching {
                        while (isActive && Settings.themeFlipper) {
                            delay(7000)

                            val mode =
                                if (Settings.default_night_mode == AppCompatDelegate.MODE_NIGHT_NO) {
                                    AppCompatDelegate.MODE_NIGHT_YES
                                } else {
                                    AppCompatDelegate.MODE_NIGHT_NO
                                }

                            Settings.default_night_mode = mode

                            withContext(Dispatchers.Main) { AppCompatDelegate.setDefaultNightMode(mode) }
                        }
                    }
                    .onFailure { it.printStackTrace() }
            }
    }
}

class HarmlessException(msg: String) : Exception(msg)
