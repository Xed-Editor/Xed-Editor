@file:OptIn(ExperimentalUuidApi::class)
package com.rk.ai.nativeagent.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
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
import com.rk.ai.models.McpCommonOptions
import com.rk.ai.models.McpServerConfig
import com.rk.ai.nativeagent.engine.VibeCodingEngine
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import kotlinx.coroutines.launch

internal val McpServerConfig.displayUrl: String get() = when (this) {
    is McpServerConfig.SseTransportServer -> url
    is McpServerConfig.StreamableHTTPServer -> url
}

@Composable
internal fun AdvancedSection(
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
internal fun McpServerEditDialog(
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
