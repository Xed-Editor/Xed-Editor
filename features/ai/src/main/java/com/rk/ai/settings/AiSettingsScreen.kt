package com.rk.ai.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.rk.ai.api.AiExtensions
import com.rk.ai.icons.Robot
import com.rk.ai.provider.AiModelCatalog
import com.rk.ai.provider.AiProvider
import com.rk.ai.provider.AiProviderRuntime
import com.rk.ai.provider.BuiltinProviders
import com.rk.ai.provider.CustomProviderProblem
import com.rk.ai.provider.CustomProviderSpec
import com.rk.ai.provider.CustomProviders
import com.rk.ai.tools.AiTool
import com.rk.components.NextScreenCard
import com.rk.components.PreferenceList
import com.rk.components.PreferenceListItem
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
    var editingProvider by remember { mutableStateOf<CustomProviderSpec?>(null) }
    var deletingProvider by remember { mutableStateOf<CustomProviderSpec?>(null) }

    val providers by AiExtensions.providers.collectAsState()
    val tools by AiExtensions.tools.collectAsState()
    val customProviders = CustomProviders.all()
    val provider = providers.firstOrNull { it.id == AiSettings.providerId } ?: providers.firstOrNull()
    val models = provider?.let { AiModelCatalog.modelsFor(it.id) }.orEmpty()
    val apiKey = provider?.let { AiSettings.apiKey(it.id) }.orEmpty()
    val modelId = provider?.let { AiSettings.modelId(it.id) }.orEmpty()

    PreferenceLayout(label = stringResource(strings.ai_feature_label), modifier = modifier) {
        PreferenceGroup(heading = stringResource(strings.ai_provider)) {
            Box {
                SettingsItem(
                    label = stringResource(strings.ai_provider),
                    description = provider?.displayName,
                    singleLineDescription = true,
                    showSwitch = false,
                    startWidget = { provider?.let { ProviderIcon(it.icon) } },
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
                            leadingIcon = { ProviderIcon(candidate.icon) },
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

        PreferenceGroup(heading = stringResource(strings.ai_custom_providers)) {
            customProviders.forEach { spec ->
                SettingsItem(
                    label = spec.name,
                    description = spec.baseUrl,
                    singleLineDescription = true,
                    showSwitch = false,
                    startWidget = { ProviderIcon(Robot) },
                    endWidget = {
                        IconButton(onClick = { deletingProvider = spec }) {
                            Icon(
                                imageVector = Icons.Filled.Delete,
                                contentDescription = stringResource(strings.ai_delete_provider),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(end = 8.dp),
                            )
                        }
                    },
                    sideEffect = { editingProvider = spec },
                )
            }

            SettingsItem(
                label = stringResource(strings.ai_add_provider),
                description = stringResource(strings.ai_add_provider_description),
                singleLineDescription = true,
                showSwitch = false,
                startWidget = {
                    Icon(
                        imageVector = Icons.Filled.Add,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 16.dp).size(24.dp),
                    )
                },
                sideEffect = { editingProvider = CustomProviders.newSpec() },
            )
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

            val modelItems = models.map { PreferenceListItem(it.id, it.displayName) }
            PreferenceList(
                label = stringResource(strings.ai_model),
                description = modelId.ifBlank { models.firstOrNull()?.id ?: stringResource(strings.ai_not_set) },
                items = modelItems,
                selectedItem = modelId,
                // Only worth a second line when the label differs from the id it stands for.
                showIds = models.any { it.displayName != it.id },
                onItemSelected = { selected -> provider?.let { AiSettings.setModelId(it.id, selected) } },
                message = { Text(stringResource(strings.ai_model_picker_hint)) },
                customInputLabel = stringResource(strings.ai_custom_model_id),
                customInputValue = if (models.any { it.id == modelId }) "" else modelId,
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
            var keyText by remember(provider?.id) { mutableStateOf(apiKey) }
            AlertDialog(
                onDismissRequest = { editing = null },
                title = { Text(stringResource(strings.ai_api_key)) },
                text = {
                    Column {
                        Text(
                            text = provider?.displayName.orEmpty(),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(12.dp))
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
                            AiProviderRuntime.invalidate()
                            editing = null
                        }
                    ) {
                        Text(stringResource(strings.apply))
                    }
                },
                dismissButton = { TextButton(onClick = { editing = null }) { Text(stringResource(strings.cancel)) } },
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

    editingProvider?.let { initial ->
        val existing = CustomProviders.find(initial.id) != null
        CustomProviderDialog(
            initial = initial,
            exists = existing,
            onDismiss = { editingProvider = null },
            onSave = { spec ->
                CustomProviders.save(spec)
                if (!existing) {
                    AiSettings.providerId = spec.id
                    spec.modelIds().firstOrNull()?.let { AiSettings.setModelId(spec.id, it) }
                }
                editingProvider = null
            },
        )
    }

    deletingProvider?.let { spec ->
        AlertDialog(
            onDismissRequest = { deletingProvider = null },
            title = { Text(stringResource(strings.ai_delete_provider)) },
            text = { Text(stringResource(strings.ai_delete_provider_message, spec.name)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        CustomProviders.delete(spec.id)
                        AiSettings.clearApiKey(spec.id)
                        AiSettings.setModelId(spec.id, "")
                        if (AiSettings.providerId == spec.id) {
                            AiSettings.providerId = BuiltinProviders.default().id
                        }
                        deletingProvider = null
                    }
                ) {
                    Text(stringResource(strings.delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { deletingProvider = null }) { Text(stringResource(strings.cancel)) }
            },
        )
    }
}

/**
 * Collects the endpoint of a provider the user adds. Save is blocked until the record is usable, and the error appears
 * next to the field it belongs to.
 */
@Composable
private fun CustomProviderDialog(
    initial: CustomProviderSpec,
    exists: Boolean,
    onDismiss: () -> Unit,
    onSave: (CustomProviderSpec) -> Unit,
) {
    var name by remember(initial.id) { mutableStateOf(initial.name) }
    var baseUrl by remember(initial.id) { mutableStateOf(initial.baseUrl) }
    var requestPath by remember(initial.id) { mutableStateOf(initial.requestPath) }
    var modelsText by remember(initial.id) { mutableStateOf(initial.modelIdsText()) }
    var requiresApiKey by remember(initial.id) { mutableStateOf(initial.requiresApiKey) }
    var showErrors by remember(initial.id) { mutableStateOf(false) }

    val candidate =
        CustomProviderSpec(
            id = initial.id,
            name = name.trim(),
            baseUrl = baseUrl.trim(),
            requestPath = requestPath.trim(),
            requiresApiKey = requiresApiKey,
            models = CustomProviderSpec.parseModelIds(modelsText),
        )
    val problem = candidate.problem
    val nameError = showErrors && problem == CustomProviderProblem.MISSING_NAME
    val baseUrlError = showErrors && problem == CustomProviderProblem.INVALID_BASE_URL

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(if (exists) strings.ai_edit_provider else strings.ai_add_provider)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text(
                    text = stringResource(strings.ai_provider_dialog_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(12.dp))

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text(stringResource(strings.ai_provider_name)) },
                    isError = nameError,
                    supportingText =
                        if (nameError) {
                            { Text(stringResource(strings.ai_provider_name_required)) }
                        } else null,
                )
                Spacer(Modifier.height(8.dp))

                OutlinedTextField(
                    value = baseUrl,
                    onValueChange = { baseUrl = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    label = { Text(stringResource(strings.ai_provider_base_url)) },
                    placeholder = { Text("https://api.example.com") },
                    isError = baseUrlError,
                    supportingText =
                        if (baseUrlError) {
                            { Text(stringResource(strings.ai_provider_base_url_invalid)) }
                        } else null,
                )
                Spacer(Modifier.height(8.dp))

                OutlinedTextField(
                    value = requestPath,
                    onValueChange = { requestPath = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    label = { Text(stringResource(strings.ai_provider_path)) },
                    placeholder = { Text(CustomProviderSpec.DEFAULT_CHAT_PATH) },
                    supportingText = { Text(stringResource(strings.ai_provider_path_hint)) },
                )
                Spacer(Modifier.height(8.dp))

                OutlinedTextField(
                    value = modelsText,
                    onValueChange = { modelsText = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(strings.ai_provider_models)) },
                    placeholder = { Text("model-a, model-b") },
                    supportingText = { Text(stringResource(strings.ai_provider_models_hint)) },
                )
                Spacer(Modifier.height(12.dp))

                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = stringResource(strings.ai_provider_requires_key),
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.weight(1f),
                    )
                    Switch(checked = requiresApiKey, onCheckedChange = { requiresApiKey = it })
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (problem == null) {
                        onSave(candidate)
                    } else {
                        showErrors = true
                    }
                }
            ) {
                Text(stringResource(strings.apply))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(strings.cancel)) } },
    )
}

private fun selectProvider(provider: AiProvider) {
    AiSettings.providerId = provider.id
    AiProviderRuntime.invalidate()
}

@Composable
private fun ProviderIcon(icon: ImageVector) {
    Icon(
        imageVector = icon,
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
