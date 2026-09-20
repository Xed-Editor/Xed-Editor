package com.rk.ai.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
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
import com.rk.resources.getString
import com.rk.resources.strings

@Composable
fun AiSettingsScreen(modifier: Modifier = Modifier, onOpenMemory: () -> Unit = {}) {
    var editing by remember { mutableStateOf<EditTarget?>(null) }
    var pickingModel by remember { mutableStateOf(false) }
    var providerMenuOpen by remember { mutableStateOf(false) }

    val providers by AiExtensions.providers.collectAsState()
    val tools by AiExtensions.tools.collectAsState()
    val provider = providers.firstOrNull { it.id == AiSettings.providerId } ?: providers.firstOrNull()
    val models = provider?.let { AiModelCatalog.modelsFor(it.id) }.orEmpty()
    val apiKey = provider?.let { AiSettings.apiKey(it.id) }.orEmpty()

    PreferenceLayout(label = stringResource(strings.ai_feature_label), modifier = modifier) {
        PreferenceGroup(heading = stringResource(strings.ai_provider)) {
            Box {
                SettingsItem(
                    label = stringResource(strings.ai_provider),
                    description = provider?.displayName,
                    singleLineDescription = true,
                    showSwitch = false,
                    startWidget = { provider?.let { ProviderIcon(it) } },
                    endWidget = {
                        Icon(
                            imageVector = Icons.Filled.KeyboardArrowDown,
                            contentDescription = null,
                            modifier = Modifier.padding(end = 16.dp),
                        )
                    },
                    sideEffect = { providerMenuOpen = true },
                )

                DropdownMenu(
                    expanded = providerMenuOpen,
                    onDismissRequest = { providerMenuOpen = false },
                    shape = RoundedCornerShape(16.dp),
                ) {
                    providers.forEach { candidate ->
                        DropdownMenuItem(
                            text = { Text(candidate.displayName) },
                            leadingIcon = { ProviderIcon(candidate) },
                            trailingIcon = {
                                if (candidate.id == provider?.id) {
                                    Icon(Icons.Filled.Check, contentDescription = null)
                                }
                            },
                            modifier =
                                Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    .clip(RoundedCornerShape(12.dp)),
                            onClick = {
                                selectProvider(candidate)
                                providerMenuOpen = false
                            },
                        )
                    }
                }
            }
        }

        PreferenceGroup(heading = stringResource(strings.ai_connection)) {
            if (provider?.requiresApiKey == true) {
                SettingsItem(
                    label = stringResource(strings.ai_api_key),
                    description = if (apiKey.isBlank()) stringResource(strings.ai_not_set) else maskKey(apiKey),
                    showSwitch = false,
                    endWidget = { NavigateChevron() },
                    sideEffect = { editing = EditTarget.ApiKey },
                )
            }

            SettingsItem(
                label = stringResource(strings.ai_model),
                description = AiSettings.modelId,
                showSwitch = false,
                endWidget = { NavigateChevron() },
                sideEffect = { pickingModel = true },
            )
        }

        PreferenceGroup(heading = stringResource(strings.ai_agent)) {
            SettingsItem(
                label = stringResource(strings.ai_system_prompt),
                description = AiSettings.systemPrompt,
                singleLineDescription = true,
                showSwitch = false,
                endWidget = { NavigateChevron() },
                sideEffect = { editing = EditTarget.SystemPrompt },
            )
        }

        PreferenceGroup(heading = stringResource(strings.ai_memory)) {
            val count = AiMemory.entries.size
            SettingsItem(
                label = stringResource(strings.ai_long_term_memory),
                description =
                    when (count) {
                        0 -> stringResource(strings.ai_memory_empty)
                        1 -> stringResource(strings.ai_memory_one_note)
                        else -> stringResource(strings.ai_memory_notes, count)
                    },
                showSwitch = false,
                endWidget = { NavigateChevron() },
                sideEffect = { onOpenMemory() },
            )
        }

        PreferenceGroup(heading = stringResource(strings.ai_tool_permissions)) {
            PermissionMode.entries.forEach { mode ->
                ChoiceRow(
                    title = mode.title(),
                    selected = AiSettings.currentPermissionMode() == mode,
                    onSelect = { AiSettings.permissionMode = mode.name },
                )
            }
        }

        PreferenceGroup(heading = stringResource(strings.ai_tools_count, tools.size)) {
            tools.forEach { tool -> ToolRow(tool) }
        }
    }

    when (editing) {
        EditTarget.ApiKey ->
            EditTextDialog(
                title = stringResource(strings.ai_api_key),
                description = stringResource(strings.ai_api_key_hint),
                initial = apiKey,
                password = true,
                onDismiss = { editing = null },
                onSave = { key -> provider?.let { AiSettings.setApiKey(it.id, key) } },
            )

        EditTarget.SystemPrompt ->
            EditTextDialog(
                title = stringResource(strings.ai_system_prompt),
                description = stringResource(strings.ai_system_prompt_hint),
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
    AiModelCatalog.defaultFor(provider)?.let { AiSettings.modelId = it.id }
    AiProviderRuntime.invalidate()
}

@Composable
private fun ProviderIcon(provider: AiProvider) {
    Icon(
        imageVector = provider.icon,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 16.dp).size(24.dp),
    )
}

@Composable
private fun ChoiceRow(title: String, selected: Boolean, onSelect: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    PreferenceTemplate(
        modifier = Modifier.clickable(indication = ripple(), interactionSource = interactionSource) { onSelect() },
        title = { Text(fontWeight = FontWeight.Bold, text = title) },
        enabled = true,
        applyPaddings = false,
        startWidget = { RadioButton(selected = selected, onClick = onSelect) },
    )
}

@Composable
private fun ToolRow(tool: AiTool) {
    SettingsItem(
        label = tool.name,
        description =
            stringResource(
                strings.ai_tool_row_description,
                tool.kind,
                if (tool.isDestructive) {
                    stringResource(strings.ai_tool_asks_first)
                } else {
                    stringResource(strings.ai_tool_runs_freely)
                },
            ),
        singleLineDescription = true,
        state = remember(tool.name) { mutableStateOf(AiSettings.isToolEnabled(tool.name)) },
        sideEffect = { AiSettings.setToolEnabled(tool.name, it) },
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
            Text(text = stringResource(strings.ai_model), style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(4.dp))
            Text(
                text = stringResource(strings.ai_model_picker_hint),
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
                label = { Text(stringResource(strings.ai_custom_model_id)) },
            )

            Spacer(Modifier.height(18.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onDismiss) { Text(stringResource(strings.cancel)) }
                Spacer(Modifier.width(8.dp))
                Button(onClick = { onPick(custom.trim()) }, enabled = custom.isNotBlank()) {
                    Text(stringResource(strings.ai_use))
                }
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
        PermissionMode.ASK -> strings.ai_mode_ask.getString()
        PermissionMode.ACCEPT_EDITS -> strings.ai_mode_accept_edits.getString()
        PermissionMode.PLAN -> strings.ai_mode_plan.getString()
        PermissionMode.YOLO -> strings.ai_mode_autonomous.getString()
    }
