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

data class ContextInfo(
    val currentGoal: String = "",
    val currentFile: String = "",
    val modifiedFiles: List<String> = emptyList(),
    val memorySize: Int = 0,
    val contextTokens: Int? = null,
    val projectIndexed: Boolean = false,
    val toolCalls: Int = 0,
    val cacheHits: Int = 0,
    val workspacePath: String = "",
)

@Composable
fun ContextPanel(
    info: ContextInfo,
    modifier: Modifier = Modifier,
) {
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
            Text(
                text = "Context",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(8.dp))

            if (info.currentGoal.isNotBlank()) {
                ContextRow("Goal", info.currentGoal.take(100))
                Spacer(Modifier.height(4.dp))
            }

            if (info.currentFile.isNotBlank()) {
                ContextRow("File", info.currentFile)
                Spacer(Modifier.height(4.dp))
            }

            if (info.modifiedFiles.isNotEmpty()) {
                ContextRow("Modified", "${info.modifiedFiles.size} files")
                info.modifiedFiles.takeLast(3).forEach { file ->
                    Text(
                        text = "  \u2022 ${file.split("/").last()}",
                        style = MaterialTheme.typography.labelSmall,
                        fontSize = 9.sp,
                        color = colorScheme.onSurfaceVariant,
                        fontFamily = FontFamily.Monospace,
                    )
                }
                Spacer(Modifier.height(4.dp))
            }

            if (info.workspacePath.isNotBlank()) {
                ContextRow("Workspace", info.workspacePath)
                Spacer(Modifier.height(4.dp))
            }

            Text(
                text = "Memory: ${info.memorySize} items",
                style = MaterialTheme.typography.labelSmall,
                fontSize = 10.sp,
                color = colorScheme.onSurfaceVariant,
            )
            Text(
                text = "Index: ${if (info.projectIndexed) "Ready" else "Not built"}",
                style = MaterialTheme.typography.labelSmall,
                fontSize = 10.sp,
                color = if (info.projectIndexed) colorScheme.primary else colorScheme.error,
            )
            Text(
                text = "Ctx: ${info.contextTokens ?: "?"} tokens",
                style = MaterialTheme.typography.labelSmall,
                fontSize = 10.sp,
                color = colorScheme.onSurfaceVariant,
            )
            Text(
                text = "Tools: ${info.toolCalls} calls, ${info.cacheHits} cache hits",
                style = MaterialTheme.typography.labelSmall,
                fontSize = 10.sp,
                color = colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ContextRow(label: String, value: String) {
    val colorScheme = MaterialTheme.colorScheme
    Row(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "$label: ",
            style = MaterialTheme.typography.labelSmall,
            fontSize = 10.sp,
            fontWeight = FontWeight.SemiBold,
            color = colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.labelSmall,
            fontSize = 10.sp,
            color = colorScheme.onSurface,
            fontFamily = FontFamily.Monospace,
        )
    }
}
