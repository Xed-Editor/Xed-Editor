package com.rk.ai.chat

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.rk.ai.icons.AiBrain
import com.rk.ai.icons.SparklesBig
import com.rk.ai.settings.AiSettings
import com.rk.ai.settings.PermissionMode
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement

@Composable
fun AiChatScreen(controller: AiChatController, modifier: Modifier = Modifier) {
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    var input by remember { mutableStateOf("") }
    val messages = controller.messages
    val lastMessage = messages.lastOrNull()

    val atBottom by
        remember {
            derivedStateOf {
                val info = listState.layoutInfo
                val lastVisible = info.visibleItemsInfo.lastOrNull()?.index ?: 0
                info.totalItemsCount == 0 || lastVisible >= info.totalItemsCount - 1
            }
        }

    LaunchedEffect(messages.size, lastMessage?.text, controller.pendingApproval) {
        if (messages.isNotEmpty() && atBottom) {
            listState.animateScrollToItem(messages.lastIndex)
        }
    }

    Box(modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            if (messages.isEmpty()) {
                EmptyState(onSuggestion = controller::send, modifier = Modifier.weight(1f))
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    contentPadding =
                        PaddingValues(start = 14.dp, end = 14.dp, top = 14.dp, bottom = 18.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    items(messages, key = { it.id }) { message ->
                        when (message.role) {
                            AiChatRole.Tool -> AiToolCard(message)
                            AiChatRole.User -> UserMessageBubble(message)
                            AiChatRole.Assistant -> AssistantMessageBubble(message)
                        }
                    }
                }
            }

            controller.pendingApproval?.let { approval ->
                ApprovalCard(approval = approval, onDecision = controller::approve)
            }

            controller.lastError?.let { ErrorBanner(it) }

            Composer(
                value = input,
                onValueChange = { input = it },
                running = controller.isRunning,
                onSend = {
                    val pending = input
                    input = ""
                    controller.send(pending)
                },
                onStop = controller::stop,
            )
        }

        if (messages.isNotEmpty() && !atBottom) {
            SmallFloatingActionButton(
                onClick = { scope.launch { listState.animateScrollToItem(messages.lastIndex) } },
                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.align(Alignment.BottomEnd).padding(end = 18.dp, bottom = 108.dp),
            ) {
                Icon(Icons.Filled.KeyboardArrowDown, contentDescription = "Scroll to latest")
            }
        }
    }
}

@Composable
private fun EmptyState(onSuggestion: (String) -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier =
            modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primaryContainer,
            modifier = Modifier.size(76.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = SparklesBig,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(36.dp),
                )
            }
        }

        Spacer(Modifier.height(18.dp))
        Text("Xed AI", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text(
            "Ask about this project. I can read files, edit them, search the code and run shell commands.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )

        Spacer(Modifier.height(26.dp))
        SUGGESTIONS.forEach { suggestion ->
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier =
                    Modifier.fillMaxWidth().padding(vertical = 4.dp).clickable {
                        onSuggestion(suggestion.prompt)
                    },
            ) {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                    Text(
                        suggestion.title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        suggestion.subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        Spacer(Modifier.height(22.dp))
        Text(
            "${AiSettings.modelId} · ${AiSettings.currentPermissionMode().label()}",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun UserMessageBubble(message: AiChatMessage) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
        Surface(
            color = MaterialTheme.colorScheme.primaryContainer,
            shape =
                RoundedCornerShape(
                    topStart = 18.dp,
                    topEnd = 6.dp,
                    bottomStart = 18.dp,
                    bottomEnd = 18.dp,
                ),
            modifier = Modifier.widthIn(max = 320.dp),
        ) {
            SelectionContainer {
                Text(
                    text = message.text,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                )
            }
        }
    }
}

@Composable
private fun AssistantMessageBubble(message: AiChatMessage) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start) {
        AiAvatar()
        Spacer(Modifier.width(8.dp))
        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant,
            shape =
                RoundedCornerShape(
                    topStart = 6.dp,
                    topEnd = 18.dp,
                    bottomStart = 18.dp,
                    bottomEnd = 18.dp,
                ),
            modifier = Modifier.weight(1f, fill = false),
        ) {
            Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                when {
                    message.text.isEmpty() && message.isStreaming -> TypingDots()
                    message.isStreaming ->
                        SelectionContainer {
                            Text(
                                text = message.text,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    else ->
                        AiMarkdownText(
                            markdown = message.text,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                }

                if (message.isStreaming && message.text.isNotEmpty()) {
                    Spacer(Modifier.height(6.dp))
                    StreamingCursor()
                }

                message.error?.let { error ->
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = error,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
    }
}

@Composable
private fun AiAvatar() {
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.primaryContainer,
        modifier = Modifier.size(28.dp),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = SparklesBig,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(16.dp),
            )
        }
    }
}

@Composable
private fun TypingDots() {
    val transition = rememberInfiniteTransition(label = "typing")
    Row(verticalAlignment = Alignment.CenterVertically) {
        repeat(3) { index ->
            val alpha by
                transition.animateFloat(
                    initialValue = 0.25f,
                    targetValue = 1f,
                    animationSpec =
                        infiniteRepeatable(
                            animation = tween(durationMillis = 600, delayMillis = index * 180),
                            repeatMode = RepeatMode.Reverse,
                        ),
                    label = "dot$index",
                )
            Box(
                Modifier.padding(horizontal = 2.dp)
                    .size(7.dp)
                    .background(
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = alpha),
                        CircleShape,
                    )
            )
        }
    }
}

@Composable
private fun StreamingCursor() {
    val transition = rememberInfiniteTransition(label = "cursor")
    val alpha by
        transition.animateFloat(
            initialValue = 0.2f,
            targetValue = 1f,
            animationSpec =
                infiniteRepeatable(
                    animation = tween(durationMillis = 500),
                    repeatMode = RepeatMode.Reverse,
                ),
            label = "cursorAlpha",
        )
    Box(
        Modifier.size(width = 8.dp, height = 14.dp)
            .background(MaterialTheme.colorScheme.primary.copy(alpha = alpha), RoundedCornerShape(2.dp))
    )
}

@Composable
private fun AiToolCard(message: AiChatMessage) {
    val status = message.toolStatus ?: ToolCallStatus.Running
    var expanded by remember { mutableStateOf(false) }
    val rotation by animateFloatAsState(if (expanded) 180f else 0f, label = "chevron")

    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth().animateContentSize(),
    ) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().clickable { expanded = !expanded },
            ) {
                Icon(
                    imageVector = Icons.Filled.KeyboardArrowDown,
                    contentDescription = if (expanded) "Collapse" else "Expand",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp).rotate(rotation),
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = message.toolName ?: "tool",
                    style = MaterialTheme.typography.labelLarge,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                if (status == ToolCallStatus.Running || status == ToolCallStatus.AwaitingApproval) {
                    CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                }
                StatusPill(status)
            }

            if (expanded) {
                message.toolArgs?.takeIf { it.isNotBlank() }?.let { args ->
                    Spacer(Modifier.height(10.dp))
                    CodePanel(title = "arguments", body = prettyJson(args))
                }
                message.diff?.takeIf { it.isNotBlank() }?.let { diff ->
                    Spacer(Modifier.height(10.dp))
                    AiDiffView(diff)
                }
                if (message.text.isNotBlank()) {
                    Spacer(Modifier.height(10.dp))
                    CodePanel(
                        title =
                            if (status == ToolCallStatus.Failed || status == ToolCallStatus.Denied) {
                                "error"
                            } else {
                                "result"
                            },
                        body = message.text.take(6000),
                    )
                }
            }

            if (!expanded) {
                message.diff?.takeIf { it.isNotBlank() }?.let { diff ->
                    Spacer(Modifier.height(10.dp))
                    AiDiffView(diff)
                }
            }
        }
    }
}

@Composable
private fun StatusPill(status: ToolCallStatus) {
    val label: String
    val color: Color
    when (status) {
        ToolCallStatus.AwaitingApproval -> {
            label = "awaiting approval"
            color = MaterialTheme.colorScheme.tertiary
        }
        ToolCallStatus.Running -> {
            label = "running"
            color = MaterialTheme.colorScheme.primary
        }
        ToolCallStatus.Success -> {
            label = "done"
            color = Color(0xFF4CAF50)
        }
        ToolCallStatus.Failed -> {
            label = "failed"
            color = MaterialTheme.colorScheme.error
        }
        ToolCallStatus.Denied -> {
            label = "denied"
            color = MaterialTheme.colorScheme.error
        }
    }

    Surface(color = color.copy(alpha = 0.16f), shape = RoundedCornerShape(50), contentColor = color) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
        )
    }
}

@Composable
private fun CodePanel(title: String, body: String) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(10.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(4.dp))
            SelectionContainer {
                Text(
                    text = body,
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}

@Composable
private fun ApprovalCard(approval: PendingApproval, onDecision: (Boolean) -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.tertiaryContainer,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Filled.Warning,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onTertiaryContainer,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "Approval required",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                )
            }

            Spacer(Modifier.height(6.dp))
            Text(
                text = approval.description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onTertiaryContainer,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = approval.toolName,
                style = MaterialTheme.typography.labelLarge,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onTertiaryContainer,
            )

            val diff = approval.diff
            if (!diff.isNullOrBlank()) {
                Spacer(Modifier.height(10.dp))
                AiDiffView(diff)
            } else if (approval.args.isNotBlank()) {
                Spacer(Modifier.height(10.dp))
                CodePanel(title = "arguments", body = prettyJson(approval.args))
            }

            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(onClick = { onDecision(true) }, modifier = Modifier.weight(1f)) {
                    Text("Allow")
                }
                OutlinedButton(onClick = { onDecision(false) }, modifier = Modifier.weight(1f)) {
                    Text("Deny")
                }
            }

            approval.filePath?.let { path ->
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "Allowing remembers $path for the rest of this chat.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                )
            }
        }
    }
}

@Composable
private fun ErrorBanner(message: String) {
    Surface(
        color = MaterialTheme.colorScheme.errorContainer,
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Filled.Warning,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
        }
    }
}

@Composable
private fun Composer(
    value: String,
    onValueChange: (String) -> Unit,
    running: Boolean,
    onSend: () -> Unit,
    onStop: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 3.dp,
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier.weight(1f),
            ) {
                BasicTextField(
                    value = value,
                    onValueChange = onValueChange,
                    textStyle =
                        MaterialTheme.typography.bodyMedium.copy(
                            color = MaterialTheme.colorScheme.onSurface
                        ),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    maxLines = 6,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                    decorationBox = { innerTextField ->
                        Box {
                            if (value.isEmpty()) {
                                Text(
                                    text = "Message Xed AI…",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            innerTextField()
                        }
                    },
                )
            }

            Spacer(Modifier.width(8.dp))

            FilledIconButton(
                onClick = { if (running) onStop() else onSend() },
                enabled = running || value.isNotBlank(),
                shape = CircleShape,
                modifier = Modifier.size(36.dp),
            ) {
                if (running) {
                    Box(
                        Modifier.size(14.dp)
                            .background(
                                MaterialTheme.colorScheme.onPrimary,
                                RoundedCornerShape(3.dp),
                            )
                    )
                } else {
                    Icon(
                        imageVector = Icons.Filled.Send,
                        contentDescription = "Send",
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
        }
    }
}

private fun prettyJson(raw: String): String =
    runCatching {
            Json { prettyPrint = true }
                .encodeToString(JsonElement.serializer(), Json.parseToJsonElement(raw))
        }
        .getOrDefault(raw)

private fun PermissionMode.label(): String =
    when (this) {
        PermissionMode.ASK -> "ask before changes"
        PermissionMode.ACCEPT_EDITS -> "auto-approve edits"
        PermissionMode.PLAN -> "plan only"
        PermissionMode.YOLO -> "autonomous"
    }

private data class Suggestion(val title: String, val subtitle: String, val prompt: String)

private val SUGGESTIONS =
    listOf(
        Suggestion(
            "Explain this project",
            "Summarise the structure and the key files",
            "Give me a quick overview of this project: what it does and how it is organised.",
        ),
        Suggestion(
            "Find a bug",
            "Search the code for likely problems",
            "Look through the project for a likely bug or rough edge and explain what you find.",
        ),
        Suggestion(
            "Add a feature",
            "Describe it and let me edit the files",
            "I want to add a small feature. Ask me what it should do, then implement it.",
        ),
    )
