package com.rk.ai.nativeagent.ui.panels

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rk.ai.nativeagent.engine.ToolExecutionRecord

@Composable
fun ToolActivityPanel(
    toolExecutions: List<ToolExecutionRecord>,
    modifier: Modifier = Modifier,
) {
    if (toolExecutions.isEmpty()) return

    val colorScheme = MaterialTheme.colorScheme

    Card(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(max = 200.dp),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = colorScheme.surfaceContainerLow),
    ) {
        Column(modifier = Modifier.padding(8.dp)) {
            Text(
                text = "Tool Activity (${toolExecutions.size})",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
            )

            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(1.dp),
            ) {
                items(toolExecutions.takeLast(30).reversed()) { record ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 4.dp, vertical = 1.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            modifier = Modifier
                                .size(5.dp)
                                .clip(CircleShape)
                                .background(
                                    if (record.success) colorScheme.primary
                                    else colorScheme.error
                                ),
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = record.toolName,
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 10.sp,
                            modifier = Modifier.width(80.dp),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = "${record.durationMs}ms",
                            style = MaterialTheme.typography.labelSmall,
                            fontSize = 9.sp,
                            color = colorScheme.onSurfaceVariant,
                            modifier = Modifier.width(45.dp),
                        )
                        if (record.fromCache) {
                            Text(
                                text = "cached",
                                style = MaterialTheme.typography.labelSmall,
                                fontSize = 8.sp,
                                color = colorScheme.tertiary,
                                modifier = Modifier.width(40.dp),
                            )
                        }
                        Text(
                            text = if (record.tokens > 0) "${record.tokens}t" else "",
                            style = MaterialTheme.typography.labelSmall,
                            fontSize = 9.sp,
                            color = colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        )
                    }
                }
            }
        }
    }
}
