package com.rk.ai.nativeagent.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rk.ai.models.ToolApprovalState
import com.rk.ai.models.UIMessagePart

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

    val statusColor = when (part.approvalState) {
        is ToolApprovalState.Auto,
        is ToolApprovalState.Approved -> colorScheme.primary
        is ToolApprovalState.Pending -> colorScheme.tertiary
        is ToolApprovalState.Denied -> colorScheme.error
        is ToolApprovalState.Answered -> colorScheme.secondary
    }

    val statusLabel = when (part.approvalState) {
        is ToolApprovalState.Auto -> "auto"
        is ToolApprovalState.Pending -> "pending"
        is ToolApprovalState.Approved -> "approved"
        is ToolApprovalState.Denied -> "denied"
        is ToolApprovalState.Answered -> "answered"
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        shape = RoundedCornerShape(8.dp),
        color = colorScheme.surfaceVariant.copy(alpha = 0.5f),
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
                        Text(
                            text = if (expanded) "▼" else "▶",
                            style = MaterialTheme.typography.labelSmall,
                            color = colorScheme.onSurfaceVariant,
                            modifier = Modifier.width(14.dp),
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = "Tool: ${part.toolName}",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = colorScheme.onSurfaceVariant,
                        )
                    }
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

                // Expandable input/output detail
                AnimatedVisibility(
                    visible = expanded,
                    enter = expandVertically(),
                    exit = shrinkVertically(),
                ) {
                    Column(modifier = Modifier.padding(top = 6.dp)) {
                        CodePreviewLabel("Input:")
                        CodePreviewText(part.input.toString().truncatePreview(), colorScheme)

                        if (part.output.isNotEmpty()) {
                            Spacer(Modifier.height(4.dp))
                            CodePreviewLabel("Output:")
                            part.output.forEach { outputPart ->
                                when (outputPart) {
                                    is com.rk.ai.models.UIMessagePart.Text -> CodePreviewText(
                                        text = outputPart.text.truncatePreview(),
                                        colorScheme = colorScheme,
                                    )
                                    else -> Text(
                                        text = "[${outputPart::class.simpleName}]",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                    )
                                }
                            }
                        }
                    }
                }

                // Denied reason display
                if (part.approvalState is ToolApprovalState.Denied) {
                    val reason = (part.approvalState as ToolApprovalState.Denied).reason
                    if (reason.isNotBlank()) {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = "Reason: $reason",
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
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
            }

            // Action buttons
            if (isPending) {
                Column(
                    modifier = Modifier.padding(start = 8.dp, end = 8.dp, bottom = 8.dp),
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        // Approve button
                        Button(
                            onClick = { onApprove?.invoke("") },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = colorScheme.primary,
                            ),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                        ) {
                            Text(
                                "Approve",
                                style = MaterialTheme.typography.labelSmall,
                            )
                        }

                        // Deny button
                        OutlinedButton(
                            onClick = { showDenyInput = !showDenyInput },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = colorScheme.error,
                            ),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                        ) {
                            Text(
                                "Deny",
                                style = MaterialTheme.typography.labelSmall,
                            )
                        }

                        // Answer button (for ask_user type tools)
                        if (part.toolName == "ask_user" || part.toolName == "question") {
                            OutlinedButton(
                                onClick = { showAnswerInput = !showAnswerInput },
                                modifier = Modifier.weight(1f),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                            ) {
                                Text(
                                    "Answer",
                                    style = MaterialTheme.typography.labelSmall,
                                )
                            }
                        }
                    }

                    // Deny reason input
                    AnimatedVisibility(visible = showDenyInput) {
                        Column(modifier = Modifier.padding(top = 6.dp)) {
                            OutlinedTextField(
                                value = denyReason,
                                onValueChange = { denyReason = it },
                                placeholder = { Text("Reason for denial (optional)", style = MaterialTheme.typography.bodySmall) },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                                textStyle = MaterialTheme.typography.bodySmall,
                            )
                            Spacer(Modifier.height(4.dp))
                            Button(
                                onClick = {
                                    onDeny?.invoke(denyReason)
                                    showDenyInput = false
                                    denyReason = ""
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = colorScheme.error,
                                ),
                            ) {
                                Text("Confirm Deny", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }

                    // Answer input
                    AnimatedVisibility(visible = showAnswerInput) {
                        Column(modifier = Modifier.padding(top = 6.dp)) {
                            OutlinedTextField(
                                value = answerText,
                                onValueChange = { answerText = it },
                                placeholder = { Text("Your answer", style = MaterialTheme.typography.bodySmall) },
                                singleLine = false,
                                maxLines = 3,
                                modifier = Modifier.fillMaxWidth(),
                                textStyle = MaterialTheme.typography.bodySmall,
                            )
                            Spacer(Modifier.height(4.dp))
                            Button(
                                onClick = {
                                    onAnswer?.invoke(answerText)
                                    showAnswerInput = false
                                    answerText = ""
                                },
                            ) {
                                Text("Submit Answer", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
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
