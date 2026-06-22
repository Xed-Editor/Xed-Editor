package com.rk.ai.nativeagent.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.rk.ai.agent.events.SessionTodo
import com.rk.ai.agent.events.SessionTodoStatus

@Composable
internal fun VibeCodingTodoPanel(
    visible: Boolean,
    todos: List<SessionTodo>,
    completedCount: Int,
    onClear: () -> Unit,
    colorScheme: ColorScheme,
) {
    AnimatedVisibility(
        visible = visible,
        enter = slideInVertically { -it } + fadeIn(),
        exit = slideOutVertically { -it } + fadeOut(),
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 2.dp),
            shape = RoundedCornerShape(8.dp),
            color = colorScheme.surfaceContainerHigh,
            tonalElevation = 2.dp,
        ) {
            Column(modifier = Modifier.padding(8.dp)) {
                // Header with progress
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            text = "Tasks ($completedCount/${todos.size})",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                        )
                        // Progress indicator
                        if (todos.isNotEmpty()) {
                            LinearProgressIndicator(
                                progress = { completedCount.toFloat() / todos.size },
                                modifier = Modifier.width(60.dp).height(4.dp),
                                color = colorScheme.primary,
                                trackColor = colorScheme.surfaceContainerHighest,
                                strokeCap = androidx.compose.ui.graphics.StrokeCap.Round,
                            )
                        }
                    }
                    TextButton(
                        onClick = onClear,
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                    ) {
                        Text("Clear", style = MaterialTheme.typography.labelSmall)
                    }
                }
                Spacer(Modifier.height(4.dp))

                // Todo items (max 5 visible)
                todos.take(5).forEach { todo ->
                    TodoItem(todo = todo, colorScheme = colorScheme)
                }
                if (todos.size > 5) {
                    Text(
                        text = "+${todos.size - 5} more",
                        style = MaterialTheme.typography.labelSmall,
                        color = colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier.padding(start = 20.dp, top = 2.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun TodoItem(
    todo: SessionTodo,
    colorScheme: ColorScheme,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
    ) {
        val icon = when (todo.status) {
            SessionTodoStatus.COMPLETED -> Icons.Outlined.CheckCircle
            SessionTodoStatus.IN_PROGRESS -> Icons.Outlined.PlayCircle
            SessionTodoStatus.CANCELLED -> Icons.Outlined.Cancel
            SessionTodoStatus.PENDING -> Icons.Outlined.RadioButtonUnchecked
        }
        Icon(
            icon,
            contentDescription = null,
            modifier = Modifier.size(14.dp),
            tint = when (todo.status) {
                SessionTodoStatus.COMPLETED -> colorScheme.primary
                SessionTodoStatus.IN_PROGRESS -> colorScheme.tertiary
                SessionTodoStatus.CANCELLED -> colorScheme.error
                SessionTodoStatus.PENDING -> colorScheme.onSurfaceVariant
            },
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = todo.description,
            style = MaterialTheme.typography.bodySmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
