package com.rk.ai.nativeagent.ui.panels

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rk.ai.agent.planner.TaskNode
import com.rk.ai.agent.planner.TaskStatus
import com.rk.ai.agent.planner.TaskTree

@Composable
fun ExecutionGraphPanel(
    taskTree: TaskTree?,
    modifier: Modifier = Modifier,
) {
    if (taskTree == null) return

    val colorScheme = MaterialTheme.colorScheme

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = colorScheme.surfaceContainerLow),
    ) {
        Column(
            modifier = Modifier
                .padding(12.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Execution Plan",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = "${taskTree.completedCount}/${taskTree.totalCount} (${(taskTree.progress * 100).toInt()}%)",
                    style = MaterialTheme.typography.labelSmall,
                    fontSize = 10.sp,
                    color = colorScheme.onSurfaceVariant,
                )
            }

            Spacer(Modifier.height(2.dp))

            Text(
                text = taskTree.goal,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Medium,
                color = colorScheme.primary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )

            Spacer(Modifier.height(8.dp))

            if (taskTree.rootTasks.isNotEmpty()) {
                taskTree.rootTasks.forEach { node ->
                    TaskNodeView(node = node, depth = 0)
                }
            } else {
                Text(
                    text = "No tasks in plan",
                    style = MaterialTheme.typography.bodySmall,
                    color = colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun TaskNodeView(node: TaskNode, depth: Int) {
    val colorScheme = MaterialTheme.colorScheme

    val icon = when (node.status) {
        TaskStatus.COMPLETED -> "\u2705"
        TaskStatus.IN_PROGRESS -> "\uD83D\uDD35"
        TaskStatus.FAILED -> "\u274C"
        TaskStatus.BLOCKED -> "\u23F8\uFE0F"
        TaskStatus.SKIPPED -> "\u2796"
        TaskStatus.PENDING -> "\u23F3"
    }

    val statusColor = when (node.status) {
        TaskStatus.COMPLETED -> colorScheme.primary
        TaskStatus.IN_PROGRESS -> colorScheme.tertiary
        TaskStatus.FAILED -> colorScheme.error
        TaskStatus.BLOCKED -> colorScheme.error
        TaskStatus.SKIPPED -> colorScheme.outlineVariant
        TaskStatus.PENDING -> colorScheme.onSurfaceVariant
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = (depth * 16).dp, top = 2.dp, bottom = 2.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 1.dp),
        ) {
            Text(text = icon, fontSize = 11.sp)
            Spacer(Modifier.width(4.dp))
            Text(
                text = node.title,
                style = MaterialTheme.typography.bodySmall,
                fontSize = 11.sp,
                fontWeight = if (node.status == TaskStatus.IN_PROGRESS) FontWeight.SemiBold else FontWeight.Normal,
                color = statusColor,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }

        if (node.error != null) {
            Text(
                text = node.error,
                style = MaterialTheme.typography.labelSmall,
                fontSize = 9.sp,
                color = colorScheme.error,
                modifier = Modifier.padding(start = 20.dp),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }

        node.subtasks.forEach { subtask ->
            TaskNodeView(node = subtask, depth = depth + 1)
        }
    }
}
