package com.rk.components

import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.RadioButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.rk.components.compose.preferences.base.DividerColumn
import com.rk.editor.textmateSources
import com.rk.tabs.CodeEditorState

@Composable
fun SyntaxPanel(onDismissRequest:()-> Unit,editorState: CodeEditorState) {
    XedDialog(onDismissRequest = onDismissRequest) {
        DividerColumn(modifier = Modifier.verticalScroll(rememberScrollState())) {
            textmateSources.values.toSet().forEach { scope ->
                SettingsToggle(
                    modifier = Modifier,
                    label = scope.removePrefix("source."),
                    default = false,
                    sideEffect = {
                        editorState.textmateScope = scope
                    },
                    showSwitch = false,
                    startWidget = {
                        RadioButton(
                            selected = editorState.textmateScope == scope,
                            onClick = {
                                editorState.textmateScope = scope
                            }
                        )
                    }
                )
            }
        }
    }
}