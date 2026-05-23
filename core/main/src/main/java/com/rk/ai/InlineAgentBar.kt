package com.rk.ai

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rk.activities.main.MainViewModel
import com.rk.ai.session.AiSessionManager
import com.rk.icons.Icon
import com.rk.icons.XedIcon
import com.rk.resources.drawables
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private typealias StateUpdate = (ConversationState) -> ConversationState

@Composable
fun InlineAgentBar(
    viewModel: MainViewModel,
    visible: Boolean,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    var input by remember { mutableStateOf("") }
    var conversation by remember { mutableStateOf(ConversationState()) }
    val conversationChannel = remember { Channel<StateUpdate>(Channel.UNLIMITED) }
    val listState = rememberLazyListState()
    var currentSendJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }

    LaunchedEffect(conversationChannel) {
        for (update in conversationChannel) {
            conversation = update(conversation)
            val isNearBottom = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.let {
                it.index >= conversation.messages.size - 2
            } ?: true
            if (isNearBottom && conversation.messages.isNotEmpty()) {
                listState.animateScrollToItem(conversation.messages.size)
            }
        }
    }
    val colorScheme = MaterialTheme.colorScheme

    fun buildFileContext(): String {
        val state = viewModel.currentTab as? com.rk.tabs.editor.EditorTab ?: return ""
        val filePath = state.file?.getAbsolutePath() ?: return ""
        val editor = state.editorState.editor.get()
        val selectedText = editor?.getSelectedText().orEmpty()
        val fileName = state.file.getName()
        val extension = fileName.substringAfterLast('.', "")
        val cursorLine = (editor?.cursor?.leftLine ?: 0).coerceAtLeast(0)
        val cursorColumn = (editor?.cursor?.leftColumn ?: 0).coerceAtLeast(0)
        val fullText = editor?.text?.toString().orEmpty()
        val focusedSnippet = focusedContextSnippet(fullText, cursorLine, cursorColumn)
        return buildString {
            appendLine("## Current File Context")
            appendLine("- File: `$fileName`")
            appendLine("- Path: `$filePath`")
            if (extension.isNotBlank()) appendLine("- Type: .$extension")
            val lineCount = editor?.lineCount ?: 0
            if (lineCount > 0) appendLine("- Lines: $lineCount")
            appendLine("- Cursor: ${cursorLine + 1}:${cursorColumn + 1}")

            if (focusedSnippet.isNotBlank()) {
                appendLine()
                appendLine("### Nearby Code")
                appendLine("```$extension")
                appendLine(focusedSnippet)
                appendLine("```")
            }

            if (selectedText.isNotBlank()) {
                appendLine()
                appendLine("### Selected Code")
                appendLine("```${extension.lowercase()}")
                appendLine(selectedText.take(2000))
                appendLine("```")
            }
        }
    }

    fun sendMessage(prompt: String) {
        currentSendJob?.cancel()
        currentSendJob = scope.launch {
            doSendMessage(prompt)
        }
    }

    private suspend fun doSendMessage(prompt: String) {
        val state = viewModel.currentTab
        val wd = if (state is com.rk.tabs.editor.EditorTab) {
            state.projectRoot?.getAbsolutePath()
                ?: state.file?.getAbsolutePath()?.let { java.io.File(it).parent }
        } else null
        val effectiveWd = wd ?: "/home"
        val currentAgent = AiSessionManager.currentAgent
        val firstMessage = conversation.messages.isEmpty()

        val fullPrompt = if (firstMessage) {
            buildString {
                val fileContext = buildFileContext()
                if (fileContext.isNotBlank()) appendLine(fileContext).appendLine()
                appendLine("## Request")
                appendLine(prompt)
            }
        } else {
            conversation.withSystemPrompt(currentAgent.displayName).buildContextPrompt(prompt)
        }

        conversation = conversation.addUserMessage(prompt)

        val bridgeInfo = withContext(Dispatchers.IO) {
            IdeBridge.ensureStarted(viewModel, effectiveWd) ?: IdeBridge.getBridgeInfo()
        }
        if (bridgeInfo == null) {
            conversation = conversation.setError("IDE bridge is not running. Start the AI sheet once and retry.")
            return
        }

        val result = try {
            withContext(Dispatchers.IO) {
                AgentCli.runAgent(
                    prompt = fullPrompt,
                    agent = currentAgent,
                    workingDir = effectiveWd,
                    ideBridge = bridgeInfo,
                    onOutput = { chunk ->
                        val cleaned = cleanStreamingLine(chunk) ?: return@runAgent
                        conversationChannel.trySend { it.appendStreaming(cleaned + "\n") }
                    },
                )
            }
        } catch (e: Exception) {
            conversationChannel.trySend { it.setError(e.message ?: "Unknown error") }
            return
        }

        try {
            conversationChannel.trySend { it.finishStreaming() }
            if (result.timedOut) {
                conversationChannel.trySend { it.setError("Request timed out. Try a shorter prompt or reconnect the agent.") }
                return
            }
            if (result.exitCode != 0) {
                val detail = AgentCli.cleanOutput(
                    listOf(result.error, result.output)
                        .filter { it.isNotBlank() }
                        .joinToString("\n")
                ).lineSequence().firstOrNull()?.take(240).orEmpty()
                val message = if (detail.isBlank()) {
                    "Agent exited with code ${result.exitCode}."
                } else {
                    "Agent exited with code ${result.exitCode}: $detail"
                }
                conversationChannel.trySend { it.addSystemMessage(message) }
            }
        } catch (e: Exception) {
            conversationChannel.trySend { it.setError(e.message ?: "Unknown error") }
        }
    }

    AnimatedVisibility(
        visible = visible,
        enter = slideInVertically(initialOffsetY = { it }),
        exit = slideOutVertically(targetOffsetY = { it }),
        modifier = modifier,
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth().heightIn(max = 420.dp),
            shadowElevation = 8.dp,
            tonalElevation = 4.dp,
            shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
        ) {
            Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                Header(
                    messageCount = conversation.messages.size / 2,
                    isLoading = conversation.isLoading,
                    onClear = { conversation = conversation.clear() },
                    onDismiss = onDismiss,
                    colorScheme = colorScheme,
                )

                if (conversation.messages.isEmpty() && !conversation.isLoading && conversation.streamingText.isBlank()) {
                    EmptyState(colorScheme)
                }

                LazyColumn(
                    state = listState,
                    modifier = Modifier.weight(1f).fillMaxWidth().padding(vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    items(conversation.messages, key = { "msg-${it.timestamp}" }) { msg ->
                        when (msg) {
                            is ChatMessage.User -> UserBubble(msg.content, colorScheme)
                            is ChatMessage.Assistant -> AssistantBubble(msg.content, colorScheme)
                            is ChatMessage.System -> AssistantBubble(msg.content, colorScheme)
                        }
                    }

                    if (conversation.streamingText.isNotBlank()) {
                        item(key = "streaming") {
                            StreamingBubble(conversation.streamingText, colorScheme)
                        }
                    }
                }

                conversation.error?.let { err ->
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = colorScheme.errorContainer,
                        modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
                    ) {
                        Text(
                            text = err,
                            modifier = Modifier.padding(8.dp),
                            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                            color = colorScheme.onErrorContainer,
                        )
                    }
                }

                InputRow(
                    value = input,
                    onValueChange = { input = it },
                    enabled = !conversation.isLoading,
                    onSend = {
                        if (input.isBlank()) return@InputRow
                        val prompt = input.trim()
                        input = ""
                        sendMessage(prompt)
                    },
                )
            }
        }
    }
}

@Composable
private fun Header(
    messageCount: Int,
    isLoading: Boolean,
    onClear: () -> Unit,
    onDismiss: () -> Unit,
    colorScheme: ColorScheme,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            text = "Ask ${AiSessionManager.currentAgent.displayName}",
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.weight(1f),
        )
        if (messageCount > 0) {
            Text(
                text = "$messageCount turns",
                style = MaterialTheme.typography.labelSmall,
                color = colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.width(4.dp))
            IconButton(onClick = onClear, modifier = Modifier.size(28.dp)) {
                XedIcon(Icon.DrawableRes(drawables.close), contentDescription = "Clear", modifier = Modifier.size(16.dp))
            }
        }
        if (isLoading) {
            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
            Spacer(Modifier.width(4.dp))
        }
        TextButton(onClick = onDismiss, modifier = Modifier.height(28.dp), contentPadding = PaddingValues(horizontal = 4.dp)) {
            Icon(Icons.Outlined.Close, contentDescription = "Close", modifier = Modifier.size(18.dp))
        }
    }
}

@Composable
private fun EmptyState(colorScheme: ColorScheme) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "Ask about your code",
            style = MaterialTheme.typography.bodyMedium,
            color = colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = "Explain, refactor, debug, or write tests",
            style = MaterialTheme.typography.bodySmall,
            color = colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
        )
    }
}

@Composable
private fun UserBubble(text: String, colorScheme: ColorScheme) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(start = 48.dp),
        horizontalAlignment = Alignment.End,
    ) {
        Text(
            text = "You",
            style = MaterialTheme.typography.labelSmall,
            color = colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 2.dp, start = 8.dp),
        )
        Surface(
            shape = RoundedCornerShape(12.dp, 4.dp, 12.dp, 12.dp),
            color = colorScheme.primaryContainer,
            modifier = Modifier.widthIn(max = 320.dp),
        ) {
            SelectionContainer {
                Text(
                    text = text,
                    modifier = Modifier.padding(10.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = colorScheme.onPrimaryContainer,
                )
            }
        }
    }
}

@Composable
private fun AssistantBubble(text: String, colorScheme: ColorScheme) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(end = 48.dp),
        horizontalAlignment = Alignment.Start,
    ) {
        Text(
            text = AiSessionManager.currentAgent.displayName,
            style = MaterialTheme.typography.labelSmall,
            color = colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 2.dp, end = 8.dp),
        )
        Surface(
            shape = RoundedCornerShape(4.dp, 12.dp, 12.dp, 12.dp),
            color = colorScheme.surfaceContainerHigh,
            modifier = Modifier.widthIn(max = 320.dp),
        ) {
            Column {
                val parts = splitCodeBlocks(text)
                parts.forEach { part ->
                    when (part) {
                        is CodeBlockPart.Text -> {
                            SelectionContainer {
                                Text(
                                    text = part.text,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = colorScheme.onSurface,
                                )
                            }
                        }
                        is CodeBlockPart.Code -> {
                            CodeBlock(part.code, part.language, colorScheme)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StreamingBubble(text: String, colorScheme: ColorScheme) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(end = 48.dp),
        horizontalAlignment = Alignment.Start,
    ) {
        Text(
            text = AiSessionManager.currentAgent.displayName,
            style = MaterialTheme.typography.labelSmall,
            color = colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 2.dp, end = 8.dp),
        )
        Surface(
            shape = RoundedCornerShape(4.dp, 12.dp, 12.dp, 12.dp),
            color = colorScheme.surfaceContainerHigh,
            modifier = Modifier.widthIn(max = 320.dp),
        ) {
            Column(modifier = Modifier.padding(10.dp)) {
                Text(
                    text = text,
                    style = MaterialTheme.typography.bodySmall,
                    color = colorScheme.onSurface,
                )
                Spacer(Modifier.height(6.dp))
                PulsingDotIndicator(colorScheme)
            }
        }
    }
}

@Composable
private fun PulsingDotIndicator(colorScheme: ColorScheme) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    Row(verticalAlignment = Alignment.CenterVertically) {
        repeat(3) { index ->
            val alpha by infiniteTransition.animateFloat(
                initialValue = 0.3f,
                targetValue = 1.0f,
                animationSpec = infiniteRepeatable(
                    animation = tween(durationMillis = 600, delayMillis = index * 200),
                    repeatMode = RepeatMode.Reverse,
                ),
                label = "dot-$index",
            )
            Box(
                modifier = Modifier
                    .size(5.dp)
                    .padding(end = if (index < 2) 3.dp else 0.dp)
                    .background(
                        color = colorScheme.primary.copy(alpha = alpha),
                        shape = CircleShape
                    )
            )
        }
    }
}

@Composable
private fun CodeBlock(code: String, language: String?, colorScheme: ColorScheme) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = colorScheme.surfaceVariant.copy(alpha = 0.7f),
        modifier = Modifier.fillMaxWidth().padding(4.dp),
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = language ?: "code",
                    style = MaterialTheme.typography.labelSmall,
                    color = colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                TextButton(
                    onClick = { setClipboard(code) },
                    modifier = Modifier.height(24.dp),
                    contentPadding = PaddingValues(horizontal = 6.dp),
                ) {
                    XedIcon(Icon.DrawableRes(drawables.copy), contentDescription = "Copy", modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(2.dp))
                    Text("Copy", style = MaterialTheme.typography.labelSmall)
                }
            }
            HorizontalDivider(color = colorScheme.outlineVariant.copy(alpha = 0.3f))
            SelectionContainer {
                Text(
                    text = code,
                    modifier = Modifier.padding(8.dp),
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                    ),
                    color = colorScheme.onSurface,
                )
            }
        }
    }
}

@Composable
private fun InputRow(
    value: String,
    onValueChange: (String) -> Unit,
    enabled: Boolean,
    onSend: () -> Unit,
) {
    var errorMessage by remember { mutableStateOf<String?>(null) }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth(),
    ) {
        OutlinedTextField(
            value = value,
            onValueChange = { onValueChange(it); errorMessage = null },
            placeholder = { Text("Follow-up question...", style = MaterialTheme.typography.bodySmall) },
            modifier = Modifier.weight(1f),
            singleLine = true,
            shape = RoundedCornerShape(12.dp),
            textStyle = MaterialTheme.typography.bodySmall,
            enabled = enabled,
            isError = errorMessage != null,
            supportingText = if (errorMessage != null) {
                { Text(errorMessage!!, color = MaterialTheme.colorScheme.error) }
            } else null,
        )
        Spacer(Modifier.width(8.dp))
        FilledTonalIconButton(
            onClick = {
                if (value.isBlank()) {
                    errorMessage = "Type a message"
                    return@FilledTonalIconButton
                }
                errorMessage = null
                onSend()
            },
            enabled = value.isNotBlank() && enabled,
        ) {
            Icon(Icons.Outlined.Send, contentDescription = "Send")
        }
    }
}

// ── Helpers ──

private sealed class CodeBlockPart {
    data class Text(val text: String) : CodeBlockPart()
    data class Code(val code: String, val language: String?) : CodeBlockPart()
}

private fun splitCodeBlocks(text: String): List<CodeBlockPart> {
    val parts = mutableListOf<CodeBlockPart>()
    val regex = Regex("```(\\w*)\\n([\\s\\S]*?)```")
    var lastEnd = 0
    regex.findAll(text).forEach { match ->
        val before = text.substring(lastEnd, match.range.first).trim()
        if (before.isNotBlank()) parts.add(CodeBlockPart.Text(before))
        val code = match.groupValues[2].trim()
        val lang = match.groupValues[1].ifBlank { null }
        if (code.isNotBlank()) parts.add(CodeBlockPart.Code(code, lang))
        lastEnd = match.range.last + 1
    }
    val after = text.substring(lastEnd).trim()
    if (after.isNotBlank()) parts.add(CodeBlockPart.Text(after))
    return parts
}

private fun focusedContextSnippet(content: String, cursorLine: Int, cursorColumn: Int): String {
    if (content.isBlank()) return ""
    val lines = content.replace("\r\n", "\n").split('\n')
    if (lines.isEmpty()) return ""
    val lineIdx = cursorLine.coerceIn(0, lines.lastIndex)
    val colIdx = cursorColumn.coerceIn(0, lines[lineIdx].length)
    val from = maxOf(0, lineIdx - 12)
    val to = minOf(lines.lastIndex, lineIdx + 8)
    val rendered = mutableListOf<String>()
    for (idx in from..to) {
        val line = lines[idx]
        if (idx == lineIdx) {
            val prefix = line.take(colIdx)
            val suffix = line.drop(colIdx)
            rendered += "$prefix<cursor>$suffix"
        } else {
            rendered += line
        }
    }
    return rendered.joinToString("\n").take(6000)
}

private fun cleanStreamingLine(line: String): String? {
    val cleaned = line.trimEnd()
    if (cleaned.isBlank()) return null
    return AgentCli.cleanOutput(cleaned).takeIf { it.isNotBlank() }
}

private fun setClipboard(text: String) {
    val context = com.rk.utils.application ?: return
    val cm = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as? android.content.ClipboardManager ?: return
    cm.setPrimaryClip(android.content.ClipData.newPlainText("xed-agent", text))
    com.rk.utils.toast("Copied to clipboard")
}

private fun ConversationState.withSystemPrompt(agentName: String): ConversationState {
    if (systemPrompt.isNotBlank()) return this
    val prompt = buildString {
        appendLine("You are an AI coding assistant integrated into Xed-Editor ($agentName).")
        appendLine("You help users write, debug, refactor, and understand code.")
        appendLine("When suggesting code changes, explain what you changed and why.")
        appendLine("Use markdown formatting in your responses.")
        appendLine("When providing code, always specify the language in code fences.")
    }
    return copy(systemPrompt = prompt)
}
