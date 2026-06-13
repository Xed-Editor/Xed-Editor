package com.rk.ai.nativeagent.ui.panels

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rk.ai.agent.executor.AgentPhase

@Composable
fun FileChangePanel(
    modifiedFiles: List<String>,
    currentPhase: AgentPhase,
    modifier: Modifier = Modifier,
) {
    if (modifiedFiles.isEmpty()) return

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
            ) {
                Text(
                    text = "Modified Files",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = "${modifiedFiles.size} files",
                    style = MaterialTheme.typography.labelSmall,
                    fontSize = 10.sp,
                    color = colorScheme.onSurfaceVariant,
                )
            }

            Spacer(Modifier.height(8.dp))

            modifiedFiles.distinct().take(10).forEach { file ->
                val fileName = file.split("/").last()
                val dir = file.split("/").dropLast(1).lastOrNull() ?: ""
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 2.dp),
                ) {
                    Text(
                        text = "\uD83D\uDCC4",
                        fontSize = 11.sp,
                    )
                    Spacer(Modifier.width(6.dp))
                    Column {
                        Text(
                            text = fileName,
                            style = MaterialTheme.typography.bodySmall,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Medium,
                        )
                        if (dir.isNotBlank()) {
                            Text(
                                text = dir,
                                style = MaterialTheme.typography.labelSmall,
                                fontSize = 9.sp,
                                color = colorScheme.onSurfaceVariant,
                                fontFamily = FontFamily.Monospace,
                            )
                        }
                    }
                }
            }
        }
    }
}
