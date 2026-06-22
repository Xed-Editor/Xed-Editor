@file:OptIn(ExperimentalUuidApi::class)
package com.rk.ai.nativeagent.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.rk.ai.nativeagent.engine.VibeCodingEngine
import com.rk.ai.providers.ProviderSetting
import com.rk.ai.providers.registry.ModelRegistry
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import kotlinx.coroutines.launch

@Composable
internal fun ProvidersSection(
    settings: com.rk.ai.persistence.settings.Settings,
    colorScheme: ColorScheme,
    scope: kotlinx.coroutines.CoroutineScope,
    engine: VibeCodingEngine,
    onEditProvider: (kotlin.uuid.Uuid) -> Unit,
    onAddProvider: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }

    Surface(
        modifier = Modifier.fillMaxWidth().clickable { expanded = !expanded },
        shape = RoundedCornerShape(12.dp),
        color = colorScheme.surfaceVariant.copy(alpha = 0.3f),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            SectionHeader(
                title = "Providers",
                icon = { Icon(Icons.Outlined.Cloud, contentDescription = null, modifier = Modifier.size(18.dp)) }
            )
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth().clickable { expanded = !expanded },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "${settings.providers.size} providers (${settings.providers.count { it.enabled }} enabled)",
                    style = MaterialTheme.typography.bodySmall,
                    color = colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                Icon(
                    if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                )
            }
            AnimatedVisibility(visible = expanded, enter = expandVertically(), exit = shrinkVertically()) {
                Column(modifier = Modifier.padding(top = 8.dp)) {
                    settings.providers.forEach { provider ->
                        ProviderCard(
                            provider = provider,
                            colorScheme = colorScheme,
                            onToggle = {
                                scope.launch {
                                    engine.settingsStore.update { s ->
                                        s.copy(providers = s.providers.map { p ->
                                            if (p.id == provider.id) p.copyProvider(enabled = !p.enabled) else p
                                        })
                                    }
                                }
                            },
                            onEdit = { onEditProvider(provider.id) },
                        )
                        Spacer(Modifier.height(4.dp))
                    }
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = onAddProvider,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp),
                    ) {
                        Icon(Icons.Outlined.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Add Provider", style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        }
    }
}

@Composable
private fun ProviderCard(
    provider: ProviderSetting,
    colorScheme: ColorScheme,
    onToggle: () -> Unit,
    onEdit: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        color = colorScheme.surface,
        tonalElevation = 0.5.dp,
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = provider.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                Text(
                    text = when (provider) {
                        is ProviderSetting.OpenAI -> "OpenAI-compatible"
                        is ProviderSetting.Google -> "Google AI"
                        is ProviderSetting.Claude -> "Anthropic Claude"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                )
            }
            Text(
                text = "${provider.models.size} models",
                style = MaterialTheme.typography.labelSmall,
                color = colorScheme.onSurfaceVariant,
            )
            Switch(
                checked = provider.enabled,
                onCheckedChange = { onToggle() },
                modifier = Modifier.padding(horizontal = 8.dp),
            )
            IconButton(onClick = onEdit, modifier = Modifier.size(28.dp)) {
                Icon(Icons.Outlined.Edit, contentDescription = "Edit", modifier = Modifier.size(16.dp))
            }
        }
    }
}

@Composable
internal fun ProviderEditor(
    providerId: kotlin.uuid.Uuid,
    settings: com.rk.ai.persistence.settings.Settings,
    engine: VibeCodingEngine,
    colorScheme: ColorScheme,
    scope: kotlinx.coroutines.CoroutineScope,
    onBack: () -> Unit,
) {
    val provider = remember(settings) { settings.providers.firstOrNull { it.id == providerId } } ?: run {
        Text("Provider not found"); return
    }

    var apiKey by remember(provider) {
        mutableStateOf(
            when (provider) {
                is ProviderSetting.OpenAI -> provider.apiKey
                is ProviderSetting.Google -> provider.apiKey
                is ProviderSetting.Claude -> provider.apiKey
            }
        )
    }
    var baseUrl by remember(provider) {
        mutableStateOf(
            when (provider) {
                is ProviderSetting.OpenAI -> provider.baseUrl
                is ProviderSetting.Google -> provider.baseUrl
                is ProviderSetting.Claude -> provider.baseUrl
            }
        )
    }
    var showApiKey by remember { mutableStateOf(false) }
    var modelsExpanded by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var isFetchingModels by remember { mutableStateOf(false) }
    var fetchModelsError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(providerId) {
        if (provider.models.isEmpty()) {
            val currentApiKey = when (provider) {
                is ProviderSetting.OpenAI -> provider.apiKey
                is ProviderSetting.Google -> provider.apiKey
                is ProviderSetting.Claude -> provider.apiKey
            }
            if (currentApiKey.isNotBlank()) {
                isFetchingModels = true
                try {
                    val apiProvider = engine.providerManager.getProviderByType(provider)
                    val apiModels = apiProvider.listModels(provider)
                    val enriched = apiModels.sortedBy { it.modelId }.map { model ->
                        model.copy(
                            displayName = model.modelId,
                            inputModalities = ModelRegistry.MODEL_INPUT_MODALITIES.getData(model.modelId),
                            outputModalities = ModelRegistry.MODEL_OUTPUT_MODALITIES.getData(model.modelId),
                            abilities = ModelRegistry.MODEL_ABILITIES.getData(model.modelId),
                        )
                    }
                    engine.settingsStore.update { s ->
                        s.copy(providers = s.providers.map { p ->
                            if (p.id == providerId) p.copyProvider(models = enriched) else p
                        })
                    }
                } catch (_: Exception) { }
                isFetchingModels = false
            }
        }
    }

    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.Outlined.ArrowBack, contentDescription = "Back")
            }
            Text(
                text = "Edit: ${provider.name}",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
        }

        Spacer(Modifier.height(8.dp))

        OutlinedTextField(
            value = apiKey,
            onValueChange = { apiKey = it },
            label = { Text("API Key") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            visualTransformation = if (showApiKey) VisualTransformation.None else PasswordVisualTransformation(),
            trailingIcon = {
                IconButton(onClick = { showApiKey = !showApiKey }) {
                    Icon(
                        if (showApiKey) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                        contentDescription = null,
                    )
                }
            },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        )

        Spacer(Modifier.height(8.dp))

        OutlinedTextField(
            value = baseUrl,
            onValueChange = { baseUrl = it },
            label = { Text("Base URL") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
        )

        Spacer(Modifier.height(12.dp))

        Surface(
            modifier = Modifier.fillMaxWidth().clickable { modelsExpanded = !modelsExpanded },
            shape = RoundedCornerShape(8.dp),
            color = colorScheme.surface,
        ) {
            Row(
                modifier = Modifier.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Models (${provider.models.size})",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f),
                )
                Icon(
                    if (modelsExpanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                )
            }
        }

        AnimatedVisibility(visible = modelsExpanded, enter = expandVertically(), exit = shrinkVertically()) {
            Column(modifier = Modifier.padding(top = 4.dp)) {
                provider.models.forEach { model ->
                    Surface(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                        shape = RoundedCornerShape(6.dp),
                        color = colorScheme.surfaceVariant.copy(alpha = 0.2f),
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                  Text(text = model.displayName.ifEmpty { model.modelId }, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
                                Text(
                                    text = buildString {
                                        append(model.type.name)
                                        if (model.abilities.isNotEmpty()) append(" · ${model.abilities.joinToString(", ")}")
                                    },
                                    style = MaterialTheme.typography.labelSmall,
                                    color = colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                )
                            }
                        }
                    }
                }

                Spacer(Modifier.height(8.dp))

                OutlinedButton(
                    onClick = {
                        scope.launch {
                            isFetchingModels = true
                            fetchModelsError = null
                            try {
                                val currentProvider = engine.settingsStore.settingsFlow.value.providers
                                    .firstOrNull { it.id == providerId } ?: return@launch
                                val apiProvider = engine.providerManager.getProviderByType(currentProvider)
                                val apiModels = apiProvider.listModels(currentProvider)
                                val enrichedModels = apiModels.sortedBy { it.modelId }.map { model ->
                                    model.copy(
                                        displayName = model.modelId,
                                        inputModalities = ModelRegistry.MODEL_INPUT_MODALITIES.getData(model.modelId),
                                        outputModalities = ModelRegistry.MODEL_OUTPUT_MODALITIES.getData(model.modelId),
                                        abilities = ModelRegistry.MODEL_ABILITIES.getData(model.modelId),
                                    )
                                }
                                engine.settingsStore.update { s ->
                                    s.copy(providers = s.providers.map { p ->
                                        if (p.id == providerId) p.copyProvider(models = enrichedModels) else p
                                    })
                                }
                            } catch (e: Exception) {
                                fetchModelsError = e.message ?: "Failed to fetch models"
                            } else {
                                isFetchingModels = false
                            }
                        }
                    },
                    enabled = !isFetchingModels,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                ) {
                    if (isFetchingModels) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(4.dp))
                    } else {
                        Icon(Icons.Outlined.CloudDownload, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                    }
                    Text("Fetch Models", style = MaterialTheme.typography.labelMedium)
                }

                fetchModelsError?.let { error ->
                    Text(
                        text = error,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        Button(
            onClick = {
                scope.launch {
                    engine.settingsStore.update { s ->
                        s.copy(providers = s.providers.map { p ->
                            if (p.id == providerId) {
                                when (p) {
                                    is ProviderSetting.OpenAI -> p.copy(apiKey = apiKey, baseUrl = baseUrl)
                                    is ProviderSetting.Google -> p.copy(apiKey = apiKey, baseUrl = baseUrl)
                                    is ProviderSetting.Claude -> p.copy(apiKey = apiKey, baseUrl = baseUrl)
                                }
                            } else p
                        })
                    }
                }
                onBack()
            },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
        ) {
            Text("Save Provider")
        }

        if (!provider.builtIn) {
            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = { showDeleteConfirm = true },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
            ) {
                Icon(Icons.Outlined.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("Delete Provider")
            }
        }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete Provider") },
            text = { Text("Are you sure you want to delete \"${provider.name}\"? This cannot be undone.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        scope.launch {
                            engine.settingsStore.update { s ->
                                s.copy(providers = s.providers.filter { it.id != providerId })
                            }
                        }
                        showDeleteConfirm = false
                        onBack()
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text("Cancel")
                }
            },
        )
    }
}

@Composable
internal fun AddProviderDialog(
    onDismiss: () -> Unit,
    onAdd: (ProviderSetting) -> Unit,
    colorScheme: ColorScheme,
) {
    var selectedType by remember { mutableStateOf(0) }
    var providerName by remember { mutableStateOf("") }

    val typeOptions = listOf(
        "OpenAI-compatible" to "OpenAI / any OpenAI-compatible API",
        "Google AI" to "Gemini / Google AI API",
        "Anthropic Claude" to "Claude / Anthropic API",
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Provider") },
        text = {
            Column {
                Text("Select provider type:", style = MaterialTheme.typography.bodySmall, color = colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(8.dp))
                Column(Modifier.selectableGroup()) {
                    typeOptions.forEachIndexed { index, (label, desc) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .selectable(
                                    selected = index == selectedType,
                                    onClick = { selectedType = index },
                                    role = Role.RadioButton,
                                )
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(selected = index == selectedType, onClick = null)
                            Spacer(Modifier.width(8.dp))
                            Column {
                                Text(label, style = MaterialTheme.typography.bodyMedium)
                                Text(desc, style = MaterialTheme.typography.labelSmall, color = colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
                            }
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = providerName,
                    onValueChange = { providerName = it },
                    label = { Text("Provider Name") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    placeholder = { Text(typeOptions[selectedType].first) },
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val name = providerName.ifBlank { typeOptions[selectedType].first }
                    val provider = when (selectedType) {
                        1 -> ProviderSetting.Google(
                            name = name,
                            apiKey = "",
                        )
                        2 -> ProviderSetting.Claude(
                            name = name,
                            apiKey = "",
                        )
                        else -> ProviderSetting.OpenAI(
                            name = name,
                            baseUrl = "https://api.openai.com/v1",
                            apiKey = "",
                        )
                    }
                    onAdd(provider)
                },
            ) {
                Text("Add")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
    )
}
