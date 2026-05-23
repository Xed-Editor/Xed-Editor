package com.rk.ai.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.rk.ai.resolvedConfiguredModelForAgent
import com.rk.ai.session.AiSessionManager
import com.rk.settings.Settings

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiSettingsScreen(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var showApiKey by remember { mutableStateOf(false) }
    var apiKey by remember { mutableStateOf(Settings.ai_api_key) }
    var completionUrl by remember { mutableStateOf(Settings.ai_completion_url) }
    var completionModel by remember { mutableStateOf(Settings.ai_completion_model) }
    var aiAutoApply by remember { mutableStateOf(Settings.ai_auto_apply) }
    var inlineCompletion by remember { mutableStateOf(Settings.ai_inline_completion) }
    var projectConfigEnabled by remember { mutableStateOf(Settings.ai_project_config_enabled) }
    val currentAgent = AiSessionManager.currentAgent
    val configuredModel = resolvedConfiguredModelForAgent(currentAgent).orEmpty()
    val availableAgents = AiSessionManager.availableAgents()

    Surface(modifier = modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            TopAppBar(
                title = { Text("AI Settings") },
                navigationIcon = {
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Outlined.Close, "Close")
                    }
                },
            )

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Card {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Agent Configuration", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(12.dp))

                        Text("Active Agent", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(4.dp))
                        Text(currentAgent.displayName, style = MaterialTheme.typography.bodyLarge)
                        if (configuredModel.isNotBlank()) {
                            Text("Model: $configuredModel", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Spacer(Modifier.height(8.dp))

                        availableAgents.forEach { agent ->
                            val isActive = agent == currentAgent
                            Surface(
                                onClick = { AiSessionManager.switchAgent(agent.name) },
                                shape = RoundedCornerShape(8.dp),
                                color = if (isActive) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    RadioButton(selected = isActive, onClick = { AiSessionManager.switchAgent(agent.name) })
                                    Spacer(Modifier.width(8.dp))
                                    Column {
                                        Text(agent.displayName, style = MaterialTheme.typography.bodyMedium)
                                        Text(agent.defaultModel, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                            }
                        }
                    }
                }

                Card {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("API Configuration", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(12.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            OutlinedTextField(
                                value = apiKey,
                                onValueChange = { apiKey = it; Settings.ai_api_key = it },
                                modifier = Modifier.weight(1f),
                                label = { Text("API Key") },
                                singleLine = true,
                                visualTransformation = if (showApiKey) VisualTransformation.None else PasswordVisualTransformation(),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                            )
                            IconButton(onClick = { showApiKey = !showApiKey }) {
                                Icon(if (showApiKey) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility, "Toggle API key visibility")
                            }
                        }
                        Spacer(Modifier.height(8.dp))

                        OutlinedTextField(
                            value = completionUrl,
                            onValueChange = { completionUrl = it; Settings.ai_completion_url = it },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("Custom API URL") },
                            placeholder = { Text("https://api.openai.com/v1") },
                            singleLine = true,
                            supportingText = { Text("Leave empty for default provider URL") },
                        )
                        Spacer(Modifier.height(8.dp))

                        OutlinedTextField(
                            value = completionModel,
                            onValueChange = { completionModel = it; Settings.ai_completion_model = it },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("Completion Model") },
                            placeholder = { Text("Default model for inline completions") },
                            singleLine = true,
                        )
                    }
                }

                Card {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Features", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(12.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Inline Completion", style = MaterialTheme.typography.bodyMedium)
                                Text("AI code suggestions while typing", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Switch(checked = inlineCompletion, onCheckedChange = { inlineCompletion = it; Settings.ai_inline_completion = it })
                        }

                        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Auto-Apply AI Changes", style = MaterialTheme.typography.bodyMedium)
                                Text("Auto-apply without review", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Switch(checked = aiAutoApply, onCheckedChange = { aiAutoApply = it; Settings.ai_auto_apply = it })
                        }

                        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Project Config", style = MaterialTheme.typography.bodyMedium)
                                Text("Respect .xed/agent.json", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Switch(checked = projectConfigEnabled, onCheckedChange = { projectConfigEnabled = it; Settings.ai_project_config_enabled = it })
                        }
                    }
                }

                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF3E0)),
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Outlined.Info, contentDescription = null, tint = Color(0xFFE65100))
                            Spacer(Modifier.width(8.dp))
                            Text("Tips", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = Color(0xFFE65100))
                        }
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = "• For Gemini: GEMINI_API_KEY or GOOGLE_API_KEY env vars\n" +
                                "• For OpenAI: OPENAI_API_KEY env var\n" +
                                "• For OpenRouter: use OPENROUTER_API_KEY\n" +
                                "• Set a custom URL to use a different provider\n" +
                                "• API Keys set here override environment variables",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFF4E342E),
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun AiDiagnosticCard(modifier: Modifier = Modifier) {
    val currentAgent = AiSessionManager.currentAgent
    val connectionStatus = AiSessionManager.connectionStatus
    val resolvedModel = resolvedConfiguredModelForAgent(currentAgent).orEmpty()
    val bridgeRunning = com.rk.ai.IdeBridge.isRunning()

    Card(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("AI Diagnostics", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))

            DiagnosticRow("Agent", currentAgent.displayName)
            DiagnosticRow("Model", resolvedModel.ifBlank { "Default" })
            DiagnosticRow("Status", connectionStatus.name)
            DiagnosticRow("Bridge", if (bridgeRunning) "Running" else "Stopped")
            DiagnosticRow("Clients", "${com.rk.ai.IdeBridge.connectedClients()}")
            DiagnosticRow("Tools", "${com.rk.ai.IdeBridge.availableTools()}")

            val lastError = com.rk.ai.session.AiSessionManager.lastError
            if (lastError != null) {
                Spacer(Modifier.height(8.dp))
                Surface(color = Color(0xFFFFEBEE), shape = RoundedCornerShape(4.dp)) {
                    Text(
                        "Last Error: $lastError",
                        color = Color(0xFFC62828),
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(8.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun DiagnosticRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
    }
}
