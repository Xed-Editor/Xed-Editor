package com.rk.ai.nativeagent.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Send
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun VibeCodingInput(
    isProcessing: Boolean,
    onSend: (String) -> Unit,
    onStop: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    var text by remember { mutableStateOf("") }
    val colorScheme = MaterialTheme.colorScheme

    Surface(
        modifier = modifier.fillMaxWidth(),
        color = colorScheme.surfaceContainerLow,
        tonalElevation = 1.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text("Ask VibeCoding...", style = MaterialTheme.typography.bodySmall) },
                textStyle = MaterialTheme.typography.bodySmall,
                shape = RoundedCornerShape(20.dp),
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    unfocusedBorderColor = colorScheme.outlineVariant.copy(alpha = 0.3f),
                    focusedBorderColor = colorScheme.primary.copy(alpha = 0.5f),
                    unfocusedContainerColor = colorScheme.surfaceContainerHighest.copy(alpha = 0.5f),
                    focusedContainerColor = colorScheme.surfaceContainerHighest.copy(alpha = 0.7f),
                ),
                enabled = !isProcessing,
            )

            Spacer(Modifier.width(6.dp))

            FilledIconButton(
                onClick = {
                    if (isProcessing) {
                        onStop?.invoke()
                    } else if (text.isNotBlank()) {
                        onSend(text.trim())
                        text = ""
                    }
                },
                modifier = Modifier.size(36.dp),
                enabled = text.isNotBlank() || isProcessing,
                shape = RoundedCornerShape(18.dp),
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = if (isProcessing) colorScheme.errorContainer else colorScheme.primary,
                    contentColor = if (isProcessing) colorScheme.onErrorContainer else colorScheme.onPrimary,
                ),
            ) {
                Icon(
                    imageVector = if (isProcessing) Icons.Outlined.Stop else Icons.Outlined.Send,
                    contentDescription = if (isProcessing) "Stop" else "Send",
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}
