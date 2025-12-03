package com.rk.components

import android.view.View
import androidx.annotation.DrawableRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.rk.DefaultScope
import com.rk.activities.main.MainActivity
import com.rk.settings.Settings
import com.rk.tabs.EditorTab
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
                if (showSwitch) {
                    MainActivity.instance?.apply {
                        viewModel.tabs.forEach {
                            if (it is EditorTab) {
                                it.editorState.editor.get()?.applySettings()
                                it.editorState.arrowKeys.get()?.visibility =
                                    if (Settings.show_extra_keys) {
                                        View.VISIBLE
                                    } else {
                                        View.GONE
                                    }
                            }
                        }
                    }
                }
            }
        },
    )
}
