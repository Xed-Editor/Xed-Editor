@file:OptIn(ExperimentalUuidApi::class)
package com.rk.ai.nativeagent.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.rk.ai.models.Assistant
import com.rk.ai.persistence.settings.Settings
import com.rk.ai.persistence.settings.SettingsStore
import com.rk.ai.persistence.settings.getCurrentAssistant
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

data class AgentConfig(
    val id: String = Uuid.random().toString(),
    val name: String = "",
    val description: String = "",
    val systemPrompt: String = "",
    val modelId: String = "",
    val temperature: Float = 0.7f,
    val mode: String = "subagent",
    val enableMemory: Boolean = false,
    val enableWebSearch: Boolean = false,
    val maxTokens: Int = 4096,
    val reasoningLevel: String = "NONE",
    val color: String = "primary",
)

@Composable
fun AgentConfigPanel(
    settingsStore: SettingsStore,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val settings by settingsStore.settingsFlow.collectAsState()
    var selectedAgentId by remember { mutableStateOf<String?>(null) }
    var showEditor by remember { mutableStateOf(false) }
    var editingConfig by remember { mutableStateOf<AgentConfig?>(null) }
    val currentAssistant = settings.getCurrentAssistant()

    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.surface,
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                tonalElevation = 2.dp,
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Outlined.Close, contentDescription = "Close")
                    }
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "Agent Manager",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(Modifier.weight(1f))
                    FilledTonalButton(
                        onClick = {
                            editingConfig = AgentConfig(
                                id = Uuid.random().toString(),
                                name = "new-agent",
                                systemPrompt = "You are a helpful assistant.",
                            )
                            showEditor = true
                        }
                    ) {
                        Icon(Icons.Outlined.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("New Agent")
                    }
                }
            }

            if (showEditor && editingConfig != null) {
                AgentEditorSheet(
                    config = editingConfig!!,
                    settings = settings,
                    onSave = { config ->
                        settingsStore.update { s ->
                            val updatedAssistant = Assistant(
                                id = Uuid.parse(config.id),
                                name = config.name,
                                systemPrompt = config.systemPrompt,
                                temperature = config.temperature,
                                maxTokens = config.maxTokens,
                                enableMemory = config.enableMemory,
                            )
                            val updatedList = s.assistants.map { a ->
                                if (a.id == updatedAssistant.id) updatedAssistant else a
                            }
                            if (updatedList.none { it.id == updatedAssistant.id }) {
                                s.copy(assistants = updatedList + updatedAssistant)
                            } else {
                                s.copy(assistants = updatedList)
                            }
                        }
                        editingConfig = null
                        showEditor = false
                    },
                    onDelete = {
                        editingConfig?.let { cfg ->
                            settingsStore.update { s ->
                                s.copy(assistants = s.assistants.filter { it.id != Uuid.parse(cfg.id) })
                            }
                        }
                        editingConfig = null
                        showEditor = false
                    },
                    onCancel = {
                        editingConfig = null
                        showEditor = false
                    },
                    modifier = Modifier.weight(1f),
                )
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    item {
                        Text(
                            "Current Agent",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Spacer(Modifier.height(4.dp))
                        AgentCard(
                            name = currentAssistant.name,
                            description = "Active assistant",
                            mode = if (currentAssistant.enableMemory) "memory" else "standard",
                            color = "primary",
                            isActive = true,
                            onClick = {},
                        )
                    }

                    item {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Available Agents",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }

                    item {
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceContainerHighest,
                            shape = RoundedCornerShape(12.dp),
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text(
                                    "Agent configuration is managed through Settings.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    "Inspired by opencode's markdown-agent system: create .md files in .opencode/agents/",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AgentCard(
    name: String,
    description: String,
    mode: String,
    color: String,
    isActive: Boolean,
    onClick: () -> Unit,
) {
    val chipColor = when (color) {
        "primary" -> MaterialTheme.colorScheme.primary
        "secondary" -> MaterialTheme.colorScheme.secondary
        "tertiary" -> MaterialTheme.colorScheme.tertiary
        else -> MaterialTheme.colorScheme.primary
    }

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = if (isActive) MaterialTheme.colorScheme.primaryContainer
            else MaterialTheme.colorScheme.surfaceContainerHighest,
        tonalElevation = if (isActive) 2.dp else 0.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = chipColor.copy(alpha = 0.15f),
            ) {
                Icon(
                    Icons.Outlined.Psychology,
                    contentDescription = null,
                    tint = chipColor,
                    modifier = Modifier.padding(8.dp).size(24.dp),
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    name,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                )
                Text(
                    description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Surface(
                shape = RoundedCornerShape(4.dp),
                color = when (mode) {
                    "memory" -> MaterialTheme.colorScheme.tertiaryContainer
                    else -> MaterialTheme.colorScheme.secondaryContainer
                },
            ) {
                Text(
                    mode.uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                )
            }
            if (isActive) {
                Spacer(Modifier.width(8.dp))
                Icon(
                    Icons.Outlined.CheckCircle,
                    contentDescription = "Active",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}

@Composable
private fun AgentEditorSheet(
    config: AgentConfig,
    settings: Settings,
    onSave: (AgentConfig) -> Unit,
    onDelete: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var name by remember { mutableStateOf(config.name) }
    var systemPrompt by remember { mutableStateOf(config.systemPrompt) }
    var temperature by remember { mutableStateOf(config.temperature.toString()) }
    var maxTokens by remember { mutableStateOf(config.maxTokens.toString()) }
    var enableMemory by remember { mutableStateOf(config.enableMemory) }

    Column(modifier = modifier.padding(16.dp)) {
        Text(
            if (config.name.isBlank()) "New Agent" else "Edit: ${config.name}",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(16.dp))

        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text("Agent Name") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )
        Spacer(Modifier.height(12.dp))

        OutlinedTextField(
            value = systemPrompt,
            onValueChange = { systemPrompt = it },
            label = { Text("System Prompt") },
            modifier = Modifier.fillMaxWidth().heightIn(min = 120.dp),
            maxLines = 10,
        )
        Spacer(Modifier.height(12.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(
                value = temperature,
                onValueChange = { temperature = it },
                label = { Text("Temperature") },
                modifier = Modifier.weight(1f),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            )
            OutlinedTextField(
                value = maxTokens,
                onValueChange = { maxTokens = it },
                label = { Text("Max Tokens") },
                modifier = Modifier.weight(1f),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            )
        }
        Spacer(Modifier.height(12.dp))

        Row(
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Enable Memory", style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.weight(1f))
            Switch(
                checked = enableMemory,
                onCheckedChange = { enableMemory = it },
            )
        }
        Spacer(Modifier.height(24.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedButton(onClick = onCancel, modifier = Modifier.weight(1f)) {
                Text("Cancel")
            }
            Button(
                onClick = {
                    onSave(config.copy(
                        name = name,
                        systemPrompt = systemPrompt,
                        temperature = temperature.toFloatOrNull() ?: 0.7f,
                        maxTokens = maxTokens.toIntOrNull() ?: 4096,
                        enableMemory = enableMemory,
                    ))
                },
                modifier = Modifier.weight(1f),
            ) {
                Text("Save Agent")
            }
        }
    }
}
