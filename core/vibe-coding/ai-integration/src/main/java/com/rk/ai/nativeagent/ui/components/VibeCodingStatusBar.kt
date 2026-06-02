@file:OptIn(ExperimentalUuidApi::class)
package com.rk.ai.nativeagent.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rk.ai.nativeagent.engine.VibeCodingState

@Composable
fun VibeCodingStatusBar(
    state: VibeCodingState,
    modifier: Modifier = Modifier,
) {
    val colorScheme = MaterialTheme.colorScheme

    val statusText = buildString {
        if (state.isProcessing) {
            append("Generating")
        }
        if (state.activeAgents.isNotEmpty()) {
            if (isNotEmpty()) append(" · ")
            append("${state.activeAgents.size} agent${if (state.activeAgents.size > 1) "s" else ""} active")
        }
        if (state.hasSecurityAlerts) {
            if (isNotEmpty()) append(" · ")
            append("${state.securityAlerts.size} alert${if (state.securityAlerts.size > 1) "s" else ""}")
        }
        if (isEmpty()) append("Ready")
    }

    val statusColor = when {
        state.isProcessing -> colorScheme.primary
        state.hasSecurityAlerts -> colorScheme.error
        else -> colorScheme.onSurfaceVariant
    }

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        color = colorScheme.surfaceContainerHigh,
        tonalElevation = 0.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (state.isProcessing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(12.dp),
                        strokeWidth = 2.dp,
                        color = colorScheme.primary,
                    )
                    Spacer(Modifier.width(8.dp))
                }
                Text(
                    text = statusText,
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 10.sp,
                    color = statusColor,
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                val messageCount = state.messages.size
                Text(
                    text = "$messageCount msg${if (messageCount != 1) "s" else ""}",
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 10.sp,
                    color = colorScheme.onSurfaceVariant,
                )
                if (state.currentConversationId != null) {
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "saved",
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp,
                        color = colorScheme.tertiary,
                    )
                }
            }
        }
    }
}
