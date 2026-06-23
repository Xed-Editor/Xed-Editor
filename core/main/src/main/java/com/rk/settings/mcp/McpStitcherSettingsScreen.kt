package com.rk.settings.mcp

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.rk.ai.AiProvider
import com.rk.components.compose.preferences.base.PreferenceGroup
import com.rk.components.compose.preferences.base.PreferenceLayout
import com.rk.settings.Settings
import com.rk.utils.toast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

data class McpServerConfig(
    val name: String = "",
    val url: String = "",
    val apiKey: String? = null,
    val headers: Map<String, String> = emptyMap(),
    val enabled: Boolean = true,
    val timeoutMs: Long = 60_000L,
)

data class McpServersConfig(
    val mcpServers: Map<String, McpServerConfig> = emptyMap()
)

object McpConfigManager {
    private val gson = Gson()

    fun loadConfig(): McpServersConfig {
        val json = Settings.ai_mcp_servers_config
        if (json.isBlank()) return McpServersConfig()
        return try {
            gson.fromJson(json, object : TypeToken<McpServersConfig>() {}.type)
        } catch (_: Exception) {
            McpServersConfig()
        }
    }

    fun saveConfig(config: McpServersConfig) {
        Settings.ai_mcp_servers_config = gson.toJson(config)
    }

    fun addServer(config: McpServersConfig, server: McpServerConfig): McpServersConfig {
        val servers = config.mcpServers.toMutableMap()
        servers[server.name] = server
        return config.copy(mcpServers = servers)
    }

    fun removeServer(config: McpServersConfig, name: String): McpServersConfig {
        val servers = config.mcpServers.toMutableMap()
        servers.remove(name)
        return config.copy(mcpServers = servers)
    }
}

@Composable
fun McpStitcherSettingsScreen() {
    PreferenceLayout(label = "MCP Stitcher") {
        var config by remember { mutableStateOf(McpConfigManager.loadConfig()) }
        var showAddDialog by remember { mutableStateOf(false) }
        var isLoading by remember { mutableStateOf(false) }
        var serverToEdit by remember { mutableStateOf<Pair<String, McpServerConfig>?>(null) }

        val scope = rememberCoroutineScope()

        PreferenceGroup(heading = "External MCP Servers") {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                if (config.mcpServers.isEmpty()) {
                    Text(
                        text = "No MCP servers configured. Add a server to get started.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                } else {
                    config.mcpServers.forEach { (name, serverConfig) ->
                        ServerCard(
                            name = name,
                            config = serverConfig,
                            onToggle = { enabled ->
                                scope.launch {
                                    val updated = serverConfig.copy(enabled = enabled)
                                    config = McpConfigManager.addServer(config, updated)
                                    McpConfigManager.saveConfig(config)
                                    refreshStitcher()
                                }
                            },
                            onEdit = { serverToEdit = name to serverConfig },
                            onDelete = {
                                scope.launch {
                                    config = McpConfigManager.removeServer(config, name)
                                    McpConfigManager.saveConfig(config)
                                    refreshStitcher()
                                    toast("Removed server '$name'")
                                }
                            }
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                }

                Button(
                    onClick = { showAddDialog = true },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isLoading
                ) {
                    Icon(Icons.Filled.Add, contentDescription = "Add")
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Add Server")
                }

                Spacer(modifier = Modifier.height(8.dp))

                Button(
                    onClick = {
                        scope.launch {
                            isLoading = true
                            refreshStitcher()
                            config = McpConfigManager.loadConfig()
                            isLoading = false
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.outlinedButtonColors(),
                    enabled = !isLoading
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.height(18.dp).width(18.dp),
                            strokeWidth = 2.dp
                        )
                    } else {
                        Icon(Icons.Filled.Refresh, contentDescription = "Refresh")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Refresh Connections")
                }
            }
        }

        if (showAddDialog) {
            AddEditServerDialog(
                title = "Add MCP Server",
                initialName = "",
                initialUrl = "",
                initialApiKey = "",
                initialHeaders = emptyMap(),
                onDismiss = { showAddDialog = false },
                onConfirm = { name, url, apiKey, headers ->
                    scope.launch {
                        val newServer = McpServerConfig(
                            name = name,
                            url = url,
                            apiKey = apiKey.ifBlank { null },
                            headers = headers,
                            enabled = true
                        )
                        config = McpConfigManager.addServer(config, newServer)
                        McpConfigManager.saveConfig(config)
                        refreshStitcher()
                        toast("Added server '$name'")
                        showAddDialog = false
                    }
                }
            )
        }

        serverToEdit?.let { (name, serverConfig) ->
            AddEditServerDialog(
                title = "Edit MCP Server",
                initialName = name,
                initialUrl = serverConfig.url,
                initialApiKey = serverConfig.apiKey ?: "",
                initialHeaders = serverConfig.headers,
                isEditing = true,
                onDismiss = { serverToEdit = null },
                onConfirm = { newName, newUrl, newApiKey, headers ->
                    scope.launch {
                        if (newName != name) {
                            config = McpConfigManager.removeServer(config, name)
                        }
                        val updatedServer = McpServerConfig(
                            name = newName,
                            url = newUrl,
                            apiKey = newApiKey.ifBlank { null },
                            headers = headers,
                            enabled = serverConfig.enabled,
                            timeoutMs = serverConfig.timeoutMs
                        )
                        config = McpConfigManager.addServer(config, updatedServer)
                        McpConfigManager.saveConfig(config)
                        refreshStitcher()
                        toast("Updated server '$newName'")
                        serverToEdit = null
                    }
                }
            )
        }
    }
}

@Composable
private fun ServerCard(
    name: String,
    config: McpServerConfig,
    onToggle: (Boolean) -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = config.url,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (config.apiKey != null) {
                        Text(
                            text = "API Key: ${config.apiKey.take(8)}...",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (config.headers.isNotEmpty()) {
                        Text(
                            text = "Headers: ${config.headers.keys.joinToString(", ")}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Switch(
                    checked = config.enabled,
                    onCheckedChange = onToggle
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Spacer(modifier = Modifier.weight(1f))

                IconButton(onClick = onEdit) {
                    Text(
                        text = "Edit",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                IconButton(onClick = onDelete) {
                    Icon(
                        Icons.Filled.Delete,
                        contentDescription = "Delete",
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}

@Composable
private fun AddEditServerDialog(
    title: String,
    initialName: String,
    initialUrl: String,
    initialApiKey: String,
    initialHeaders: Map<String, String> = emptyMap(),
    isEditing: Boolean = false,
    onDismiss: () -> Unit,
    onConfirm: (name: String, url: String, apiKey: String, headers: Map<String, String>) -> Unit
) {
    var name by remember { mutableStateOf(initialName) }
    var url by remember { mutableStateOf(initialUrl) }
    var apiKey by remember { mutableStateOf(initialApiKey) }
    var headersJson by remember { mutableStateOf(
        if (initialHeaders.isNotEmpty()) {
            try { org.json.JSONObject(initialHeaders).toString(2) } catch (_: Exception) { "" }
        } else ""
    ) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Server Name") },
                    enabled = !isEditing,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    label = { Text("URL (https://example.com/mcp)") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = apiKey,
                    onValueChange = { apiKey = it },
                    label = { Text("API Key (optional)") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = headersJson,
                    onValueChange = { headersJson = it },
                    label = { Text("Headers JSON (optional)") },
                    placeholder = { Text("{\"X-Goog-Api-Key\": \"your-key\"}") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val headers = try {
                        if (headersJson.isNotBlank()) {
                            val obj = org.json.JSONObject(headersJson)
                            obj.keys().asSequence().associateWith { obj.getString(it) }
                        } else emptyMap()
                    } catch (_: Exception) { emptyMap() }
                    onConfirm(name, url, apiKey, headers)
                },
                enabled = name.isNotBlank() && url.isNotBlank()
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

private fun refreshStitcher() {
    AiProvider.ideBridge?.refreshStitcher()
}
