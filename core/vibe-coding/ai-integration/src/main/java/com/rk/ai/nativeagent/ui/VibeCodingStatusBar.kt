package com.rk.ai.nativeagent.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rk.ai.agent.executor.AgentPhase
import com.rk.ai.nativeagent.engine.VibeCodingState

@Composable
fun VibeCodingStatusBar(
    state: VibeCodingState,
    modifier: Modifier = Modifier,
) {
    val colorScheme = MaterialTheme.colorScheme
    val phaseColor = Color(state.phaseColor)
    val isActive = state.isAgentActive

    // Pulsing animation for active phases
    val infiniteTransition = rememberInfiniteTransition(label = "statusPulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.6f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = EaseInOutCubic),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "statusPulse",
    )

    Surface(
        modifier = modifier.fillMaxWidth(),
        color = colorScheme.surfaceContainerHigh,
        tonalElevation = 1.dp,
        shape = RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            // Left: Phase indicator
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(7.dp)
                        .clip(CircleShape)
                        .background(
                            if (isActive) phaseColor.copy(alpha = pulseAlpha)
                            else phaseColor.copy(alpha = 0.6f)
                        ),
                )
                Text(
                    text = state.phaseLabel,
                    style = MaterialTheme.typography.labelSmall,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (isActive) phaseColor else phaseColor.copy(alpha = 0.6f),
                )

                if (state.isProcessing) {
                    Text(
                        text = "•",
                        style = MaterialTheme.typography.labelSmall,
                        fontSize = 8.sp,
                        color = colorScheme.primary.copy(alpha = pulseAlpha),
                    )
                    Text(
                        text = "processing",
                        style = MaterialTheme.typography.labelSmall,
                        fontSize = 9.sp,
                        color = colorScheme.onSurfaceVariant.copy(alpha = pulseAlpha),
                    )
                }
            }

            // Right: Stats
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                // Task progress
                if (state.taskTree != null) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Outlined.Checklist,
                            contentDescription = null,
                            modifier = Modifier.size(10.dp),
                            tint = colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                        )
                        Spacer(Modifier.width(2.dp))
                        Text(
                            text = "${state.taskTree.completedCount}/${state.taskTree.totalCount}",
                            style = MaterialTheme.typography.labelSmall,
                            fontSize = 9.sp,
                            color = colorScheme.onSurfaceVariant,
                        )
                    }
                }

                // Tool executions
                if (state.toolExecutions.isNotEmpty()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Outlined.Build,
                            contentDescription = null,
                            modifier = Modifier.size(10.dp),
                            tint = colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                        )
                        Spacer(Modifier.width(2.dp))
                        Text(
                            text = "${state.toolExecutions.size}",
                            style = MaterialTheme.typography.labelSmall,
                            fontSize = 9.sp,
                            color = colorScheme.onSurfaceVariant,
                        )
                    }
                }

                // Todos
                if (state.todos.isNotEmpty()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Outlined.TaskAlt,
                            contentDescription = null,
                            modifier = Modifier.size(10.dp),
                            tint = if (state.completedTodos == state.todos.size)
                                colorScheme.primary else colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                        )
                        Spacer(Modifier.width(2.dp))
                        Text(
                            text = "${state.completedTodos}/${state.todos.size}",
                            style = MaterialTheme.typography.labelSmall,
                            fontSize = 9.sp,
                            color = colorScheme.onSurfaceVariant,
                        )
                    }
                }

                // Index status
                Surface(
                    shape = RoundedCornerShape(3.dp),
                    color = if (state.projectIndexed)
                        colorScheme.primaryContainer.copy(alpha = 0.3f)
                    else colorScheme.errorContainer.copy(alpha = 0.3f),
                ) {
                    Text(
                        text = if (state.projectIndexed) "idx" else "no-idx",
                        style = MaterialTheme.typography.labelSmall,
                        fontSize = 8.sp,
                        fontFamily = FontFamily.Monospace,
                        color = if (state.projectIndexed) colorScheme.primary else colorScheme.error,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
                    )
                }

                // Context tokens
                if (state.contextTokens != null) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Outlined.Memory,
                            contentDescription = null,
                            modifier = Modifier.size(9.dp),
                            tint = colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                        )
                        Spacer(Modifier.width(2.dp))
                        Text(
                            text = "${state.contextTokens}",
                            style = MaterialTheme.typography.labelSmall,
                            fontSize = 9.sp,
                            color = colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        )
                    }
                }

                // Modified files
                if (state.modifiedFiles.isNotEmpty()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Outlined.Edit,
                            contentDescription = null,
                            modifier = Modifier.size(9.dp),
                            tint = colorScheme.secondary.copy(alpha = 0.5f),
                        )
                        Spacer(Modifier.width(2.dp))
                        Text(
                            text = "${state.modifiedFiles.size}",
                            style = MaterialTheme.typography.labelSmall,
                            fontSize = 9.sp,
                            color = colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}
