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

private var lastClick = 0L

@Composable
fun SettingsTerminalScreen(navController: NavController) {
    PreferenceLayout(label = stringResource(id = strings.terminal), backArrowVisible = true) {
        val context = LocalContext.current
        val isInstalledNot = isTermuxInstalled().not() || isTermuxCompatible().not()
        
        if (isInstalledNot) {
            rkUtils.toast("Install Termux from F-Droid")
        }
        
        PreferenceGroup {
            
            val result = testExecPermission()
            SettingsToggle(label = "Termux Exec permission", description = "Permission >> Additional Permission >> Termux Exec ${
                if (result.first.not()) {
                    "\n${result.second?.message}"
                } else {
                    ""
                }
            }", default = result.first, isSwitchLocked = true, isEnabled = isInstalledNot.not(), sideEffect = {
                if (result.first.not()) {
                    val intent = Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                        data = Uri.fromParts("package", context.packageName, null)
                    }
                    context.startActivity(intent)
                }
            })
            
            
            SettingsToggle(label = "Termux Exec", description = "Documentation for Enabling Termux Exec", showSwitch = false, sideEffect = {
                
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
