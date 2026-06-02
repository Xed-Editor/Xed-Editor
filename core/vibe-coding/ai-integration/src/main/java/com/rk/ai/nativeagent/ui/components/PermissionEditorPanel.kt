package com.rk.ai.nativeagent.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.rk.ai.nativeagent.engine.VibeCodingEngine
import com.rk.ai.nativeagent.engine.PermissionAction
import com.rk.ai.nativeagent.engine.PermissionAutoRespondRule
import kotlin.uuid.ExperimentalUuidApi

@OptIn(ExperimentalUuidApi::class)
@Composable
fun PermissionEditorPanel(
    engine: VibeCodingEngine,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by engine.state.collectAsState()
    var showAddDialog by remember { mutableStateOf(false) }
    var newToolPattern by remember { mutableStateOf("") }
    var newArgPattern by remember { mutableStateOf("*") }
    var newAction by remember { mutableStateOf(PermissionAction.ASK) }
    var newDescription by remember { mutableStateOf("") }

    val actionColors = mapOf(
        "allow" to MaterialTheme.colorScheme.primary,
        "ask" to MaterialTheme.colorScheme.tertiary,
        "deny" to MaterialTheme.colorScheme.error,
    )

    if (showAddDialog) {
        AlertDialog(
            onDismissRequest = { showAddDialog = false },
            title = { Text("Add Permission Rule") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = newToolPattern,
                        onValueChange = { newToolPattern = it },
                        label = { Text("Tool Name Pattern (e.g. runCommand)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                    )
                    OutlinedTextField(
                        value = newArgPattern,
                        onValueChange = { newArgPattern = it },
                        label = { Text("Arguments Pattern (e.g. *git *)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                    )
                    OutlinedTextField(
                        value = newDescription,
                        onValueChange = { newDescription = it },
                        label = { Text("Description") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                    )
                    Text("Action:", style = MaterialTheme.typography.bodyMedium)
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        PermissionAction.entries.forEach { action ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.clickable { newAction = action }
                            ) {
                                RadioButton(
                                    selected = newAction == action,
                                    onClick = { newAction = action }
                                )
                                Text(action.name)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (newToolPattern.isNotBlank()) {
                            engine.addPermissionAutoRespondRule(
                                PermissionAutoRespondRule(
                                    toolPattern = newToolPattern.trim(),
                                    argPattern = newArgPattern.trim(),
                                    action = newAction,
                                    description = newDescription.trim(),
                                )
                            )
                            showAddDialog = false
                            newToolPattern = ""
                            newArgPattern = "*"
                            newAction = PermissionAction.ASK
                            newDescription = ""
                        }
                    }
                ) {
                    Text("Add")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    Surface(
        modifier = modifier,
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
                    Icon(Icons.Outlined.Security, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "Permission Rules",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        "Last match wins",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.width(8.dp))
                    IconButton(onClick = { showAddDialog = true }) {
                        Icon(Icons.Outlined.Add, contentDescription = "Add Rule")
                    }
                    Spacer(Modifier.width(4.dp))
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Outlined.Close, contentDescription = "Close")
                    }
                }
            }

            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(state.permissionAutoRespondRules) { rule ->
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Surface(
                                        shape = RoundedCornerShape(4.dp),
                                        color = MaterialTheme.colorScheme.tertiaryContainer,
                                    ) {
                                        Text(
                                            rule.toolPattern,
                                            style = MaterialTheme.typography.labelSmall,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                        )
                                    }
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        rule.argPattern,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                    )
                                }
                                if (rule.description.isNotBlank()) {
                                    Spacer(Modifier.height(4.dp))
                                    Text(
                                        rule.description,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                val actionKey = rule.action.name.lowercase()
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = (actionColors[actionKey] ?: MaterialTheme.colorScheme.primary)
                                        .copy(alpha = 0.15f),
                                ) {
                                    Text(
                                        rule.action.name.uppercase(),
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = actionColors[actionKey] ?: MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                    )
                                }
                                Spacer(Modifier.width(8.dp))
                                IconButton(
                                    onClick = { engine.removePermissionAutoRespondRule(rule.id) },
                                    modifier = Modifier.size(24.dp)
                                ) {
                                    Icon(
                                        Icons.Outlined.Delete,
                                        contentDescription = "Delete Rule",
                                        tint = MaterialTheme.colorScheme.error,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                item {
                    Spacer(Modifier.height(16.dp))
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceContainerHighest,
                        shape = RoundedCornerShape(12.dp),
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                "Pattern Reference",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                            )
                            Spacer(Modifier.height(8.dp))
                            listOf(
                                "* matches zero or more characters",
                                "Tool patterns match tool names (e.g. runCommand, readFile)",
                                "Arg patterns match arguments JSON string (e.g. *rm *)",
                                "Rules: last matching rule wins",
                            ).forEach { tip ->
                                Row(verticalAlignment = Alignment.Top) {
                                    Text("  •  ", style = MaterialTheme.typography.bodySmall)
                                    Text(tip, style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                    }
                }
            }

            Surface(
                color = MaterialTheme.colorScheme.surfaceContainerLow,
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Outlined.Info,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "Permission rules are applied in real-time when tools execute.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
