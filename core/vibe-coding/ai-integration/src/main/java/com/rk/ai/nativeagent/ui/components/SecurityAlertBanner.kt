package com.rk.ai.nativeagent.ui.components

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rk.ai.nativeagent.engine.SecurityAlert

@Composable
fun SecurityAlertBanner(
    alert: SecurityAlert,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colorScheme = MaterialTheme.colorScheme
    val bgColor = when (alert.severity) {
        "CRITICAL" -> colorScheme.errorContainer
        "HIGH" -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.7f)
        "MEDIUM" -> colorScheme.tertiaryContainer
        else -> colorScheme.surfaceContainerHigh
    }
    val fgColor = when (alert.severity) {
        "CRITICAL" -> colorScheme.onErrorContainer
        "HIGH" -> colorScheme.onErrorContainer
        "MEDIUM" -> colorScheme.onTertiaryContainer
        else -> colorScheme.onSurfaceVariant
    }
    val iconColor = when (alert.severity) {
        "CRITICAL" -> colorScheme.error
        "HIGH" -> colorScheme.error
        "MEDIUM" -> colorScheme.tertiary
        else -> colorScheme.onSurfaceVariant
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = bgColor),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Text(
                text = when (alert.severity) {
                    "CRITICAL" -> "!"
                    "HIGH" -> "!"
                    "MEDIUM" -> "i"
                    else -> "?"
                },
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                color = iconColor,
                modifier = Modifier.padding(top = 1.dp),
            )
            Spacer(Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Security ${alert.severity.lowercase().replaceFirstChar { it.uppercase() }}",
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 12.sp,
                    color = fgColor,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = alert.message,
                    style = MaterialTheme.typography.bodySmall,
                    fontSize = 11.sp,
                    color = fgColor.copy(alpha = 0.8f),
                )
                if (alert.filePath != null) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = "File: ${alert.filePath}",
                        style = MaterialTheme.typography.labelSmall,
                        fontSize = 10.sp,
                        color = fgColor.copy(alpha = 0.6f),
                    )
                }
            }
            Text(
                text = "×",
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .clickable(onClick = onDismiss)
                    .padding(4.dp),
                color = fgColor.copy(alpha = 0.7f),
                fontSize = 14.sp,
            )
        }
    }
}
