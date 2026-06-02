package com.rk.ai.nativeagent.ui.components

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rk.ai.nativeagent.engine.AgentActivity
import com.rk.ai.nativeagent.engine.AgentActivityStatus

@Composable
fun AgentActivityCard(
    activity: AgentActivity,
    modifier: Modifier = Modifier,
) {
    val colorScheme = MaterialTheme.colorScheme
    val statusColor = when (activity.status) {
        AgentActivityStatus.RUNNING -> colorScheme.primary
        AgentActivityStatus.COMPLETED -> colorScheme.tertiary
        AgentActivityStatus.FAILED -> colorScheme.error
        AgentActivityStatus.PENDING -> colorScheme.outlineVariant
    }
    val statusText = when (activity.status) {
        AgentActivityStatus.RUNNING -> "Running..."
        AgentActivityStatus.COMPLETED -> "Completed"
        AgentActivityStatus.FAILED -> "Failed"
        AgentActivityStatus.PENDING -> "Pending"
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = colorScheme.surfaceContainerLow,
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .offset(y = 4.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(statusColor),
            )
            Spacer(Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = activity.agentName,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = activity.task.take(200),
                    style = MaterialTheme.typography.bodySmall,
                    color = colorScheme.onSurfaceVariant,
                    maxLines = 3,
                )
                Spacer(Modifier.height(4.dp))
                Row(
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = statusText,
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp,
                        color = statusColor,
                    )
                    if (activity.completedAt != null) {
                        val elapsed = (activity.completedAt - activity.startedAt) / 1000
                        Text(
                            text = "${elapsed}s",
                            style = MaterialTheme.typography.labelSmall,
                            fontSize = 10.sp,
                            color = colorScheme.onSurfaceVariant,
                        )
                    }
                }
                if (activity.result != null) {
                    Spacer(Modifier.height(4.dp))
                    val resultText = when (val r = activity.result) {
                        is com.rk.ai.agent.agents.AgentResult.Success ->
                            r.summary.ifEmpty { r.output.take(100) }
                        is com.rk.ai.agent.agents.AgentResult.Failure ->
                            "Error: ${r.error}"
                        else -> ""
                    }
                    if (resultText.isNotBlank()) {
                        Text(
                            text = resultText,
                            style = MaterialTheme.typography.bodySmall,
                            fontSize = 11.sp,
                            color = when (activity.result) {
                                is com.rk.ai.agent.agents.AgentResult.Failure -> colorScheme.error
                                else -> colorScheme.onSurfaceVariant
                            },
                        )
                    }
                }
            }
        }
    }
}
