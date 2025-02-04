package com.rk.xededitor.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.rk.settings.Settings
import com.rk.settings.Settings.getBoolean
import org.robok.engine.core.components.compose.preferences.base.PreferenceTemplate
import org.robok.engine.core.components.compose.preferences.switch.PreferenceSwitch

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SettingsToggle(
    modifier: Modifier = Modifier,
    label: String,
    description: String? = null,
    @DrawableRes iconRes: Int? = null,
    key: String? = null,
    default: Boolean = false,
    ReactiveSideEffect: ((checked: Boolean) -> Boolean)? = null,
    sideEffect: ((checked: Boolean) -> Unit)? = null,
    showSwitch: Boolean = true,
    onLongClick: (() -> Unit)? = null,
    isEnabled: Boolean = true,
    isSwitchLocked: Boolean = false
) {
    var state by remember {
        mutableStateOf(
            if (key == null) {
                default
            } else {
                getBoolean(key, default)
            }
        )
    }
    
    if (showSwitch) {
        PreferenceSwitch(checked = state,
            onLongClick = onLongClick,
            onCheckedChange = {
            if (isSwitchLocked.not()) {
                state = !state
                if (key != null) {
                    Settings.setBoolean(key, state)
                }
                
            }
            if (ReactiveSideEffect != null){
                state = ReactiveSideEffect.invoke(state) == true
            }else{
                sideEffect?.invoke(state)
            }
            
        }, label = label, modifier = modifier, description = description, enabled = isEnabled, onClick = {
            if (isSwitchLocked.not()) {
                state = !state
                if (key != null) {
                    Settings.setBoolean(key, state)
                }
            }
            if (ReactiveSideEffect != null){
                state = ReactiveSideEffect.invoke(state) == true
            }else{
                sideEffect?.invoke(state)
            }
        })
    } else {
        val interactionSource = remember { MutableInteractionSource() }
        PreferenceTemplate(
            modifier = modifier.combinedClickable(
                enabled = isEnabled,
                indication = ripple(),
                interactionSource = interactionSource,
                onLongClick = onLongClick,
                onClick = {sideEffect?.invoke(false)}
            ),
            contentModifier = Modifier
                .fillMaxHeight()
                .padding(vertical = 16.dp)
                .padding(start = 16.dp),
            title = { Text(fontWeight = FontWeight.Bold, text = label) },
            description = { description?.let { Text(text = it) } },
            enabled = true,
            applyPaddings = false,
        )
    }
    
    
}