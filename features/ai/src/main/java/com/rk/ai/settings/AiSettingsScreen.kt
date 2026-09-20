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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.rk.ai.api.AiExtensions
import com.rk.ai.provider.AiModel
import com.rk.ai.provider.AiModelCatalog
import com.rk.ai.provider.AiProvider
import com.rk.ai.provider.AiProviderRuntime
import com.rk.ai.tools.AiTool
import com.rk.components.SettingsItem
import com.rk.components.XedDialog
import com.rk.components.compose.preferences.base.PreferenceGroup
import com.rk.components.compose.preferences.base.PreferenceLayout
import com.rk.components.compose.preferences.base.PreferenceTemplate

@Composable
fun AiSettingsScreen(modifier: Modifier = Modifier, onOpenMemory: () -> Unit = {}) {
    var editing by remember { mutableStateOf<EditTarget?>(null) }
    var pickingModel by remember { mutableStateOf(false) }

    val providers by AiExtensions.providers.collectAsState()
    val tools by AiExtensions.tools.collectAsState()
    val provider = providers.firstOrNull { it.id == AiSettings.providerId } ?: providers.firstOrNull()
    val models = provider?.let { AiModelCatalog.modelsFor(it.id) }.orEmpty()

    PreferenceLayout(label = "AI", modifier = modifier) {
        PreferenceGroup(
            heading = "Provider",
            description =
                "Where requests are sent. Extensions can add providers; picking one restores its " +
                    "default endpoint and first model.",
        ) {
            providers.forEach { candidate ->
                ChoiceRow(
                    title = candidate.displayName,
                    description = candidate.defaultBaseUrl,
                    selected = candidate.id == provider?.id,
                    onSelect = { selectProvider(candidate) },
                )
            }
        }

        PreferenceGroup(heading = "Connection") {
            SettingsItem(
                label = "API key",
                description = if (AiSettings.apiKey.isBlank()) "Not set" else maskKey(AiSettings.apiKey),
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
                sideEffect = { pickingModel = true },
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
            heading = "Memory",
            description =
                "Notes the assistant carries between chats. They are added to the system prompt of " +
                    "every run.",
        ) {
            val count = AiMemory.entries.size
            SettingsItem(
                label = "Long-term memory",
                description =
                    when (count) {
                        0 -> "Nothing remembered yet"
                        1 -> "1 saved note"
                        else -> "$count saved notes"
                    },
                showSwitch = false,
                endWidget = { NavigateChevron() },
                sideEffect = { onOpenMemory() },
            )
        }

        PreferenceGroup(
            heading = "Tool permissions",
            description = "Controls when the agent must ask before running a tool.",
        ) {
            PermissionMode.entries.forEach { mode ->
                ChoiceRow(
                    title = mode.title(),
                    description = mode.description(),
                    selected = AiSettings.currentPermissionMode() == mode,
                    onSelect = { AiSettings.permissionMode = mode.name },
                )
            }
        }

        PreferenceGroup(
            heading = "Tools (${tools.size})",
            description = "Every tool the model can call, including the ones extensions registered.",
        ) {
            tools.forEach { tool -> ToolRow(tool) }
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
                description = "Endpoint root for ${provider?.displayName ?: "the active provider"}.",
                initial = AiSettings.baseUrl,
                onDismiss = { editing = null },
                onSave = { AiSettings.baseUrl = it },
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

    if (pickingModel) {
        ModelPickerDialog(
            models = models,
            current = AiSettings.modelId,
            onDismiss = { pickingModel = false },
            onPick = { modelId ->
                AiSettings.modelId = modelId
                pickingModel = false
            },
        )
    }
}

private fun selectProvider(provider: AiProvider) {
    AiSettings.providerId = provider.id
    AiSettings.baseUrl = provider.defaultBaseUrl
    AiModelCatalog.defaultFor(provider)?.let { AiSettings.modelId = it.id }
    AiProviderRuntime.invalidate()
}

@Composable
private fun ChoiceRow(title: String, description: String, selected: Boolean, onSelect: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    PreferenceTemplate(
        modifier = Modifier.clickable(indication = ripple(), interactionSource = interactionSource) { onSelect() },
        contentModifier = Modifier.fillMaxHeight(),
        title = { Text(fontWeight = FontWeight.Bold, text = title) },
        description = { Text(text = description) },
        enabled = true,
        applyPaddings = true,
        startWidget = { RadioButton(selected = selected, onClick = onSelect) },
    )
}

@Composable
private fun ToolRow(tool: AiTool) {
    SettingsItem(
        label = tool.name,
        description = "${tool.kind} · ${if (tool.isDestructive) "asks first" else "runs freely"}",
        singleLineDescription = true,
        showSwitch = false,
        isEnabled = false,
    )
}

@Composable
private fun ModelPickerDialog(
    models: List<AiModel>,
    current: String,
    onDismiss: () -> Unit,
    onPick: (String) -> Unit,
) {
    var custom by remember { mutableStateOf("") }

    XedDialog(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().padding(20.dp).verticalScroll(rememberScrollState())) {
            Text(text = "Model", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(4.dp))
            Text(
                text = "Models registered for this provider. Type an id below to use one that is not listed.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(Modifier.height(12.dp))
            models.forEach { model ->
                SettingsItem(
                    label = model.displayName,
                    description = model.id,
                    singleLineDescription = true,
                    showSwitch = false,
                    startWidget = {
                        RadioButton(selected = model.id == current, onClick = { onPick(model.id) })
                    },
                    sideEffect = { onPick(model.id) },
                )
            }

            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = custom,
                onValueChange = { custom = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text("Custom model id") },
            )

            Spacer(Modifier.height(18.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onDismiss) { Text("Cancel") }
                Spacer(Modifier.width(8.dp))
                Button(onClick = { onPick(custom.trim()) }, enabled = custom.isNotBlank()) { Text("Use") }
            }
        }
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
