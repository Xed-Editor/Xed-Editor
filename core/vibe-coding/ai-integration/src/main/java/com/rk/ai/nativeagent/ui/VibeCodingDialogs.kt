@file:OptIn(ExperimentalUuidApi::class)
package com.rk.ai.nativeagent.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rk.ai.nativeagent.engine.VibeCodingState
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@Composable
internal fun RenameSessionDialog(
    sessionId: Uuid,
    currentTitle: String,
    onRename: (Uuid, String) -> Unit,
    onDismiss: () -> Unit,
) {
    var renameText by remember { mutableStateOf(currentTitle) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Rename Session") },
        text = {
            OutlinedTextField(
                value = renameText,
                onValueChange = { renameText = it },
                singleLine = true,
                placeholder = { Text("Session name") },
            )
        },
        confirmButton = {
            TextButton(onClick = {
                onRename(sessionId, renameText)
                onDismiss()
            }) { Text("Rename") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

@Composable
internal fun StopConfirmDialog(
    onStop: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Stop Generation?") },
        text = { Text("The agent is still processing. Stopping now may lose progress. Are you sure?") },
        confirmButton = {
            TextButton(onClick = {
                onDismiss()
                onStop()
            }) { Text("Stop", color = MaterialTheme.colorScheme.error) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Continue") }
        },
    )
}

@Composable
internal fun ExportConversationDialog(
    state: VibeCodingState,
    onDismiss: () -> Unit,
) {
    val exportContent = remember(state.messages) { state.exportAsMarkdown() }
    val context = LocalContext.current

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Export Conversation") },
        text = {
            Column {
                Text(
                    "Conversation export as Markdown:",
                    style = MaterialTheme.typography.bodySmall,
                )
                Spacer(Modifier.height(8.dp))
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 300.dp),
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHighest,
                ) {
                    Text(
                        text = exportContent.take(2000) + if (exportContent.length > 2000) "\n\n..." else "",
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 10.sp,
                        ),
                        modifier = Modifier
                            .padding(8.dp)
                            .verticalScroll(rememberScrollState()),
                    )
                }
            }
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(onClick = {
                    val cm = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                    cm.setPrimaryClip(android.content.ClipData.newPlainText("VibeCoding", exportContent))
                    onDismiss()
                }) {
                    Text("Copy")
                }
                TextButton(onClick = {
                    try {
                        val dir = java.io.File(context.filesDir, "vibecoding_exports")
                        dir.mkdirs()
                        val file = java.io.File(dir, "conversation_${java.lang.System.currentTimeMillis()}.md")
                        file.writeText(exportContent)
                        android.widget.Toast.makeText(context, "Saved to ${file.absolutePath}", android.widget.Toast.LENGTH_LONG).show()
                    } catch (e: Exception) {
                        android.widget.Toast.makeText(context, "Failed to save: ${e.message}", android.widget.Toast.LENGTH_SHORT).show()
                    }
                    onDismiss()
                }) {
                    Text("Save to File")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        },
    )
}

@Composable
internal fun ClearConversationDialog(
    onClear: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Clear Conversation") },
        text = { Text("This will permanently delete all messages in this conversation. This action cannot be undone.") },
        confirmButton = {
            TextButton(
                onClick = {
                    onDismiss()
                    onClear()
                },
            ) {
                Text("Clear", color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}
