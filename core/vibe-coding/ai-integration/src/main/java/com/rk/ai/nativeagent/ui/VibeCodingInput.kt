@file:OptIn(ExperimentalUuidApi::class)
package com.rk.ai.nativeagent.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AttachFile
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Send
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.rk.ai.models.UIMessagePart
import kotlin.uuid.ExperimentalUuidApi

@Composable
fun VibeCodingInput(
    isProcessing: Boolean,
    onSend: (String, List<UIMessagePart>) -> Unit,
    onStop: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    var text by remember { mutableStateOf("") }
    val colorScheme = MaterialTheme.colorScheme
    val context = LocalContext.current
    var attachedParts by remember { mutableStateOf<List<UIMessagePart>>(emptyList()) }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent(),
    ) { uri: Uri? ->
        uri?.let {
            try {
                val inputStream = context.contentResolver.openInputStream(it)
                val bytes = inputStream?.readBytes() ?: return@let
                inputStream.close()
                val fileName = it.lastPathSegment ?: "file"
                val mimeType = context.contentResolver.getType(it) ?: "application/octet-stream"
                when {
                    mimeType.startsWith("image/") -> {
                        attachedParts = attachedParts + UIMessagePart.Image(
                            url = it.toString(),
                        )
                    }
                    else -> {
                        val content = String(bytes, Charsets.UTF_8)
                        attachedParts = attachedParts + UIMessagePart.Document(
                            url = it.toString(),
                            fileName = fileName,
                            mime = mimeType,
                        )
                    }
                }
            } catch (_: Exception) { }
        }
    }

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent(),
    ) { uri: Uri? ->
        uri?.let {
            try {
                val inputStream = context.contentResolver.openInputStream(it)
                val bytes = inputStream?.readBytes() ?: return@let
                inputStream.close()
                attachedParts = attachedParts + UIMessagePart.Image(
                    url = it.toString(),
                )
            } catch (_: Exception) { }
        }
    }

    Surface(
        modifier = modifier.fillMaxWidth(),
        color = colorScheme.surfaceContainerLow,
        tonalElevation = 1.dp,
    ) {
        Column {
            // Attachment chips
            if (attachedParts.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 2.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    attachedParts.forEachIndexed { idx, part ->
                        val label = when (part) {
                            is UIMessagePart.Image -> "📷 Image ${idx + 1}"
                            is UIMessagePart.Text -> "📄 ${part.text.take(30)}..."
                            else -> "📎 File ${idx + 1}"
                        }
                        InputChip(
                            selected = false,
                            onClick = {
                                attachedParts = attachedParts.toMutableList().also { it.removeAt(idx) }
                            },
                            label = {
                                Text(label, style = MaterialTheme.typography.labelSmall)
                            },
                            trailingIcon = {
                                Text("✕", style = MaterialTheme.typography.labelSmall)
                            },
                            modifier = Modifier.height(24.dp),
                        )
                    }
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Attach file button
                FilledTonalIconButton(
                    onClick = { filePickerLauncher.launch("*/*") },
                    modifier = Modifier.size(32.dp),
                    enabled = !isProcessing,
                ) {
                    Icon(
                        Icons.Outlined.AttachFile,
                        contentDescription = "Attach file",
                        modifier = Modifier.size(16.dp),
                    )
                }

                Spacer(Modifier.width(4.dp))

                // Attach image button
                FilledTonalIconButton(
                    onClick = { imagePickerLauncher.launch("image/*") },
                    modifier = Modifier.size(32.dp),
                    enabled = !isProcessing,
                ) {
                    Icon(
                        Icons.Outlined.Image,
                        contentDescription = "Attach image",
                        modifier = Modifier.size(16.dp),
                    )
                }

                Spacer(Modifier.width(4.dp))

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
                        } else if (text.isNotBlank() || attachedParts.isNotEmpty()) {
                            onSend(text.trim(), attachedParts)
                            text = ""
                            attachedParts = emptyList()
                        }
                    },
                    modifier = Modifier.size(36.dp),
                    enabled = text.isNotBlank() || attachedParts.isNotEmpty() || isProcessing,
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
}
