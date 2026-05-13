package com.rk.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.rk.DefaultScope
import com.rk.settings.editor.refreshEditorSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Composable
fun EditorSettingsToggle(
    modifier: Modifier = Modifier,
    label: String,
    description: String? = null,
    @DrawableRes iconRes: Int? = null,
    default: Boolean = false,
    reactiveSideEffect: ((checked: Boolean) -> Boolean)? = null,
    sideEffect: ((checked: Boolean) -> Unit)? = null,
    showSwitch: Boolean = true,
    onLongClick: (() -> Unit)? = null,
    isEnabled: Boolean = true,
    isSwitchLocked: Boolean = false,
    state: MutableState<Boolean> = remember { mutableStateOf(default) },
) {
    SettingsToggle(
        modifier = modifier,
        label = label,
        state = state,
        description = description,
        startWidget =
            iconRes?.let {
                {
                    Icon(
                        modifier = Modifier.padding(start = 16.dp),
                        painter = painterResource(id = it),
                        contentDescription = null,
                    )
                }
            },
        default = default,
        reactiveSideEffect = reactiveSideEffect,
        showSwitch = showSwitch,
        onLongClick = onLongClick,
        isEnabled = isEnabled,
        isSwitchLocked = isSwitchLocked,
        sideEffect = {
            DefaultScope.launch(Dispatchers.Main) {
                sideEffect?.invoke(it)
                if (showSwitch) refreshEditorSettings()
            }
        },
    )
}
