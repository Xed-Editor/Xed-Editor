package com.rk.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.RadioButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.unit.dp
import com.rk.components.compose.preferences.base.DividerColumn
import com.rk.file.FileType
import com.rk.resources.drawables
import com.rk.tabs.CodeEditorState

@Composable
fun SyntaxPanel(onDismissRequest: () -> Unit, editorState: CodeEditorState) {
    XedDialog(onDismissRequest = onDismissRequest) {
        DividerColumn(modifier = Modifier.verticalScroll(rememberScrollState())) {
            FileType.entries
                .toTypedArray()
                .filter { it.textmateScope != null }
                .forEach { fileType ->
                    val scope = fileType.textmateScope!!

                    SettingsToggle(
                        label = fileType.title,
                        description = scope,
                        default = false,
                        sideEffect = { editorState.textmateScope = scope },
                        showSwitch = false,
                        startWidget = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                RadioButton(
                                    selected = editorState.textmateScope == scope,
                                    onClick = { editorState.textmateScope = scope },
                                )

                                Icon(
                                    imageVector = ImageVector.vectorResource(fileType.icon ?: drawables.file),
                                    contentDescription = null,
                                    modifier = Modifier.size(24.dp),
                                )
                            }
                        },
                    )
                }
        }
    }
}
