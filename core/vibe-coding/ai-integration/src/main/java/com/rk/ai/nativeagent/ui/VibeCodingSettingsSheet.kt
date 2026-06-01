@file:OptIn(ExperimentalUuidApi::class)
package com.rk.ai.nativeagent.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import com.rk.ai.models.Assistant
import com.rk.ai.models.McpCommonOptions
import com.rk.ai.models.McpServerConfig
import com.rk.ai.nativeagent.engine.VibeCodingEngine
import com.rk.ai.persistence.settings.getCurrentAssistant
import com.rk.ai.providers.Model
import com.rk.ai.providers.ModelType
import com.rk.ai.providers.ProviderManager
import com.rk.ai.providers.ProviderSetting
import com.rk.ai.providers.registry.ModelRegistry
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import kotlinx.coroutines.launch

private val McpServerConfig.displayUrl: String get() = when (this) {
    is McpServerConfig.SseTransportServer -> url
    is McpServerConfig.StreamableHTTPServer -> url
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VibeCodingSettingsSheet(
    engine: VibeCodingEngine,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val settings by engine.settingsStore.settingsFlow.collectAsState()
    val colorScheme = MaterialTheme.colorScheme
    val scope = rememberCoroutineScope()

    var selectedModelId by remember { mutableStateOf(settings.chatModelId) }
    var editProviderId by remember { mutableStateOf<Uuid?>(null) }
    var showAddProvider by remember { mutableStateOf(false) }
    var editMcpServerId by remember { mutableStateOf<Uuid?>(null) }
    var showAddMcpServer by remember { mutableStateOf(false) }

    val allChatModels: List<Pair<ProviderSetting, Model>> = remember(settings) {
        settings.providers
            .filter { it.enabled }
            .flatMap { provider ->
                provider.models
                    .filter { it.type == ModelType.CHAT }
                    .map { model -> provider to model }
            }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 16.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "VibeCoding Settings",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Outlined.Close, contentDescription = "Close")
                }
            }

            Spacer(Modifier.height(8.dp))

            if (editProviderId != null) {
                ProviderEditor(
                    providerId = editProviderId!!,
                    settings = settings,
                    engine = engine,
                    colorScheme = colorScheme,
                    scope = scope,
                    onBack = { editProviderId = null },
                )
            } else {
                SettingsContent(
                    engine = engine,
                    settings = settings,
                    colorScheme = colorScheme,
                    scope = scope,
                    selectedModelId = selectedModelId,
                    allChatModels = allChatModels,
                    onSelectModel = { selectedModelId = it },
                    onEditProvider = { editProviderId = it },
                    onAddProvider = { showAddProvider = true },
                    onEditMcpServer = { editMcpServerId = it },
                    onAddMcpServer = { showAddMcpServer = true },
                    onApplyModel = {
                        scope.launch {
                            engine.settingsStore.update { s -> s.copy(chatModelId = selectedModelId) }
                        }
                        onDismiss()
                    },
                )
            }
        }
    }

    if (showAddProvider) {
        AddProviderDialog(
            onDismiss = { showAddProvider = false },
            onAdd = { provider ->
                scope.launch {
                    engine.settingsStore.update { s ->
                        s.copy(providers = s.providers + provider)
                    }
                }
                showAddProvider = false
            },
            colorScheme = colorScheme,
        )
    }

    if (showAddMcpServer) {
        McpServerEditDialog(
            server = null,
            onDismiss = { showAddMcpServer = false },
            onSave = { server ->
                scope.launch {
                    engine.settingsStore.update { s ->
                        s.copy(mcpServers = s.mcpServers + server)
                    }
                }
                showAddMcpServer = false
            },
            colorScheme = colorScheme,
        )
    }

    editMcpServerId?.let { id ->
        val server = settings.mcpServers.firstOrNull { it.id == id }
        if (server != null) {
            McpServerEditDialog(
                server = server,
                onDismiss = { editMcpServerId = null },
                onSave = { updated ->
                    scope.launch {
                        engine.settingsStore.update { s ->
                            s.copy(mcpServers = s.mcpServers.map { if (it.id == id) updated else it })
                        }
                    }
                    editMcpServerId = null
                },
                onDelete = {
                    scope.launch {
                        engine.settingsStore.update { s ->
                            s.copy(mcpServers = s.mcpServers.filter { it.id != id })
                        }
                    }
                    editMcpServerId = null
                },
                colorScheme = colorScheme,
            )
        } else {
            LaunchedEffect(Unit) { editMcpServerId = null }
        }
    }
}

@Composable
private fun SettingsContent(
    engine: VibeCodingEngine,
    settings: com.rk.ai.persistence.settings.Settings,
    colorScheme: ColorScheme,
    scope: kotlinx.coroutines.CoroutineScope,
    selectedModelId: kotlin.uuid.Uuid,
    allChatModels: List<Pair<ProviderSetting, Model>>,
    onSelectModel: (kotlin.uuid.Uuid) -> Unit,
    onEditProvider: (kotlin.uuid.Uuid) -> Unit,
    onAddProvider: () -> Unit,
    onEditMcpServer: (kotlin.uuid.Uuid) -> Unit,
    onAddMcpServer: () -> Unit,
    onApplyModel: () -> Unit,
) {
    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.heightIn(max = 600.dp),
    ) {
        item { ModelSection(settings, colorScheme, selectedModelId, allChatModels, onSelectModel) }
        item { Divider(Modifier.padding(vertical = 4.dp)) }
        item { ProvidersSection(settings, colorScheme, scope, engine, onEditProvider, onAddProvider) }
        item { Divider(Modifier.padding(vertical = 4.dp)) }
        item { AssistantSection(settings, colorScheme, scope, engine) }
        item { Divider(Modifier.padding(vertical = 4.dp)) }
        item { WebSearchSection(settings, colorScheme, scope, engine) }
        item { Divider(Modifier.padding(vertical = 4.dp)) }
        item { AdvancedSection(settings, colorScheme, scope, engine, onEditMcpServer, onAddMcpServer) }
        item { Spacer(Modifier.height(8.dp)) }
        item {
            Button(
                onClick = onApplyModel,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
            ) {
                Text("Apply")
            }
        }
        item { Spacer(Modifier.height(16.dp)) }
    }
}

@Composable
private fun SectionHeader(title: String, icon: @Composable () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        icon()
        Text(text = title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun ModelSection(
    settings: com.rk.ai.persistence.settings.Settings,
    colorScheme: ColorScheme,
    selectedModelId: kotlin.uuid.Uuid,
    allChatModels: List<Pair<ProviderSetting, Model>>,
    onSelectModel: (kotlin.uuid.Uuid) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }

    Surface(
        modifier = Modifier.fillMaxWidth().clickable { expanded = !expanded },
        shape = RoundedCornerShape(12.dp),
        color = colorScheme.surfaceVariant.copy(alpha = 0.3f),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            SectionHeader(
                title = "Model",
                icon = { Icon(Icons.Outlined.ModelTraining, contentDescription = null, modifier = Modifier.size(18.dp)) }
            )
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth().clickable { expanded = !expanded },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val currentModel = settings.providers.flatMap { it.models }
                    .firstOrNull { it.id == settings.chatModelId }
                Text(
                    text = currentModel?.displayName?.ifEmpty { currentModel?.modelId } ?: "No model",
                    style = MaterialTheme.typography.bodyMedium,
                    color = colorScheme.primary,
                    modifier = Modifier.weight(1f),
                )
                Icon(
                    if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                )
            }
            AnimatedVisibility(visible = expanded, enter = expandVertically(), exit = shrinkVertically()) {
                if (allChatModels.isEmpty()) {
                    Text(
                        text = "No models available. Add a provider below.",
                        style = MaterialTheme.typography.bodySmall,
                        color = colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                } else {
                    Column(modifier = Modifier.padding(top = 8.dp)) {
                        allChatModels.forEach { (provider, model) ->
                            val isSelected = model.id == selectedModelId
                            Surface(
                                modifier = Modifier.fillMaxWidth().clickable { onSelectModel(model.id) },
                                shape = RoundedCornerShape(8.dp),
                                color = if (isSelected) colorScheme.primaryContainer.copy(alpha = 0.3f) else colorScheme.surface,
                                tonalElevation = if (isSelected) 1.dp else 0.dp,
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = model.displayName.ifEmpty { model.modelId },
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                                        )
                                        Text(
                                            text = provider.name,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                        )
                                    }
                                    if (isSelected) {
                                        Icon(Icons.Outlined.Check, contentDescription = null, tint = colorScheme.primary, modifier = Modifier.size(20.dp))
                                    }
                                }
                            }
                            Spacer(Modifier.height(4.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ProvidersSection(
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
private fun ProviderEditor(
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
            val apiKey = when (provider) {
                is ProviderSetting.OpenAI -> provider.apiKey
                is ProviderSetting.Google -> provider.apiKey
                is ProviderSetting.Claude -> provider.apiKey
            }
            if (apiKey.isNotBlank()) {
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
                            } finally {
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
private fun AddProviderDialog(
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

@Composable
private fun AssistantSection(
    settings: com.rk.ai.persistence.settings.Settings,
    colorScheme: ColorScheme,
    scope: kotlinx.coroutines.CoroutineScope,
    engine: VibeCodingEngine,
) {
    var expanded by remember { mutableStateOf(false) }
    val assistant = remember(settings) { settings.getCurrentAssistant() }

    var systemPrompt by remember(assistant) { mutableStateOf(assistant.systemPrompt) }
    var temperature by remember(assistant) { mutableStateOf(assistant.temperature?.toString() ?: "") }
    var topP by remember(assistant) { mutableStateOf(assistant.topP?.toString() ?: "") }
    var maxTokens by remember(assistant) { mutableStateOf(assistant.maxTokens?.toString() ?: "") }
    var enableMemory by remember(assistant) { mutableStateOf(assistant.enableMemory) }
    var streamOutput by remember(assistant) { mutableStateOf(assistant.streamOutput) }

    Surface(
        modifier = Modifier.fillMaxWidth().clickable { expanded = !expanded },
        shape = RoundedCornerShape(12.dp),
        color = colorScheme.surfaceVariant.copy(alpha = 0.3f),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            SectionHeader(
                title = "Assistant",
                icon = { Icon(Icons.Outlined.SmartToy, contentDescription = null, modifier = Modifier.size(18.dp)) }
            )
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth().clickable { expanded = !expanded },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = assistant.name.ifEmpty { "Default" },
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
                    OutlinedTextField(
                        value = systemPrompt,
                        onValueChange = { systemPrompt = it },
                        label = { Text("System Prompt") },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 80.dp),
                        maxLines = 6,
                        textStyle = MaterialTheme.typography.bodySmall,
                    )

                    Spacer(Modifier.height(8.dp))

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        OutlinedTextField(
                            value = temperature,
                            onValueChange = { temperature = it },
                            label = { Text("Temperature") },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            placeholder = { Text("Auto") },
                        )
                        OutlinedTextField(
                            value = topP,
                            onValueChange = { topP = it },
                            label = { Text("Top P") },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            placeholder = { Text("Auto") },
                        )
                    }

                    Spacer(Modifier.height(8.dp))

                    OutlinedTextField(
                        value = maxTokens,
                        onValueChange = { maxTokens = it },
                        label = { Text("Max Tokens") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        placeholder = { Text("Unlimited") },
                    )

                    Spacer(Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = enableMemory, onCheckedChange = { enableMemory = it })
                            Text("Enable Memory", style = MaterialTheme.typography.bodySmall)
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = streamOutput, onCheckedChange = { streamOutput = it })
                            Text("Stream Output", style = MaterialTheme.typography.bodySmall)
                        }
                    }

                    Spacer(Modifier.height(8.dp))

                    Button(
                        onClick = {
                            scope.launch {
                                engine.settingsStore.update { s ->
                                    s.copy(assistants = s.assistants.map { a ->
                                        if (a.id == assistant.id) {
                                            a.copy(
                                                systemPrompt = systemPrompt,
                                                temperature = temperature.toFloatOrNull(),
                                                topP = topP.toFloatOrNull(),
                                                maxTokens = maxTokens.toIntOrNull(),
                                                enableMemory = enableMemory,
                                                streamOutput = streamOutput,
                                            )
                                        } else a
                                    })
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                    ) {
                        Text("Save Assistant", style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        }
    }
}

@Composable
private fun WebSearchSection(
    settings: com.rk.ai.persistence.settings.Settings,
    colorScheme: ColorScheme,
    scope: kotlinx.coroutines.CoroutineScope,
    engine: VibeCodingEngine,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = colorScheme.surfaceVariant.copy(alpha = 0.3f),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            SectionHeader(
                title = "Web Search",
                icon = { Icon(Icons.Outlined.Language, contentDescription = null, modifier = Modifier.size(18.dp)) }
            )
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text("Enable web search for real-time info", style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                Switch(
                    checked = settings.enableWebSearch,
                    onCheckedChange = {
                        scope.launch {
                            engine.settingsStore.update { s -> s.copy(enableWebSearch = it) }
                        }
                    },
                )
            }
        }
    }
}

@Composable
private fun AdvancedSection(
    settings: com.rk.ai.persistence.settings.Settings,
    colorScheme: ColorScheme,
    scope: kotlinx.coroutines.CoroutineScope,
    engine: VibeCodingEngine,
    onEditMcpServer: (kotlin.uuid.Uuid) -> Unit,
    onAddMcpServer: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }

    Surface(
        modifier = Modifier.fillMaxWidth().clickable { expanded = !expanded },
        shape = RoundedCornerShape(12.dp),
        color = colorScheme.surfaceVariant.copy(alpha = 0.3f),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            SectionHeader(
                title = "Advanced",
                icon = { Icon(Icons.Outlined.Tune, contentDescription = null, modifier = Modifier.size(18.dp)) }
            )
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth().clickable { expanded = !expanded },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Developer mode, MCP servers",
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
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text("Developer Mode", style = MaterialTheme.typography.bodySmall)
                        Switch(
                            checked = settings.developerMode,
                            onCheckedChange = {
                                scope.launch {
                                    engine.settingsStore.update { s -> s.copy(developerMode = it) }
                                }
                            },
                        )
                    }

                    Spacer(Modifier.height(12.dp))

                    Text("MCP Servers", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(4.dp))

                    if (settings.mcpServers.isEmpty()) {
                        Text(
                            text = "No MCP servers configured.",
                            style = MaterialTheme.typography.bodySmall,
                            color = colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        )
                    } else {
                        settings.mcpServers.forEach { server ->
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 2.dp)
                                    .clickable { onEditMcpServer(server.id) },
                                shape = RoundedCornerShape(6.dp),
                                color = colorScheme.surface,
                                tonalElevation = 0.5.dp,
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = server.commonOptions.name.ifEmpty { "Unnamed Server" },
                                            style = MaterialTheme.typography.bodySmall,
                                            fontWeight = FontWeight.Medium,
                                        )
                                        Text(
                                            text = server.displayUrl.take(50),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                        )
                                    }
                                    Icon(
                                        if (server.commonOptions.enable) Icons.Outlined.CheckCircle else Icons.Outlined.HorizontalRule,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp),
                                        tint = if (server.commonOptions.enable) colorScheme.primary else colorScheme.onSurfaceVariant.copy(alpha = 0.3f),
                                    )
                                }
                            }
                        }
                    }

                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = onAddMcpServer,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp),
                    ) {
                        Icon(Icons.Outlined.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Add MCP Server", style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        }
    }
}

@Composable
private fun McpServerEditDialog(
    server: McpServerConfig?,
    onDismiss: () -> Unit,
    onSave: (McpServerConfig) -> Unit,
    onDelete: (() -> Unit)? = null,
    colorScheme: ColorScheme,
) {
    val isNew = server == null
    var name by remember(server) { mutableStateOf(server?.commonOptions?.name ?: "") }
    var url by remember(server) { mutableStateOf(server?.displayUrl ?: "") }
    var useSse by remember(server) { mutableStateOf(server is McpServerConfig.SseTransportServer) }
    var enabled by remember(server) { mutableStateOf(server?.commonOptions?.enable ?: true) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isNew) "Add MCP Server" else "Edit MCP Server") },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Server Name") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    placeholder = { Text("My MCP Server") },
                )

                Spacer(Modifier.height(8.dp))

                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    label = { Text("Server URL") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    placeholder = { Text("https://example.com/mcp") },
                )

                Spacer(Modifier.height(8.dp))

                Text("Transport Type", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Medium)
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected = useSse, onClick = { useSse = true })
                    Text("SSE", modifier = Modifier.clickable { useSse = true }, style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.width(16.dp))
                    RadioButton(selected = !useSse, onClick = { useSse = false })
                    Text("Streamable HTTP", modifier = Modifier.clickable { useSse = false }, style = MaterialTheme.typography.bodySmall)
                }

                Spacer(Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text("Enabled", style = MaterialTheme.typography.bodySmall)
                    Switch(checked = enabled, onCheckedChange = { enabled = it })
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val newServer: McpServerConfig = if (useSse) {
                        McpServerConfig.SseTransportServer(
                            id = server?.id ?: Uuid.random(),
                            commonOptions = McpCommonOptions(
                                enable = enabled,
                                name = name,
                            ),
                            url = url,
                        )
                    } else {
                        McpServerConfig.StreamableHTTPServer(
                            id = server?.id ?: Uuid.random(),
                            commonOptions = McpCommonOptions(
                                enable = enabled,
                                name = name,
                            ),
                            url = url,
                        )
                    }
                    onSave(newServer)
                },
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            Row {
                if (!isNew && onDelete != null) {
                    TextButton(
                        onClick = { showDeleteConfirm = true },
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    ) {
                        Text("Delete")
                    }
                }
                TextButton(onClick = onDismiss) {
                    Text("Cancel")
                }
            }
        },
    )

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete MCP Server") },
            text = { Text("Are you sure you want to delete \"${name}\"? This cannot be undone.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteConfirm = false
                        onDelete?.invoke()
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
