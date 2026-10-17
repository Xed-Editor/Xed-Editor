package com.rk.ai.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.unit.dp
import com.rk.ai.api.AiExtensions
import com.rk.ai.provider.AiModelCatalog
import com.rk.ai.provider.AiProvider
import com.rk.ai.provider.AiProviderRuntime
import com.rk.ai.tools.AiTool
import com.rk.components.NextScreenCard
import com.rk.components.PreferenceList
import com.rk.components.SettingsItem
import com.rk.components.SingleInputDialog
import com.rk.components.compose.preferences.base.PreferenceGroup
import com.rk.components.compose.preferences.base.PreferenceLayout
import com.rk.components.compose.preferences.base.PreferenceTemplate
import com.rk.resources.getString
import com.rk.resources.strings

@Composable
fun AiSettingsScreen(modifier: Modifier = Modifier, onOpenMemory: () -> Unit = {}) {
    var editing by remember { mutableStateOf<EditTarget?>(null) }
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
                                Modifier.padding(horizontal = 6.dp, vertical = 2.dp).clip(RoundedCornerShape(12.dp)),
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
                    sideEffect = { editing = EditTarget.ApiKey },
                )
            }

            val modelItems = models.map { it.id to it.displayName }
            PreferenceList(
                label = stringResource(strings.ai_model),
                description = AiSettings.modelId,
                items = modelItems,
                selectedItem = AiSettings.modelId,
                showIds = true,
                onItemSelected = { modelId -> AiSettings.modelId = modelId },
                customInputLabel = stringResource(strings.ai_custom_model_id),
                customInputValue = if (models.any { it.id == AiSettings.modelId }) "" else AiSettings.modelId,
            )
        }

        PreferenceGroup(heading = stringResource(strings.ai_agent)) {
            SettingsItem(
                label = stringResource(strings.ai_system_prompt),
                description = AiSettings.systemPrompt,
                singleLineDescription = true,
                showSwitch = false,
                sideEffect = { editing = EditTarget.SystemPrompt },
            )
        }

        PreferenceGroup(heading = stringResource(strings.ai_memory)) {
            val count = AiMemory.entries.size
            NextScreenCard(
                label = stringResource(strings.ai_long_term_memory),
                description =
                    when (count) {
                        0 -> stringResource(strings.ai_memory_empty)
                        1 -> stringResource(strings.ai_memory_one_note)
                        else -> stringResource(strings.ai_memory_notes, count)
                    },
                onClick = { onOpenMemory() },
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
        EditTarget.ApiKey -> {
            var keyText by remember { mutableStateOf(apiKey) }
            AlertDialog(
                onDismissRequest = { editing = null },
                title = { Text(stringResource(strings.ai_api_key)) },
                text = {
                    Column {
                        OutlinedTextField(
                            value = keyText,
                            onValueChange = { keyText = it },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            visualTransformation = PasswordVisualTransformation(),
                        )
                    }
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            provider?.let { AiSettings.setApiKey(it.id, keyText.trim()) }
                            editing = null
                        }
                    ) {
                        Text(stringResource(strings.apply))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { editing = null }) {
                        Text(stringResource(strings.cancel))
                    }
                },
            )
        }

        EditTarget.SystemPrompt -> {
            var promptText by remember { mutableStateOf(AiSettings.systemPrompt) }
            SingleInputDialog(
                title = stringResource(strings.ai_system_prompt),
                inputLabel = stringResource(strings.ai_system_prompt),
                inputValue = promptText,
                onInputValueChange = { promptText = it },
                onConfirm = { AiSettings.systemPrompt = promptText.trim() },
                onDismiss = { editing = null },
                singleLineMode = false,
                message = {
                    Text(
                        text = stringResource(strings.ai_system_prompt_hint),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                },
            )
        }

        null -> Unit
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
