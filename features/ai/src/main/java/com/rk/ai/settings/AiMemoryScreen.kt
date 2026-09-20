package com.rk.ai.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.rk.components.SettingsItem
import com.rk.components.XedDialog
import com.rk.components.compose.preferences.base.PreferenceGroup
import com.rk.components.compose.preferences.base.PreferenceLayout

@Composable
fun AiMemoryScreen(modifier: Modifier = Modifier) {
    var editing by remember { mutableStateOf<MemoryEdit?>(null) }
    val memories = AiMemory.entries

    PreferenceLayout(label = "Memory", modifier = modifier, actions = {
        IconButton(onClick = {
            AiMemory.clear()
        }) {
            Icon(imageVector = Icons.Outlined.Delete,null)
        }
    }) {
        PreferenceGroup(
            heading = "Long-term memory",
            description =
                "Notes the assistant carries into every chat. They are added to the system prompt, " +
                    "so keep them short and factual.",
        ) {
            SettingsItem(
                label = "Add a memory",
                description = "Write something the assistant should always know.",
                showSwitch = false,
                startWidget = {
                    Icon(
                        imageVector = Icons.Filled.Add,
                        contentDescription = null,
                        modifier = Modifier.padding(start = 16.dp),
                    )
                },
                sideEffect = { editing = MemoryEdit(index = null) },
            )

            if (memories.isEmpty()) {
                SettingsItem(
                    label = "Nothing remembered yet",
                    description = "The assistant can also add notes itself with save_memory.",
                    showSwitch = false,
                    isEnabled = false,
                )
            }
        }

        if (memories.isNotEmpty()) {
            PreferenceGroup(
                heading = "Saved notes",
                description =
                    "Tap a note to edit it. The assistant sees the list in the order shown here.",
            ) {
                memories.forEachIndexed { index, entry ->
                    SettingsItem(
                        label = "Memory ${index + 1}",
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
        }
    }

    editing?.let { edit ->
        MemoryDialog(
            initial = edit.index?.let { memories.getOrNull(it - 1) }.orEmpty(),
            isNew = edit.index == null,
            onDismiss = { editing = null },
            onSave = { text ->
                if (edit.index == null) AiMemory.add(text) else AiMemory.updateAt(edit.index, text)
            },
        )
    }
}

@Composable
private fun MemoryActions(position: Int, onEdit: () -> Unit, onDelete: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onEdit) {
            Icon(
                imageVector = Icons.Filled.Edit,
                contentDescription = "Edit memory $position",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        IconButton(onClick = onDelete) {
            Icon(
                imageVector = Icons.Outlined.Delete,
                contentDescription = "Forget memory $position",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun MemoryDialog(
    initial: String,
    isNew: Boolean,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
) {
    var text by remember { mutableStateOf(initial) }

    XedDialog(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().padding(20.dp)) {
            Text(
                text = if (isNew) "Add a memory" else "Edit memory",
                style = MaterialTheme.typography.titleMedium,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "The assistant will see this in every future chat.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(Modifier.height(16.dp))
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                modifier = Modifier.fillMaxWidth(),
                minLines = 3,
            )

            Spacer(Modifier.height(18.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                TextButton(onClick = onDismiss) { Text("Cancel") }
                Spacer(Modifier.width(8.dp))
                Button(
                    onClick = {
                        onSave(text)
                        onDismiss()
                    },
                    enabled = text.isNotBlank(),
                ) {
                    Text("Save")
                }
            }
        }
    }
}

private data class MemoryEdit(val index: Int?)
