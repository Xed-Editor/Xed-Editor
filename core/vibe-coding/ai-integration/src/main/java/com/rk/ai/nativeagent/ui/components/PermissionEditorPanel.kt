package com.rk.ai.nativeagent.ui.components

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

data class PermissionRule(
    val id: String,
    val tool: String,
    val pattern: String,
    val action: String, // allow, ask, deny
)

@Composable
fun PermissionEditorPanel(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var rules by remember {
        mutableStateOf(listOf(
            PermissionRule("1", "read", "*", "allow"),
            PermissionRule("2", "*.env", "*", "deny"),
            PermissionRule("3", "*.env.*", "*", "deny"),
            PermissionRule("4", "edit", "*", "allow"),
            PermissionRule("5", "bash", "git *", "allow"),
            PermissionRule("6", "bash", "npm *", "allow"),
            PermissionRule("7", "bash", "rm *", "deny"),
            PermissionRule("8", "external_directory", "*", "ask"),
            PermissionRule("9", "doom_loop", "*", "ask"),
        ))
    }

    val availableTools = listOf("read", "edit", "glob", "grep", "bash", "task", "skill", "lsp", "question", "webfetch", "websearch", "external_directory", "doom_loop")
    val availableActions = listOf("allow", "ask", "deny")
    val actionColors = mapOf(
        "allow" to MaterialTheme.colorScheme.primary,
        "ask" to MaterialTheme.colorScheme.tertiary,
        "deny" to MaterialTheme.colorScheme.error,
    )

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
                items(rules) { rule ->
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
                                            rule.tool,
                                            style = MaterialTheme.typography.labelSmall,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                        )
                                    }
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        rule.pattern,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                    )
                                }
                            }
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = (actionColors[rule.action] ?: MaterialTheme.colorScheme.primary)
                                    .copy(alpha = 0.15f),
                            ) {
                                Text(
                                    rule.action.uppercase(),
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = actionColors[rule.action] ?: MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                )
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
                                "? matches exactly one character",
                                "~ expands to home directory",
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
                        "Configure in opencode.json under the \"permission\" key",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
