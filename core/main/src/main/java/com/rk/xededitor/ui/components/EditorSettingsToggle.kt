package com.rk.xededitor.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.lifecycle.lifecycleScope
import com.rk.DefaultScope
import com.rk.xededitor.ui.activities.main.EditorTab
import com.rk.xededitor.ui.activities.main.MainActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Composable
fun EditorSettingsToggle(
    modifier: Modifier = Modifier,
    label: String,
    description: String? = null,
    @DrawableRes iconRes: Int? = null,
    default: Boolean,
    reactiveSideEffect: ((checked: Boolean) -> Boolean)? = null,
    sideEffect: ((checked: Boolean) -> Unit)? = null,
    showSwitch: Boolean = true,
    onLongClick: (() -> Unit)? = null,
    isEnabled: Boolean = true,
    isSwitchLocked: Boolean = false,
) {
    SettingsToggle(
        modifier = modifier,
        label = label,
        description = description,
        iconRes = iconRes,
        default = default,
        reactiveSideEffect = reactiveSideEffect,
        showSwitch = showSwitch,
        onLongClick = onLongClick,
        isEnabled = isEnabled,
        isSwitchLocked = isSwitchLocked,
        sideEffect = {
            DefaultScope.launch(Dispatchers.Main) {
                sideEffect?.invoke(it)
                if (showSwitch){
                    MainActivity.instance?.apply {
                        viewModel.tabs.forEach{
                            if (it is EditorTab){
                                it.editorState.editor?.applySettings()
                            }
                        }
                    }
                }
            }
        },
    )
}
