package com.rk.ai.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.rk.activities.main.MainActivity
import com.rk.ai.tab.AiTab
import com.rk.components.SettingsItem
import com.rk.components.XedDialog
import com.rk.components.compose.preferences.base.PreferenceGroup
import com.rk.components.compose.preferences.base.PreferenceLayout
import com.rk.components.compose.preferences.base.PreferenceTemplate
import com.rk.resources.getString
import com.rk.resources.strings
import com.rk.settings.editor.FontRegistry
import java.io.File

@Composable
fun AiSettingsScreen(modifier: Modifier = Modifier) {
    var editing by remember { mutableStateOf<EditTarget?>(null) }

    PreferenceLayout(label = "AI", modifier = modifier) {
        PreferenceGroup(heading = "Connection") {
            SettingsItem(
                label = "API key",
                description =
                    if (AiSettings.apiKey.isBlank()) {
                        "Not set"
                    } else {
                        maskKey(AiSettings.apiKey)
                    },
                showSwitch = false,
                endWidget = { NavigateChevron() },
                sideEffect = { editing = EditTarget.ApiKey },
            )

            SettingsItem(
                label = "Base URL",
                description = AiSettings.baseUrl,
                showSwitch = false,
                endWidget = { NavigateChevron() },
                sideEffect = { editing = EditTarget.BaseUrl },
            )

            SettingsItem(
                label = "Model",
                description = AiSettings.modelId,
                showSwitch = false,
                endWidget = { NavigateChevron() },
                sideEffect = { editing = EditTarget.Model },
            )
        }

        PreferenceGroup(
            heading = "Agent",
            description = "The system prompt is sent at the start of every conversation.",
        ) {
            SettingsItem(
                label = "System prompt",
                description = AiSettings.systemPrompt,
                singleLineDescription = true,
                showSwitch = false,
                endWidget = { NavigateChevron() },
                sideEffect = { editing = EditTarget.SystemPrompt },
            )
        }

        PreferenceGroup(
            heading = "Tool permissions",
            description = "Controls when the agent must ask before running a tool.",
        ) {
            PermissionMode.entries.forEach { mode ->

                val interactionSource = remember { MutableInteractionSource() }
                PreferenceTemplate(
                    modifier =
                        modifier.clickable(indication = ripple(), interactionSource = interactionSource) {
                            AiSettings.permissionMode = mode.name
                        },
                    contentModifier = Modifier.fillMaxHeight(),
                    title = { Text(fontWeight = FontWeight.Bold, text = mode.title()) },
                    description = { Text(text = mode.description()) },
                    enabled = true,
                    applyPaddings = true,
                    startWidget = {
                        RadioButton(selected = AiSettings.currentPermissionMode() == mode, onClick = {
                            AiSettings.permissionMode = mode.name
                        })
                    },
                )

            }
        }
    }

    when (editing) {
        EditTarget.ApiKey ->
            EditTextDialog(
                title = "API key",
                description = "Stored on device. DeepSeek keys start with 'sk-'.",
                initial = AiSettings.apiKey,
                password = true,
                onDismiss = { editing = null },
                onSave = { AiSettings.apiKey = it },
            )

        EditTarget.BaseUrl ->
            EditTextDialog(
                title = "Base URL",
                description = "OpenAI-compatible endpoint, e.g. https://api.deepseek.com",
                initial = AiSettings.baseUrl,
                onDismiss = { editing = null },
                onSave = { AiSettings.baseUrl = it },
            )

        EditTarget.Model ->
            EditTextDialog(
                title = "Model",
                description =
                    "Model id sent to the API. Recommended: deepseek-chat (tools) or deepseek-reasoner.",
                initial = AiSettings.modelId,
                onDismiss = { editing = null },
                onSave = { AiSettings.modelId = it },
            )

        EditTarget.SystemPrompt ->
            EditTextDialog(
                title = "System prompt",
                description = "Prepended to every conversation.",
                initial = AiSettings.systemPrompt,
                singleLine = false,
                onDismiss = { editing = null },
                onSave = { AiSettings.systemPrompt = it },
            )

        null -> Unit
    }
}

@Composable
private fun NavigateChevron() {
    Icon(
        modifier = Modifier.padding(16.dp),
        imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
        contentDescription = null,
    )
}

@Composable
private fun EditTextDialog(
    title: String,
    initial: String,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
    description: String? = null,
    singleLine: Boolean = true,
    password: Boolean = false,
) {
    var text by remember { mutableStateOf(initial) }

    XedDialog(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().padding(20.dp)) {
            Text(text = title, style = MaterialTheme.typography.titleMedium)
            description?.let {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Spacer(Modifier.height(16.dp))
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = singleLine,
                minLines = if (singleLine) 1 else 4,
                visualTransformation =
                    if (password) {
                        PasswordVisualTransformation()
                    } else {
                        VisualTransformation.None
                    },
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
                        onSave(text.trim())
                        onDismiss()
                    }
                ) {
                    Text("Save")
                }
            }
        }
    }
}

private enum class EditTarget {
    ApiKey,
    BaseUrl,
    Model,
    SystemPrompt,
}

private fun maskKey(key: String): String =
    if (key.length <= 4) {
        "•".repeat(key.length.coerceAtLeast(4))
    } else {
        "•".repeat(10) + key.takeLast(4)
    }

private fun PermissionMode.title(): String =
    when (this) {
        PermissionMode.ASK -> "Ask before every tool"
        PermissionMode.ACCEPT_EDITS -> "Auto-approve file edits"
        PermissionMode.PLAN -> "Plan only"
        PermissionMode.YOLO -> "Autonomous"
    }

private fun PermissionMode.description(): String =
    when (this) {
        PermissionMode.ASK -> "ask for writes and shell commands"
        PermissionMode.ACCEPT_EDITS -> "Edits run freely; shell commands still ask"
        PermissionMode.PLAN -> "writes and shell commands are blocked"
        PermissionMode.YOLO -> "Run everything without asking"
    }
