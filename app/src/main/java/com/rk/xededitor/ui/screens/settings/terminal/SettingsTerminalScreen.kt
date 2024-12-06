package com.rk.xededitor.ui.screens.settings.terminal

import android.content.Intent
import android.net.Uri
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavController
import com.rk.karbon_exec.isTermuxCompatible
import com.rk.karbon_exec.isTermuxInstalled
import com.rk.karbon_exec.testExecPermission
import com.rk.libcommons.DefaultScope
import com.rk.resources.getString
import com.rk.resources.strings
import com.rk.settings.PreferencesKeys
import com.rk.xededitor.rkUtils
import com.rk.xededitor.ui.components.SettingsToggle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.robok.engine.core.components.compose.preferences.base.PreferenceGroup
import org.robok.engine.core.components.compose.preferences.base.PreferenceLayout


@Composable
fun SettingsTerminalScreen(navController: NavController) {
    PreferenceLayout(label = stringResource(id = strings.terminal), backArrowVisible = true) {
        val context = LocalContext.current
        val isInstalled = isTermuxInstalled() && isTermuxCompatible()
        
        if (isInstalled.not()) {
            rkUtils.toast(strings.install_termux.getString())
        }
        
        PreferenceGroup {
            val result = testExecPermission()
            SettingsToggle(label = stringResource(strings.termux_exec), description = if (result.first.not()) {
                result.second?.message.toString()
            } else {
                "Termux Exec"
            }, default = result.first, isSwitchLocked = true, isEnabled = isInstalled, sideEffect = {
                if (result.first.not()) {
                    val intent = Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                        data = Uri.fromParts("package", context.packageName, null)
                    }
                    context.startActivity(intent)
                }
            })
            
            
            SettingsToggle(label = stringResource(strings.termux_exec_guide), description = stringResource(strings.termux_exec_guide_desc), showSwitch = false, sideEffect = {
                
                val url = if (isTermuxInstalled()) {
                    if (isTermuxCompatible()) {
                        //good
                        "https://github.com/Xed-Editor/Xed-Editor/blob/main/docs/termux/SETUP_TERMUX.md"
                    } else {
                        //google play
                        "https://github.com/Xed-Editor/Xed-Editor/blob/main/docs/termux/GOOGLE_PLAY_TERMUX.md"
                    }
                } else {
                    //not installed
                    "https://github.com/Xed-Editor/Xed-Editor/blob/main/docs/termux/INSTALL_TERMUX.md"
                }
                
                DefaultScope.launch {
                    delay(1000)
                    withContext(Dispatchers.Main) {
                        val intent = Intent(Intent.ACTION_VIEW)
                        intent.setData(Uri.parse(url))
                        intent.setPackage("com.github.android")
                        
                        if (intent.resolveActivity(context.packageManager) != null) {
                            context.startActivity(intent)
                        } else {
                            val builder = CustomTabsIntent.Builder()
                            builder.setShowTitle(true)
                            builder.build().launchUrl(context, Uri.parse(url))
                        }
                    }
                }
            })
            
            SettingsToggle(
                label = stringResource(id = strings.fail_safe),
                description = stringResource(id = strings.failsafe_desc),
                key = PreferencesKeys.FAIL_SAFE,
                default = true
            )
        }
    }
}
