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
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rk.ai.core.MessageRole
import com.rk.ai.models.UIMessage
import com.rk.ai.models.UIMessagePart
import com.rk.ai.nativeagent.ui.markdown.MarkdownContent
import com.rk.theme.DesignTokens

@Composable
fun VibeCodingMessageBubble(
    message: UIMessage,
    onApproveTool: ((String) -> Unit)? = null,
    onDenyTool: ((String, String) -> Unit)? = null,
    onAnswerTool: ((String, String) -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val isUser = message.role == MessageRole.USER
    val colorScheme = MaterialTheme.colorScheme

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = DesignTokens.Spacing.medium, vertical = DesignTokens.Spacing.xsmall),
        horizontalAlignment = if (isUser) Alignment.End else Alignment.Start,
    ) {
        Surface(
            shape = RoundedCornerShape(
                topStart = DesignTokens.CornerRadius.xlarge,
                topEnd = DesignTokens.CornerRadius.xlarge,
                bottomStart = if (isUser) DesignTokens.CornerRadius.xlarge else DesignTokens.CornerRadius.small,
                bottomEnd = if (isUser) DesignTokens.CornerRadius.small else DesignTokens.CornerRadius.xlarge,
            ),
            color = if (isUser) colorScheme.primaryContainer else colorScheme.surfaceContainerHigh,
            tonalElevation = if (isUser) 0.dp else 1.dp,
        ) {
            Column(modifier = Modifier.padding(DesignTokens.Spacing.medium)) {
                message.parts.forEach { part ->
                    MessagePartContent(
                        part = part,
                        isUser = isUser,
                        onApproveTool = onApproveTool,
                        onDenyTool = onDenyTool,
                        onAnswerTool = onAnswerTool,
                    )
                    Spacer(Modifier.height(DesignTokens.Spacing.xsmall))
                }

                message.usage?.let { usage ->
                    val promptTokens = usage.promptTokens ?: 0
                    val completionTokens = usage.completionTokens ?: 0
                    val totalTokens = usage.totalTokens ?: (promptTokens + completionTokens)
                    if (totalTokens > 0) {
                        Spacer(Modifier.height(DesignTokens.Spacing.xxsmall))
                        Text(
                            text = "△ $promptTokens / ◻ $completionTokens / ∑ $totalTokens tok",
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MessagePartContent(
    part: UIMessagePart,
    isUser: Boolean,
    onApproveTool: ((String) -> Unit)? = null,
    onDenyTool: ((String, String) -> Unit)? = null,
    onAnswerTool: ((String, String) -> Unit)? = null,
) {
    val colorScheme = MaterialTheme.colorScheme
    when (part) {
        is UIMessagePart.Text -> {
            if (isUser) {
                Text(
                    text = part.text,
                    color = colorScheme.onPrimaryContainer,
                    style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 22.sp),
                )
            } else {
                MarkdownContent(
                    text = part.text,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
        is UIMessagePart.Reasoning -> MessageReasoningBlock(part)
        is UIMessagePart.Tool -> VibeCodingToolCard(
            part = part,
            onApprove = { onApproveTool?.invoke(part.toolCallId) },
            onDeny = { reason -> onDenyTool?.invoke(part.toolCallId, reason) },
            onAnswer = { answer -> onAnswerTool?.invoke(part.toolCallId, answer) },
        )
        is UIMessagePart.StepStart -> {
            val colorScheme = MaterialTheme.colorScheme
            Surface(
                shape = DesignTokens.CornerRadius.small,
                color = colorScheme.primaryContainer.copy(alpha = 0.3f),
            ) {
                Text(
                    text = "Step ${part.stepIndex + 1}",
                    style = MaterialTheme.typography.labelSmall,
                    color = colorScheme.onPrimaryContainer,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(horizontal = DesignTokens.Spacing.small, vertical = DesignTokens.Spacing.xsmall),
                )
            }
        }
        is UIMessagePart.StepFinish -> {
            val colorScheme = MaterialTheme.colorScheme
            val tokenInfo = buildString {
                if (part.inputTokens > 0) append("Δ ${part.inputTokens} ")
                if (part.outputTokens > 0) append("◻ ${part.outputTokens} ")
                if (part.reasoningTokens > 0) append("~ ${part.reasoningTokens} ")
                if (part.cost > 0f) append("$${String.format("%.4f", part.cost)}")
            }
            Text(
                text = "Step ${part.stepIndex + 1} done | ${tokenInfo.ifEmpty { "done" }}",
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                color = colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
            )
        }
        is UIMessagePart.Image -> AttachmentLabel("[Image: ${part.url.take(60)}]")
        is UIMessagePart.Document -> AttachmentLabel("[Document: ${part.fileName}]")
        else -> AttachmentLabel("[${part::class.simpleName}]", muted = true)
    }
}

@Composable
private fun AttachmentLabel(text: String, muted: Boolean = false) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = if (muted)
            MaterialTheme.colorScheme.onSurfaceVariant
        else
            MaterialTheme.colorScheme.primary,
        fontStyle = if (muted) FontStyle.Normal else FontStyle.Italic,
    )
}

@Composable
private fun MessageReasoningBlock(part: UIMessagePart.Reasoning) {
    val colorScheme = MaterialTheme.colorScheme
    var expanded by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(DesignTokens.CornerRadius.medium)
            .background(colorScheme.tertiaryContainer.copy(alpha = 0.3f))
            .clickable { expanded = !expanded }
            .padding(DesignTokens.Spacing.small),
    ) {
        Text(
            text = if (expanded) "▼ Thinking" else "▶ Thinking",
            style = MaterialTheme.typography.labelSmall,
            color = colorScheme.onTertiaryContainer,
            fontWeight = FontWeight.SemiBold,
        )
        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically(),
            exit = shrinkVertically(),
        ) {
            Text(
                text = part.reasoning,
                style = MaterialTheme.typography.bodySmall.copy(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                ),
                color = colorScheme.onTertiaryContainer.copy(alpha = 0.8f),
                modifier = Modifier.padding(top = DesignTokens.Spacing.xsmall),
            )
        }
    }
}
