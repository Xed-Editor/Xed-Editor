@file:OptIn(ExperimentalUuidApi::class)
package com.rk.ai.nativeagent.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.rk.ai.nativeagent.engine.AgentActivity
import com.rk.ai.nativeagent.engine.VibeCodingEngine
import com.rk.ai.nativeagent.engine.VibeCodingState
import com.rk.ai.nativeagent.ui.components.SecurityAlertBanner
import kotlin.uuid.ExperimentalUuidApi

@Composable
internal fun VibeCodingContentStack(
    state: VibeCodingState,
    engine: VibeCodingEngine,
    colorScheme: ColorScheme,
    context: android.content.Context,
    hasTodos: Boolean,
    showAgentActivity: Boolean,
    onClearTodos: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        // Security alerts
        if (state.hasSecurityAlerts) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 120.dp),
            ) {
                items(
                    state.securityAlerts.takeLast(3),
                    key = { it.id ?: it.message.take(50) },
                ) { alert ->
                    SecurityAlertBanner(
                        alert = alert,
                        onDismiss = { engine.dismissSecurityAlert(alert.id) },
                    )
                }
            }
        }

        // Todo panel
        VibeCodingTodoPanel(
            visible = hasTodos,
            todos = state.todos,
            completedCount = state.completedTodos,
            onClear = onClearTodos,
            colorScheme = colorScheme,
        )

        // Agent activity
        AnimatedVisibility(
            visible = showAgentActivity && state.agentActivities.isNotEmpty(),
            enter = slideInVertically { -it } + fadeIn(),
            exit = slideOutVertically { -it } + fadeOut(),
        ) {
            AgentActivitySection(
                activities = state.agentActivities,
                colorScheme = colorScheme,
            )
        }

        // Main message list
        Box(modifier = Modifier.weight(1f)) {
            if (state.messages.isEmpty() && !state.isProcessing) {
                VibeCodingEmptyState(
                    colorScheme = colorScheme,
                    workspacePath = state.workspacePath,
                    onQuickAction = { prompt -> engine.sendMessage(prompt) },
                )
            } else {
                VibeCodingMessageList(
                    messages = state.messages,
                    isProcessing = state.isProcessing,
                    currentPhase = state.currentPhase,
                    onApproveTool = { toolCallId -> engine.approveTool(toolCallId) },
                    onDenyTool = { toolCallId, reason -> engine.denyTool(toolCallId, reason) },
                    onAnswerTool = { toolCallId, answer -> engine.answerTool(toolCallId, answer) },
                    onCopyMessage = { text ->
                        val cm = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                        cm.setPrimaryClip(android.content.ClipData.newPlainText("VibeCoding", text))
                    },
                    onDeleteMessage = { index -> engine.deleteMessage(index) },
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}

@Composable
internal fun AgentActivitySection(
    activities: List<AgentActivity>,
    colorScheme: ColorScheme,
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 160.dp)
            .padding(horizontal = 8.dp, vertical = 2.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        items(activities.takeLast(5)) { activity ->
            com.rk.ai.nativeagent.ui.components.AgentActivityCard(activity = activity)
        }
    }
}
