package com.rk.xededitor.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.lifecycle.lifecycleScope
import com.rk.libcommons.DefaultScope
import com.rk.xededitor.MainActivity.MainActivity
import com.rk.xededitor.MainActivity.tabs.editor.EditorFragment
import com.rk.xededitor.MainActivity.tabs.editor.editorFragmentsForEach
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

fun updateEditorSettings() {
    MainActivity.withContext {
        editorFragmentsForEach {
            lifecycleScope.launch { it.editor?.applySettings() }
        }
    }
}

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
                    updateEditorSettings()
                }
            }
        },
    )
}
