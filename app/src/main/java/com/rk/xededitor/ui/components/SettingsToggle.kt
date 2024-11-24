package com.rk.xededitor.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.rk.settings.PreferencesData
import com.rk.settings.PreferencesData.getBoolean
import org.robok.engine.core.components.compose.preferences.base.PreferenceTemplate
import org.robok.engine.core.components.compose.preferences.switch.PreferenceSwitch

@Composable
fun SettingsToggle(
    modifier: Modifier = Modifier,
    label: String,
    description: String,
    @DrawableRes iconRes: Int? = null,
    key: String? = null,
    default: Boolean = false,
    sideEffect: ((state: Boolean) -> Unit)? = null,
    showSwitch:Boolean = true
) {
    var state by remember { mutableStateOf(getBoolean(key, default)) }

    if (showSwitch){
        PreferenceSwitch(checked = state, onCheckedChange = {}, label = label, modifier = modifier, description = description, onClick = {
            state = !state
            PreferencesData.setBoolean(key, state)
            sideEffect?.invoke(state)
        })
    }else{
        val interactionSource = remember { MutableInteractionSource() }
        PreferenceTemplate(
            modifier =
            modifier.clickable(
                enabled = true,
                indication = ripple(),
                interactionSource = interactionSource,
            ) {
                sideEffect?.invoke(false)
            },
            contentModifier = Modifier.fillMaxHeight().padding(vertical = 16.dp).padding(start = 16.dp),
            title = { Text(fontWeight = FontWeight.Bold, text = label) },
            description = { description?.let { Text(text = it) } },
            enabled = true,
            applyPaddings = false,
        )
    }
    
    
    
    
    
}