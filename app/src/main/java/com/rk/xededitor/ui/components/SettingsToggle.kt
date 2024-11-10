package com.rk.xededitor.ui.components

import androidx.annotation.DrawableRes
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
import com.rk.settings.PreferencesData
import com.rk.settings.PreferencesData.getBoolean
import com.rk.settings.PreferencesKeys
import com.rk.xededitor.R
import org.robok.engine.core.components.compose.preferences.category.PreferenceCategory

@Composable
fun SettingsToggle(modifier: Modifier = Modifier, label: String, description: String,@DrawableRes iconRes:Int, key:String, default:Boolean, sideEffect: ((state:Boolean) -> Unit)? = null) {
    var state by remember { mutableStateOf(getBoolean(key, default))}
    PreferenceCategory(
        label = label,
        description = description,
        iconResource = iconRes,
        onNavigate = {
            state = !state
            PreferencesData.setBoolean(key, state)
            sideEffect?.invoke(state)
        },
        endWidget = {
            Switch(
                modifier = Modifier.padding(12.dp).height(24.dp),
                checked = state,
                onCheckedChange = null,
            )
        },
    )
    
    
}