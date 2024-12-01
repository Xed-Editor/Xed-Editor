package com.rk.xededitor.ui.screens.settings.terminal

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavController
import com.jaredrummler.ktsh.Shell
import com.rk.karbon_exec.isTermuxCompatible
import com.rk.karbon_exec.isTermuxInstalled
import com.rk.karbon_exec.testExecPermission
import com.rk.resources.strings
import com.rk.settings.PreferencesData.getBoolean
import com.rk.settings.PreferencesKeys
import com.rk.xededitor.rkUtils
import com.rk.xededitor.ui.components.SettingsToggle
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import org.robok.engine.core.components.compose.preferences.base.PreferenceGroup
import org.robok.engine.core.components.compose.preferences.base.PreferenceLayout
import java.io.File

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
fun SettingsTerminalScreen(navController: NavController) {
    PreferenceLayout(label = stringResource(id = strings.terminal), backArrowVisible = true) {
        val context = LocalContext.current
        
        PreferenceGroup {
            SettingsToggle(
                label = stringResource(id = strings.fail_safe),
                description = stringResource(id = strings.failsafe_desc),
                key = PreferencesKeys.FAIL_SAFE,
                default = false
            )
            
            SettingsToggle(label = "Termux Exec", description = "Enable Termux Exec", isSwitchLocked = true,
                
                default = isTermuxInstalled() && isTermuxCompatible() && testExecPermission().also {
                    it.second?.let { e ->
                        rkUtils.toast(e.message)
                    }
                }.first,
                
                sideEffect = {
                    //only continue if command threw an error
                    testExecPermission().second?.let {
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
                })
        }
    }
}
