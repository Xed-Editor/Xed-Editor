package com.rk.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.rk.components.compose.preferences.base.PreferenceTemplate
import com.rk.components.compose.preferences.switch.PreferenceSwitch

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SettingsToggle(
    modifier: Modifier = Modifier,
    label: String,
    default: Boolean,
    state: MutableState<Boolean> = remember { mutableStateOf(default) },
    description: String? = null,
    @DrawableRes iconRes: Int? = null,
    reactiveSideEffect: ((checked: Boolean) -> Boolean)? = null,
    sideEffect: ((checked: Boolean) -> Unit)? = null,
    showSwitch: Boolean = true,
    onLongClick: (() -> Unit)? = null,
    isEnabled: Boolean = true,
    isSwitchLocked: Boolean = false,
    startWidget: (@Composable () -> Unit)? = null,
    endWidget: (@Composable () -> Unit)? = null,
) {

    if (showSwitch && endWidget != null){
        throw IllegalStateException("endWidget with show switch")
    }

    if (showSwitch) {
        PreferenceSwitch(checked = state.value,
            onLongClick = onLongClick,
            onCheckedChange = {
                if (isSwitchLocked.not()) {
                    state.value = !state.value
                }
                if (reactiveSideEffect != null) {
                    state.value = reactiveSideEffect.invoke(state.value) == true
                } else {
                    sideEffect?.invoke(state.value)
                }

            },
            label = label,
            modifier = modifier,
            description = description,
            enabled = isEnabled,
            onClick = {
                if (isSwitchLocked.not()) {
                    state.value = !state.value
                }
                if (reactiveSideEffect != null) {
                    state.value = reactiveSideEffect.invoke(state.value) == true
                } else {
                    sideEffect?.invoke(state.value)
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
                onClick = { sideEffect?.invoke(false) }
            ),
            contentModifier = Modifier
                .fillMaxHeight()
                .padding(vertical = 16.dp)
                .padding(start = 16.dp),
            title = { Text(fontWeight = FontWeight.Bold, text = label) },
            description = { description?.let { Text(text = it) } },
            enabled = true,
            applyPaddings = false,
            endWidget = endWidget,
            startWidget = startWidget
        )
    }


}