@file:OptIn(ExperimentalUuidApi::class)
package com.rk.ai.nativeagent.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.rk.ai.nativeagent.engine.VibeCodingEngine
import com.rk.ai.providers.Model
import com.rk.ai.providers.ModelType
import com.rk.ai.providers.ProviderSetting
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import kotlinx.coroutines.launch

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
        sheetMaxWidth = androidx.compose.ui.unit.Dp.Unspecified,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = MaterialTheme.colorScheme.onSurface,
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
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
        item { HorizontalDivider(Modifier.padding(vertical = 4.dp), color = colorScheme.outlineVariant.copy(alpha = 0.2f)) }
        item { ProvidersSection(settings, colorScheme, scope, engine, onEditProvider, onAddProvider) }
        item { HorizontalDivider(Modifier.padding(vertical = 4.dp), color = colorScheme.outlineVariant.copy(alpha = 0.2f)) }
        item { AssistantSection(settings, colorScheme, scope, engine) }
        item { HorizontalDivider(Modifier.padding(vertical = 4.dp), color = colorScheme.outlineVariant.copy(alpha = 0.2f)) }
        item { WebSearchSection(settings, colorScheme, scope, engine) }
        item { HorizontalDivider(Modifier.padding(vertical = 4.dp), color = colorScheme.outlineVariant.copy(alpha = 0.2f)) }
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
