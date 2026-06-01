@file:OptIn(ExperimentalUuidApi::class)
package com.rk.ai.nativeagent.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.List
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import kotlin.uuid.ExperimentalUuidApi
import androidx.compose.ui.unit.dp
import com.rk.ai.nativeagent.engine.VibeCodingEngine

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

    Box(modifier = modifier.fillMaxSize()) {
        Row(modifier = Modifier.fillMaxSize()) {
            // File tree sidebar (left)
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

            VibeCodingMessageList(
                messages = state.messages,
                isProcessing = state.isProcessing,
                onApproveTool = { toolCallId -> engine.approveTool(toolCallId) },
                onDenyTool = { toolCallId, reason -> engine.denyTool(toolCallId, reason) },
                onAnswerTool = { toolCallId, answer -> engine.answerTool(toolCallId, answer) },
                modifier = Modifier.weight(1f),
            )

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

            VibeCodingInput(
                isProcessing = state.isProcessing,
                onSend = { text, parts -> engine.sendMessage(text, parts) },
                onStop = { engine.stopGeneration() },
            )
        }

        // History sidebar overlay
        AnimatedVisibility(
            visible = showHistory,
            enter = slideInHorizontally { it },
            exit = slideOutHorizontally { it },
        ) {
            VibeCodingConversationSidebar(
                conversationRepo = engine.generationHandler.conversationRepo,
                currentConversationId = null,
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
}
