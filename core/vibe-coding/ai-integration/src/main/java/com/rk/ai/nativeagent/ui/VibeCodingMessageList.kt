@file:OptIn(ExperimentalUuidApi::class)
package com.rk.ai.nativeagent.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rk.ai.agent.executor.AgentPhase
import com.rk.ai.models.UIMessage
import kotlin.uuid.ExperimentalUuidApi

@Composable
fun VibeCodingMessageList(
    messages: List<UIMessage>,
    isProcessing: Boolean,
    currentPhase: AgentPhase = AgentPhase.IDLE,
    onApproveTool: ((String) -> Unit)? = null,
    onDenyTool: ((String, String) -> Unit)? = null,
    onAnswerTool: ((String, String) -> Unit)? = null,
    onCopyMessage: ((String) -> Unit)? = null,
    onDeleteMessage: ((Int) -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()

    // Smart auto-scroll: only scroll to bottom if user is already near the bottom
    val isNearBottom by remember {
        derivedStateOf {
            val layoutInfo = listState.layoutInfo
            val lastVisible = layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            val totalItems = layoutInfo.totalItemsCount
            lastVisible >= totalItems - 3
        }
    }

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty() && isNearBottom) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    when {
        messages.isEmpty() && !isProcessing -> EmptyState(modifier)
        messages.isEmpty() && isProcessing -> LoadingState(modifier)
        else ->
            MessageListContent(
                messages = messages,
                isProcessing = isProcessing,
                currentPhase = currentPhase,
                listState = listState,
                onApproveTool = onApproveTool,
                onDenyTool = onDenyTool,
                onAnswerTool = onAnswerTool,
                onCopyMessage = onCopyMessage,
                onDeleteMessage = onDeleteMessage,
                modifier = modifier,
            )
    }
}

@Composable
private fun EmptyState(modifier: Modifier) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "VibeCoding",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "Ask me anything about your code",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f),
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun LoadingState(modifier: Modifier) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator(modifier = Modifier.size(24.dp))
    }
}

@Composable
private fun MessageListContent(
    messages: List<UIMessage>,
    isProcessing: Boolean,
    currentPhase: AgentPhase,
    listState: androidx.compose.foundation.lazy.LazyListState,
    onApproveTool: ((String) -> Unit)? = null,
    onDenyTool: ((String, String) -> Unit)? = null,
    onAnswerTool: ((String, String) -> Unit)? = null,
    onCopyMessage: ((String) -> Unit)? = null,
    onDeleteMessage: ((Int) -> Unit)? = null,
    modifier: Modifier,
) {
    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(vertical = 4.dp),
    ) {
        items(messages, key = { it.id }) { message ->
            val msgIndex = messages.indexOf(message)
            VibeCodingMessageBubble(
                message = message,
                onApproveTool = onApproveTool,
                onDenyTool = onDenyTool,
                onAnswerTool = onAnswerTool,
                onCopy = onCopyMessage,
                onDelete = if (msgIndex >= 0 && onDeleteMessage != null) {
                    { onDeleteMessage(msgIndex) }
                } else null,
            )
        }

        if (isProcessing) {
            item {
                // Thinking indicator with phase
                ThinkingIndicator(
                    phase = currentPhase,
                    toolCount = messages.flatMap { it.getTools() }.count { !it.isExecuted },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
                )
            }
        }
    }
}

@Composable
private fun ThinkingIndicator(
    phase: AgentPhase,
    toolCount: Int,
    modifier: Modifier = Modifier,
) {
    val infiniteTransition = rememberInfiniteTransition(label = "thinking")
    val dotsAlpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = EaseInOutCubic),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "dotsAlpha",
    )

    val phaseLabel =
        when (phase) {
            AgentPhase.IDLE -> "Thinking"
            AgentPhase.PLANNING -> "Planning"
            AgentPhase.ANALYZING -> "Analyzing"
            AgentPhase.INDEXING -> "Indexing"
            AgentPhase.EXPLORING -> "Exploring"
            AgentPhase.EXECUTING -> "Working"
            AgentPhase.VERIFYING -> "Verifying"
            AgentPhase.REVIEWING -> "Reviewing"
            AgentPhase.TESTING -> "Testing"
            AgentPhase.COMPLETED -> "Done"
            AgentPhase.FAILED -> "Failed"
        }

    val phaseColor =
        when (phase) {
            AgentPhase.PLANNING -> MaterialTheme.colorScheme.tertiary
            AgentPhase.ANALYZING, AgentPhase.INDEXING -> MaterialTheme.colorScheme.secondary
            AgentPhase.EXECUTING -> MaterialTheme.colorScheme.primary
            AgentPhase.VERIFYING, AgentPhase.TESTING -> MaterialTheme.colorScheme.error
            else -> MaterialTheme.colorScheme.onSurfaceVariant
        }

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.6f),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // Phase badge
            Surface(
                shape = RoundedCornerShape(4.dp),
                color = phaseColor.copy(alpha = 0.15f),
            ) {
                Text(
                    text = phaseLabel,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = phaseColor,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                    fontSize = 10.sp,
                )
            }

            // Animated dots
            Row(
                horizontalArrangement = Arrangement.spacedBy(3.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                repeat(3) { index ->
                    Box(
                        modifier =
                            Modifier
                                .size(4.dp)
                                .alpha(
                                    when (index) {
                                        0 -> dotsAlpha
                                        1 -> (dotsAlpha + 0.3f).coerceAtMost(1f)
                                        else -> (dotsAlpha + 0.6f).coerceAtMost(1f)
                                    },
                                )
                                .background(
                                    color = phaseColor,
                                    shape = RoundedCornerShape(2.dp),
                                ),
                    )
                }
            }

            if (toolCount > 0) {
                Spacer(Modifier.width(4.dp))
                Text(
                    text = "$toolCount tool${if (toolCount != 1) "s" else ""}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                    fontSize = 9.sp,
                )
            }
        }
    }
}
