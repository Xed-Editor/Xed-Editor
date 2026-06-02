@file:OptIn(ExperimentalUuidApi::class)
package com.rk.ai.nativeagent.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
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
import kotlin.uuid.ExperimentalUuidApi
import androidx.compose.ui.unit.dp
import com.rk.ai.nativeagent.engine.VibeCodingEngine
import com.rk.ai.nativeagent.ui.components.*

@Composable
fun VibeCodingPanel(
    engine: VibeCodingEngine,
    modifier: Modifier = Modifier,
) {
    val state by engine.state.collectAsState()
    val colorScheme = MaterialTheme.colorScheme
    var showSettings by remember { mutableStateOf(false) }
    var showHistory by remember { mutableStateOf(false) }
    var showFiles by remember { mutableStateOf(false) }
    var showAgentPanel by remember { mutableStateOf(false) }

    Box(modifier = modifier.fillMaxSize()) {
        Row(modifier = Modifier.fillMaxSize()) {
            AnimatedVisibility(
                visible = showFiles,
                enter = slideInHorizontally { -it },
                exit = slideOutHorizontally { -it },
            ) {
                VibeCodingFileTreeSidebar(
                    ideService = engine.ideService,
                    workspacePath = "/",
                    onOpenFile = { path -> engine.openFileInEditor(path) },
                    onDismiss = { showFiles = false },
                    modifier = Modifier
                        .width(260.dp)
                        .fillMaxHeight(),
                )
            }

            Column(modifier = Modifier.fillMaxSize()) {
                // Top toolbar
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = colorScheme.surfaceContainerLow,
                    tonalElevation = 0.5.dp,
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            val settings by engine.settingsStore.settingsFlow.collectAsState()
                            val modelName = remember(settings.chatModelId, settings.providers) {
                                val model = settings.providers.flatMap { it.models }
                                    .firstOrNull { it.id == settings.chatModelId }
                                model?.displayName?.ifEmpty { model.modelId } ?: "No model"
                            }

                            Text(
                                text = modelName,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Medium,
                                color = colorScheme.primary,
                            )
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            FilledTonalIconButton(
                                onClick = { showFiles = !showFiles },
                                modifier = Modifier.size(28.dp),
                            ) {
                                Icon(
                                    Icons.Outlined.Code,
                                    contentDescription = "Files",
                                    modifier = Modifier.size(14.dp),
                                )
                            }
                            FilledTonalIconButton(
                                onClick = { showHistory = !showHistory },
                                modifier = Modifier.size(28.dp),
                            ) {
                                Icon(
                                    Icons.Outlined.List,
                                    contentDescription = "History",
                                    modifier = Modifier.size(14.dp),
                                )
                            }
                            if (state.agentActivities.isNotEmpty()) {
                                FilledTonalIconButton(
                                    onClick = { showAgentPanel = !showAgentPanel },
                                    modifier = Modifier.size(28.dp),
                                ) {
                                    Icon(
                                        Icons.Outlined.Psychology,
                                        contentDescription = "Agents",
                                        modifier = Modifier.size(14.dp),
                                    )
                                }
                            }
                            FilledTonalIconButton(
                                onClick = { showSettings = true },
                                modifier = Modifier.size(28.dp),
                            ) {
                                Icon(
                                    Icons.Outlined.Settings,
                                    contentDescription = "Settings",
                                    modifier = Modifier.size(14.dp),
                                )
                            }
                            FilledTonalIconButton(
                                onClick = { engine.clearConversation() },
                                modifier = Modifier.size(28.dp),
                                enabled = state.messages.isNotEmpty(),
                            ) {
                                Icon(
                                    Icons.Outlined.Delete,
                                    contentDescription = "Clear",
                                    modifier = Modifier.size(14.dp),
                                )
                            }
                        }
                    }
                }

                // Security alerts
                if (state.hasSecurityAlerts) {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 160.dp),
                    ) {
                        items(state.securityAlerts.takeLast(3)) { alert ->
                            SecurityAlertBanner(
                                alert = alert,
                                onDismiss = { /* engine handles dismissal */ },
                            )
                        }
                    }
                }

                // Agent activity panel (collapsible)
                AnimatedVisibility(
                    visible = showAgentPanel && state.agentActivities.isNotEmpty(),
                    enter = fadeIn(),
                    exit = fadeOut(),
                ) {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 200.dp)
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        items(state.agentActivities.takeLast(5)) { activity ->
                            AgentActivityCard(activity = activity)
                        }
                    }
                }

                // Main message list
                VibeCodingMessageList(
                    messages = state.messages,
                    isProcessing = state.isProcessing,
                    onApproveTool = { toolCallId -> engine.approveTool(toolCallId) },
                    onDenyTool = { toolCallId, reason -> engine.denyTool(toolCallId, reason) },
                    onAnswerTool = { toolCallId, answer -> engine.answerTool(toolCallId, answer) },
                    modifier = Modifier.weight(1f),
                )

                // Error banner
                AnimatedVisibility(
                    visible = state.error != null,
                    enter = fadeIn(),
                    exit = fadeOut(),
                ) {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        color = colorScheme.errorContainer.copy(alpha = 0.8f),
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = state.error ?: "",
                                style = MaterialTheme.typography.bodySmall,
                                color = colorScheme.onErrorContainer,
                                modifier = Modifier.weight(1f),
                            )
                            IconButton(
                                onClick = { engine.clearError() },
                                modifier = Modifier.size(20.dp),
                            ) {
                                Icon(
                                    Icons.Outlined.Close,
                                    contentDescription = "Dismiss",
                                    modifier = Modifier.size(14.dp),
                                )
                            }
                        }
                    }
                }

                // Status bar
                VibeCodingStatusBar(
                    state = state,
                    modifier = Modifier.padding(horizontal = 4.dp),
                )

                // Input area
                VibeCodingInput(
                    isProcessing = state.isProcessing,
                    onSend = { text, parts -> engine.sendMessage(text, parts) },
                    onStop = { engine.stopGeneration() },
                )
            }
        }

        // History sidebar
        AnimatedVisibility(
            visible = showHistory,
            enter = slideInHorizontally { it },
            exit = slideOutHorizontally { it },
        ) {
            VibeCodingConversationSidebar(
                conversationRepo = engine.generationHandler.conversationRepo,
                currentConversationId = state.currentConversationId,
                assistantId = engine.getCurrentAssistantId(),
                onSelectConversation = { conversation ->
                    engine.loadConversation(conversation)
                    showHistory = false
                },
                onDismiss = { showHistory = false },
                modifier = Modifier
                    .width(260.dp)
                    .fillMaxHeight(),
            )
        }
    }

    if (showSettings) {
        VibeCodingSettingsSheet(
            engine = engine,
            onDismiss = { showSettings = false },
        )
    }
}
