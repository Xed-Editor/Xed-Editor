package com.rk.ai.nativeagent.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rk.ai.models.ToolApprovalState
import com.rk.ai.models.ExecutionState
import com.rk.ai.models.UIMessagePart
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive

private const val MAX_PREVIEW_CHARS = 500

private fun String.truncatePreview(): String =
    if (length > MAX_PREVIEW_CHARS) take(MAX_PREVIEW_CHARS) + "…" else this

@Composable
fun VibeCodingToolCard(
    part: UIMessagePart.Tool,
    onApprove: ((String) -> Unit)? = null,
    onDeny: ((String) -> Unit)? = null,
    onAnswer: ((String) -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val colorScheme = MaterialTheme.colorScheme
    var expanded by remember { mutableStateOf(false) }
    var denyReason by remember { mutableStateOf("") }
    var showDenyInput by remember { mutableStateOf(false) }
    var answerText by remember { mutableStateOf("") }
    var showAnswerInput by remember { mutableStateOf(false) }

    val isPending = part.approvalState is ToolApprovalState.Pending
    val isAnswered = part.approvalState is ToolApprovalState.Answered
    val isRunning = part.isRunning
    val hasError = part.executionState is ExecutionState.Error
    val isDenied = part.approvalState is ToolApprovalState.Denied

    val statusColor = when {
        isRunning -> colorScheme.tertiary
        hasError -> colorScheme.error
        isDenied -> colorScheme.error
        isPending -> colorScheme.tertiary
        isAnswered -> colorScheme.secondary
        else -> colorScheme.primary
    }

    val statusLabel = when {
        isRunning -> "running"
        hasError -> "error"
        isDenied -> "denied"
        isPending -> "pending"
        isAnswered -> "answered"
        part.executionState is ExecutionState.Completed -> "done"
        part.approvalState is ToolApprovalState.Approved -> "approved"
        else -> "auto"
    }

    val statusIcon = when {
        isRunning -> Icons.Outlined.Refresh
        hasError -> Icons.Outlined.ErrorOutline
        isDenied -> Icons.Outlined.Cancel
        isPending -> Icons.Outlined.HourglassTop
        isAnswered -> Icons.Outlined.QuestionAnswer
        part.executionState is ExecutionState.Completed -> Icons.Outlined.CheckCircle
        else -> Icons.Outlined.CheckCircle
    }

    val durationText = remember(part.executionState) {
        val completed = part.executionState as? ExecutionState.Completed
        if (completed?.startedAt != null && completed.completedAt != null) {
            try {
                val start = java.time.Instant.parse(completed.startedAt)
                val end = java.time.Instant.parse(completed.completedAt)
                val millis = java.time.Duration.between(start, end).toMillis()
                when {
                    millis < 1000 -> "${millis}ms"
                    millis < 60000 -> "${millis / 1000}.${(millis % 1000) / 100}s"
                    else -> "${millis / 60000}m ${(millis % 60000) / 1000}s"
                }
            } catch (_: Exception) { null }
        } else null
    }

    val fileName = remember(part.input) {
        try {
            val json = kotlinx.serialization.json.Json.parseToJsonElement(part.input.ifBlank { "{}" })
            val obj = json as? JsonObject ?: return@remember null
            val path = obj["file_path"]?.jsonPrimitive?.content
                ?: obj["filePath"]?.jsonPrimitive?.content
                ?: obj["path"]?.jsonPrimitive?.content
                ?: obj["file"]?.jsonPrimitive?.content
            path?.substringAfterLast("/")?.takeIf { it.isNotBlank() }
        } catch (_: Exception) { null }
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        shape = RoundedCornerShape(8.dp),
        color = when {
            hasError -> colorScheme.errorContainer.copy(alpha = 0.3f)
            isDenied -> colorScheme.errorContainer.copy(alpha = 0.2f)
            isRunning -> colorScheme.tertiaryContainer.copy(alpha = 0.2f)
            else -> colorScheme.surfaceVariant.copy(alpha = 0.5f)
        },
        tonalElevation = 0.5.dp,
    ) {
        Column {
            Column(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { expanded = !expanded }
                    .padding(8.dp),
            ) {
                // Header row
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        // Expand indicator
                        Text(
                            text = if (expanded) "▼" else "▶",
                            style = MaterialTheme.typography.labelSmall,
                            color = colorScheme.onSurfaceVariant,
                            modifier = Modifier.width(14.dp),
                        )
                        Spacer(Modifier.width(4.dp))

                        // Status icon
                        Icon(
                            imageVector = statusIcon,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = statusColor,
                        )
                        Spacer(Modifier.width(4.dp))

                        // Tool name
                        Text(
                            text = part.toolName,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = colorScheme.onSurfaceVariant,
                        )

                        // File badge for file-touching tools
                        if (fileName != null) {
                            Spacer(Modifier.width(4.dp))
                            Surface(
                                shape = RoundedCornerShape(3.dp),
                                color = colorScheme.primary.copy(alpha = 0.1f),
                            ) {
                                Text(
                                    text = fileName,
                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                                    color = colorScheme.primary.copy(alpha = 0.7f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
                                )
                            }
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        // Duration badge
                        if (durationText != null) {
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            ) {
                                Text(
                                    text = durationText,
                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                                    color = colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
                                )
                            }
                        }

                        // Status badge
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = statusColor.copy(alpha = 0.15f),
                        ) {
                            Text(
                                text = statusLabel,
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                color = statusColor,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            )
                        }
                    }
                }

                // Action buttons (placed right after header for visibility)
                if (isPending) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 6.dp),
                    ) {
                        Button(
                            onClick = { onApprove?.invoke(part.toolCallId) },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = colorScheme.primary,
                            ),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                        ) {
                            Icon(
                                Icons.Outlined.Check,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp),
                            )
                            Spacer(Modifier.width(4.dp))
                            Text("Approve", style = MaterialTheme.typography.labelSmall)
                        }

                        OutlinedButton(
                            onClick = { showDenyInput = !showDenyInput },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = colorScheme.error,
                            ),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                        ) {
                            Icon(
                                Icons.Outlined.Close,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp),
                            )
                            Spacer(Modifier.width(4.dp))
                            Text("Deny", style = MaterialTheme.typography.labelSmall)
                        }

                        OutlinedButton(
                            onClick = { showAnswerInput = !showAnswerInput },
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                        ) {
                            Icon(
                                Icons.Outlined.QuestionAnswer,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp),
                            )
                            Spacer(Modifier.width(4.dp))
                            Text("Answer", style = MaterialTheme.typography.labelSmall)
                        }
                    }

                    // Deny reason input
                    AnimatedVisibility(visible = showDenyInput) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            OutlinedTextField(
                                value = denyReason,
                                onValueChange = { denyReason = it },
                                modifier = Modifier.weight(1f),
                                placeholder = {
                                    Text(
                                        "Reason for denial...",
                                        style = MaterialTheme.typography.bodySmall,
                                    )
                                },
                                singleLine = true,
                                textStyle = MaterialTheme.typography.bodySmall,
                            )
                            Spacer(Modifier.width(4.dp))
                            TextButton(onClick = {
                                onDeny?.invoke(denyReason)
                                denyReason = ""
                                showDenyInput = false
                            }) {
                                Text("Submit", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }

                    // Answer input
                    AnimatedVisibility(visible = showAnswerInput) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            OutlinedTextField(
                                value = answerText,
                                onValueChange = { answerText = it },
                                modifier = Modifier.weight(1f),
                                placeholder = {
                                    Text(
                                        "Your answer...",
                                        style = MaterialTheme.typography.bodySmall,
                                    )
                                },
                                singleLine = true,
                                textStyle = MaterialTheme.typography.bodySmall,
                            )
                            Spacer(Modifier.width(4.dp))
                            TextButton(onClick = {
                                onAnswer?.invoke(answerText)
                                answerText = ""
                                showAnswerInput = false
                            }) {
                                Text("Submit", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }

                // Expandable input/output detail
                AnimatedVisibility(
                    visible = expanded,
                    enter = expandVertically(),
                    exit = shrinkVertically(),
                ) {
                    Column(modifier = Modifier.padding(top = 6.dp)) {
                        // Input section
                        CodePreviewLabel("Input:")
                        CodePreviewText(part.input.toString().truncatePreview(), colorScheme)

                        // Output or Preview Diff section
                        val previewDiff = part.metadata?.get("previewDiff")?.let {
                            try { it.jsonPrimitive.content } catch (e: Exception) { null }
                        }

                        if (previewDiff != null && isPending) {
                            Spacer(Modifier.height(4.dp))
                            CodePreviewLabel("Pending Changes (Diff Preview):")
                            DiffPreviewText(
                                text = previewDiff,
                                colorScheme = colorScheme,
                            )
                        } else if (part.output.isNotEmpty()) {
                            Spacer(Modifier.height(4.dp))

                            // Check if output has diff-like content
                            val hasDiffContent = part.output.any { outputPart ->
                                outputPart is UIMessagePart.Text &&
                                    (outputPart.text.contains("---") || outputPart.text.contains("+++") ||
                                        outputPart.text.contains("@@"))
                            }

                            if (hasDiffContent) {
                                CodePreviewLabel("Output (diff):")
                            } else {
                                CodePreviewLabel("Output:")
                            }

                            part.output.forEach { outputPart ->
                                when (outputPart) {
                                    is UIMessagePart.Text -> {
                                        if (hasDiffContent) {
                                            DiffPreviewText(
                                                text = outputPart.text.truncatePreview(),
                                                colorScheme = colorScheme,
                                            )
                                        } else {
                                            CodePreviewText(
                                                text = outputPart.text.truncatePreview(),
                                                colorScheme = colorScheme,
                                            )
                                        }
                                    }
                                    else -> {
                                        Text(
                                            text = "[${outputPart::class.simpleName}]",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                        )
                                    }
                                }
                            }
                        }

                        // Error details
                        if (hasError) {
                            val errorMsg =
                                (part.executionState as ExecutionState.Error).error ?: "Unknown error"
                            Spacer(Modifier.height(4.dp))
                            CodePreviewLabel("Error:")
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = colorScheme.errorContainer.copy(alpha = 0.3f),
                            ) {
                                Text(
                                    text = errorMsg,
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 11.sp,
                                    ),
                                    color = colorScheme.onErrorContainer,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(6.dp),
                                )
                            }
                        }
                    }
                }

                // Denied reason display
                if (isDenied) {
                    val reason = (part.approvalState as ToolApprovalState.Denied).reason
                    if (reason.isNotBlank()) {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = "Reason: $reason",
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontStyle = FontStyle.Italic,
                            ),
                            color = colorScheme.error,
                        )
                    }
                }

                // Answered text display
                if (isAnswered) {
                    val answer = (part.approvalState as ToolApprovalState.Answered).answer
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "Answer: $answer",
                        style = MaterialTheme.typography.bodySmall,
                        color = colorScheme.secondary,
                        fontWeight = FontWeight.Medium,
                    )
                }

                // Retry hint for errors
                if (hasError) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "Tip: You can ask the agent to retry with a different approach",
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                        color = colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                    )
                }
            }
        }
    }
}

@Composable
private fun CodePreviewLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun CodePreviewText(text: String, colorScheme: ColorScheme) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall.copy(
            fontFamily = FontFamily.Monospace,
            fontSize = 11.sp,
        ),
        color = colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
        modifier = Modifier
            .fillMaxWidth()
            .background(colorScheme.surface.copy(alpha = 0.5f), RoundedCornerShape(4.dp))
            .padding(6.dp),
    )
}

@Composable
private fun DiffPreviewText(text: String, colorScheme: ColorScheme) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(colorScheme.surface.copy(alpha = 0.5f), RoundedCornerShape(4.dp))
            .padding(6.dp),
    ) {
        text.lines().forEach { line ->
            val lineColor = when {
                line.startsWith("+") && !line.startsWith("+++") -> Color(0xFF2E7D32).copy(alpha = 0.8f)
                line.startsWith("-") && !line.startsWith("---") -> Color(0xFFC62828).copy(alpha = 0.8f)
                line.startsWith("@@") -> colorScheme.primary.copy(alpha = 0.6f)
                line.startsWith("diff --git") || line.startsWith("index") -> colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                else -> colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
            }
            val bgColor = when {
                line.startsWith("+") && !line.startsWith("+++") -> Color(0xFF2E7D32).copy(alpha = 0.08f)
                line.startsWith("-") && !line.startsWith("---") -> Color(0xFFC62828).copy(alpha = 0.08f)
                else -> Color.Transparent
            }
            Text(
                text = line,
                style = MaterialTheme.typography.bodySmall.copy(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 10.sp,
                ),
                color = lineColor,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(bgColor)
                    .padding(horizontal = 4.dp, vertical = 0.5.dp),
            )
        }
    }
}
