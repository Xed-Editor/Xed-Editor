package com.rk.ai.nativeagent.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
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

    Column(modifier = modifier.fillMaxSize()) {
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
                    val modelName = settings.providers
                        .flatMap { it.models }
                        .firstOrNull { it.id == settings.chatModelId }
                        ?.displayName
                        ?.ifEmpty { settings.providers.flatMap { it.models }.firstOrNull { it.id == settings.chatModelId }?.modelId }
                        ?: "No model"

                    Text(
                        text = modelName,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Medium,
                        color = colorScheme.primary,
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
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
                            Icons.Outlined.Delete,
                            contentDescription = "Dismiss",
                            modifier = Modifier.size(14.dp),
                        )
                    }
                }
            }
        }

        VibeCodingInput(
            isProcessing = state.isProcessing,
            onSend = { engine.sendMessage(it) },
        )
    }

    if (showSettings) {
        VibeCodingSettingsSheet(
            engine = engine,
            onDismiss = { showSettings = false },
        )
    }
}
