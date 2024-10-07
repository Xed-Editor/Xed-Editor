package com.rk.xededitor.ui.screens.settings.editor

import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Switch
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController

import com.rk.xededitor.R
import com.rk.xededitor.settings.Keys
import com.rk.xededitor.settings.SettingsData

import org.robok.engine.core.components.compose.preferences.base.PreferenceLayout
import org.robok.engine.core.components.compose.preferences.category.PreferenceCategory

@Composable
fun SettingsEditorScreen() {
    PreferenceLayout(
        label = stringResource(id = R.string.editor),
        backArrowVisible = true,
    ) {

        var smoothTabs by remember { mutableStateOf(SettingsData.getBoolean(Keys.VIEWPAGER_SMOOTH_SCROLL,true)) }

        PreferenceCategory(
            label = stringResource(id =R.string.smooth_tabs),
            description = stringResource(id = R.string.smooth_tab_desc),
            iconResource = R.drawable.animation,
            onNavigate = {
                smoothTabs = !smoothTabs
                SettingsData.setBoolean(Keys.VIEWPAGER_SMOOTH_SCROLL,smoothTabs)
            },
            endWidget = {
                Switch(
                    modifier = Modifier
                        .padding(12.dp)
                        .height(24.dp),
                    checked = smoothTabs,
                    onCheckedChange = null
                )
            }
        )
        
    }
}