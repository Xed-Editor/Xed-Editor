package com.rk.xededitor.ui.screens.settings.editor

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.rk.components.compose.preferences.base.PreferenceGroup
import com.rk.components.compose.preferences.base.PreferenceLayout
import com.rk.libcommons.editor.lspRegistry
import com.rk.resources.strings
import com.rk.settings.Preference
import com.rk.xededitor.ui.components.SettingsToggle

@Composable
fun LspSettings(modifier: Modifier = Modifier) {
    PreferenceLayout(label = stringResource(strings.lsp_settings)) {
        if (lspRegistry.isNotEmpty()){
            PreferenceGroup {
                lspRegistry.values.forEach { server ->
                    SettingsToggle(label = server.languageName, default = Preference.getBoolean("lsp_${server.id}",false), showSwitch = true, sideEffect = {
                        Preference.setBoolean("lsp_${server.id}",it)
                    })
                }
            }
        }else{
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center){
                Text(stringResource(strings.no_language_server))
            }
        }


    }
}