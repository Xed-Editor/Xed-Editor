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
import com.rk.ai.nativeagent.engine.VibeCodingEngine
import com.rk.ai.persistence.settings.getCurrentAssistant
import kotlin.uuid.ExperimentalUuidApi
import kotlinx.coroutines.launch

@Composable
internal fun AssistantSection(
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
