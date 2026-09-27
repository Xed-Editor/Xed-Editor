package com.rk.ai.settings

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.rk.components.ResetButton
import com.rk.components.SettingsItem
import com.rk.components.SingleInputDialog
import com.rk.components.compose.preferences.base.PreferenceGroup
import com.rk.components.compose.preferences.base.PreferenceLayout
import com.rk.resources.strings

@Composable
fun AiMemoryScreen(modifier: Modifier = Modifier) {
    var editing by remember { mutableStateOf<MemoryEdit?>(null) }
    val memories = AiMemory.entries

    PreferenceLayout(
        label = stringResource(strings.ai_memory),
        modifier = modifier,
        actions = { ResetButton { AiMemory.clear() } },
    ) {
        PreferenceGroup(
            heading = stringResource(strings.ai_long_term_memory),
            description = stringResource(strings.ai_memory_screen_description),
        ) {
            if (memories.isEmpty()) {
                SettingsItem(
                    label = stringResource(strings.ai_memory_empty),
                    showSwitch = false,
                    isEnabled = false,
                )
            } else {
                memories.forEachIndexed { index, entry ->
                    SettingsItem(
                        label = stringResource(strings.ai_memory_number, index + 1),
                        description = entry,
                        showSwitch = false,
                        onClick = { editing = MemoryEdit(index = index + 1) },
                        endWidget = {
                            MemoryActions(
                                position = index + 1,
                                onEdit = { editing = MemoryEdit(index = index + 1) },
                                onDelete = { AiMemory.removeAt(index + 1) },
                            )
                        },
                    )
                }
            }

            SettingsItem(
                label = stringResource(strings.ai_add_memory),
                showSwitch = false,
                startWidget = {
                    Icon(
                        imageVector = Icons.Filled.Add,
                        contentDescription = null,
                        modifier = Modifier.padding(start = 16.dp).size(24.dp),
                    )
                },
                sideEffect = { editing = MemoryEdit(index = null) },
            )
        }
    }

    editing?.let { edit ->
        val editIndex = edit.index
        val initialText = editIndex?.let { memories.getOrNull(it - 1) }.orEmpty()
        var text by remember { mutableStateOf(initialText) }
        val isNew = editIndex == null

        SingleInputDialog(
            title = if (isNew) stringResource(strings.ai_add_memory) else stringResource(strings.ai_edit_memory_title),
            inputLabel = stringResource(strings.ai_memory),
            inputValue = text,
            onInputValueChange = { text = it },
            singleLineMode = false,
            confirmEnabled = text.isNotBlank(),
            onConfirm = {
                if (editIndex == null) {
                    AiMemory.add(text.trim())
                } else {
                    AiMemory.updateAt(editIndex, text.trim())
                }
                editing = null
            },
            onDismiss = { editing = null },
        )
    }
}

@Composable
private fun MemoryActions(position: Int, onEdit: () -> Unit, onDelete: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onEdit) {
            Icon(
                imageVector = Icons.Rounded.Edit,
                contentDescription = stringResource(strings.ai_edit_memory, position),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        IconButton(onClick = onDelete) {
            Icon(
                imageVector = Icons.Outlined.Delete,
                contentDescription = stringResource(strings.ai_forget_memory, position),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private data class MemoryEdit(val index: Int?)
