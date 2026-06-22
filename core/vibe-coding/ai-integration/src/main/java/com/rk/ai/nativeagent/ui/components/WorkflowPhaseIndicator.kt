package com.rk.ai.nativeagent.ui.components

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

data class WorkflowPhase(
    val name: String,
    val description: String,
    val isActive: Boolean = false,
    val isComplete: Boolean = false,
)

@Composable
fun WorkflowPhaseIndicator(
    phases: List<WorkflowPhase>,
    currentPhase: String?,
    modifier: Modifier = Modifier,
) {
    val colorScheme = MaterialTheme.colorScheme

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = colorScheme.surfaceContainerLow,
        ),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
        ) {
            Text(
                text = "Workflow Progress",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = colorScheme.onSurface,
            )
            Spacer(Modifier.height(12.dp))
            phases.forEachIndexed { index, phase ->
                val circleColor = when {
                    phase.isComplete -> colorScheme.tertiary
                    phase.isActive -> colorScheme.primary
                    else -> colorScheme.outlineVariant
                }
                val lineColor = when {
                    phase.isComplete -> colorScheme.tertiary.copy(alpha = 0.3f)
                    else -> colorScheme.outlineVariant.copy(alpha = 0.3f)
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.Top,
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.width(20.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(20.dp)
                                .clip(CircleShape)
                                .background(circleColor),
                            contentAlignment = Alignment.Center,
                        ) {
                            if (phase.isComplete) {
                                Text(
                                    text = "✓",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = colorScheme.onTertiary,
                                )
                            } else if (phase.isActive) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .clip(CircleShape)
                                        .background(colorScheme.onPrimary),
                                )
                            }
                        }
                        if (index < phases.lastIndex) {
                            Box(
                                modifier = Modifier
                                    .width(2.dp)
                                    .height(24.dp)
                                    .background(lineColor),
                            )
                        }
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = phase.name,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = if (phase.isActive) FontWeight.SemiBold else FontWeight.Normal,
                            color = when {
                                phase.isComplete -> colorScheme.onSurface
                                phase.isActive -> colorScheme.primary
                                else -> colorScheme.onSurfaceVariant
                            },
                            fontSize = 12.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (phase.isActive || phase.description.isNotBlank()) {
                            Text(
                                text = phase.description,
                                style = MaterialTheme.typography.labelSmall,
                                fontSize = 10.sp,
                                color = colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
        }
    }
}
