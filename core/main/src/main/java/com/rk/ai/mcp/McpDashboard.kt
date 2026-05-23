package com.rk.ai.mcp

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun McpDashboard(
    manager: McpManager,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val states = remember { mutableStateListOf<McpConnectionState>() }
    val events = remember { mutableStateListOf<McpEvent>() }
    val maxEvents = 100

    DisposableEffect(manager) {
        val listener: (McpEvent) -> Unit = { event ->
            events.add(event)
            if (events.size > maxEvents) events.removeAt(0)
            when (event) {
                is McpEvent.ConnectionChanged -> {
                    manager.getAllStates().forEach { state ->
                        val idx = states.indexOfFirst { it.serverId == state.serverId }
                        if (idx >= 0) states[idx] = state else states.add(state)
                    }
                }
                else -> {}
            }
        }
        manager.onEvent(listener)
        states.addAll(manager.getAllStates())
        onDispose { manager.removeListener(listener) }
    }

    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.surface,
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            TopAppBar(
                title = { Text("MCP Dashboard") },
                navigationIcon = {
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Outlined.Close, "Close")
                    }
                },
                actions = {
                    IconButton(onClick = { events.clear() }) {
                        Icon(Icons.Outlined.Clear, "Clear events")
                    }
                },
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                val connected = states.count { it.status == ConnectionStatus.CONNECTED }
                val total = states.size
                ServerStatCard("Connected", "$connected/$total", Color(0xFF4CAF50))
                ServerStatCard("Errors", "${states.count { it.status == ConnectionStatus.ERROR }}", Color(0xFFEF5350))
                ServerStatCard("Tools", "${states.sumOf { it.tools.size }}", Color(0xFF42A5F5))
            }

            if (states.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        "No MCP servers configured",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(states) { state ->
                        McpServerCard(manager, state)
                    }
                }
            }

            if (events.isNotEmpty()) {
                Text(
                    "Event Log (${events.size})",
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                )
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(150.dp)
                        .padding(horizontal = 8.dp),
                ) {
                    items(events.takeLast(20).reversed()) { event ->
                        Text(
                            text = when (event) {
                                is McpEvent.ConnectionChanged -> "${event.serverId}: ${event.status}"
                                is McpEvent.ToolExecuted -> "${event.serverId}: ${event.toolName} ${if (event.result.success) "OK" else "FAIL"}"
                                is McpEvent.Error -> "${event.serverId}: ${event.message}"
                                is McpEvent.Log -> "${event.serverId} [${event.level}]: ${event.message}"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 10.sp,
                            modifier = Modifier.padding(vertical = 1.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ServerStatCard(title: String, value: String, color: Color) {
    Card(
        modifier = Modifier.weight(1f),
        colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.1f)),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(value, fontWeight = FontWeight.Bold, fontSize = 20.sp, color = color)
            Text(title, style = MaterialTheme.typography.labelSmall, color = color)
        }
    }
}

@Composable
private fun McpServerCard(manager: McpManager, state: McpConnectionState) {
    val expanded = remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        onClick = { expanded.value = !expanded.value },
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(
                            when (state.status) {
                                ConnectionStatus.CONNECTED -> Color(0xFF4CAF50)
                                ConnectionStatus.CONNECTING, ConnectionStatus.RECONNECTING -> Color(0xFFFFC107)
                                ConnectionStatus.ERROR -> Color(0xFFEF5350)
                                ConnectionStatus.DISCONNECTED -> Color(0xFF9E9E9E)
                            }
                        )
                )
                Spacer(Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(state.serverId, fontWeight = FontWeight.Medium)
                    Text(
                        text = when (state.status) {
                            ConnectionStatus.CONNECTED -> "${state.tools.size} tools"
                            ConnectionStatus.CONNECTING -> "Connecting..."
                            ConnectionStatus.RECONNECTING -> "Reconnecting (${state.reconnectAttempts})"
                            ConnectionStatus.ERROR -> "Error: ${state.lastError ?: "Unknown"}"
                            ConnectionStatus.DISCONNECTED -> "Disconnected"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = {
                    when (state.status) {
                        ConnectionStatus.DISCONNECTED, ConnectionStatus.ERROR -> manager.connectToServer(state.serverId)
                        else -> manager.disconnectServer(state.serverId)
                    }
                }) {
                    Icon(
                        when (state.status) {
                            ConnectionStatus.DISCONNECTED, ConnectionStatus.ERROR -> Icons.Outlined.PlayArrow
                            else -> Icons.Outlined.Stop
                        },
                        contentDescription = if (state.status == ConnectionStatus.CONNECTED) "Disconnect" else "Connect",
                    )
                }
            }

            AnimatedVisibility(visible = expanded.value) {
                Column(modifier = Modifier.padding(top = 8.dp)) {
                    if (state.tools.isNotEmpty()) {
                        Text("Tools:", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                        state.tools.forEach { tool ->
                            Text(
                                "  - $tool",
                                style = MaterialTheme.typography.labelSmall,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 10.sp,
                            )
                        }
                    }
                    if (state.lastError != null) {
                        Text(
                            "Last Error: ${state.lastError}",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFFEF5350),
                        )
                    }
                    Text(
                        "Connected: ${if (state.connectedAt > 0) java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.US).format(java.util.Date(state.connectedAt)) else "never"}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
