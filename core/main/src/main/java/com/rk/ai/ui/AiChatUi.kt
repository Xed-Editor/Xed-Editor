package com.rk.ai.ui

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rk.ai.ChatMessage
import com.rk.ai.ConversationState

@Composable
fun ModernAiChat(
    conversation: ConversationState,
    onSend: (String) -> Unit,
    onStop: () -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var input by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    val color = MaterialTheme.colorScheme

    LaunchedEffect(conversation.messages.size, conversation.streamingText) {
        if (conversation.messages.isNotEmpty() || conversation.isStreaming) {
            listState.animateScrollToItem(Int.MAX_VALUE)
        }
    }

    Surface(
        modifier = modifier.fillMaxSize(),
        color = color.surface,
        tonalElevation = 0.dp,
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            ConversationHeader(
                messageCount = conversation.messages.size,
                isStreaming = conversation.isStreaming,
                onClear = onClear,
                onStop = onStop,
            )

            if (conversation.messages.isEmpty() && !conversation.isStreaming) {
                EmptyChatState(modifier = Modifier.weight(1f))
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(conversation.messages) { message ->
                        ChatBubble(message = message)
                    }

                    if (conversation.isStreaming && conversation.streamingText.isNotBlank()) {
                        item {
                            StreamingBubble(text = conversation.streamingText)
                        }
                    }

                    if (conversation.isLoading && !conversation.isStreaming) {
                        item {
                            LoadingIndicator()
                        }
                    }

                    if (conversation.error != null) {
                        item {
                            ErrorBubble(error = conversation.error)
                        }
                    }
                }
            }

            ChatInputBar(
                value = input,
                onValueChange = { input = it },
                onSend = {
                    val text = input.trim()
                    if (text.isNotBlank()) {
                        onSend(text)
                        input = ""
                    }
                },
                isStreaming = conversation.isStreaming,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun ConversationHeader(
    messageCount: Int,
    isStreaming: Boolean,
    onClear: () -> Unit,
    onStop: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        tonalElevation = 1.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(if (isStreaming) Color(0xFF4CAF50) else Color(0xFF9E9E9E))
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = if (isStreaming) "Generating..." else "$messageCount messages",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.weight(1f))
            if (isStreaming) {
                IconButton(onClick = onStop, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Outlined.Stop, "Stop", tint = Color(0xFFEF5350), modifier = Modifier.size(20.dp))
                }
            }
            IconButton(onClick = onClear, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Outlined.Delete, "Clear", modifier = Modifier.size(18.dp))
            }
        }
    }
}

@Composable
private fun EmptyChatState(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                Icons.Outlined.Chat,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
            )
            Spacer(Modifier.height(16.dp))
            Text(
                "Start a conversation",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
            )
            Text(
                "Ask about code, refactoring, or debugging",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
            )
        }
    }
}

@Composable
private fun ChatBubble(message: ChatMessage) {
    val isUser = message is ChatMessage.User
    val isSystem = message is ChatMessage.System
    val color = MaterialTheme.colorScheme

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
    ) {
        Surface(
            shape = RoundedCornerShape(
                topStart = if (isUser) 16.dp else 4.dp,
                topEnd = if (isUser) 4.dp else 16.dp,
                bottomStart = 16.dp,
                bottomEnd = 16.dp,
            ),
            color = when {
                isSystem -> Color(0xFFFFF3E0)
                isUser -> color.primaryContainer
                else -> color.surfaceVariant
            },
            modifier = Modifier.widthIn(max = 320.dp),
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text(
                    text = when {
                        isSystem -> "System"
                        isUser -> "You"
                        else -> "AI"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = when {
                        isSystem -> Color(0xFFE65100)
                        isUser -> color.onPrimaryContainer
                        else -> color.onSurfaceVariant
                    },
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = message.content,
                    style = MaterialTheme.typography.bodyMedium,
                    color = when {
                        isSystem -> Color(0xFF4E342E)
                        isUser -> color.onPrimaryContainer
                        else -> color.onSurfaceVariant
                    },
                )
            }
        }
    }
}

@Composable
private fun StreamingBubble(text: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start) {
        Surface(
            shape = RoundedCornerShape(4.dp, 16.dp, 16.dp, 16.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text(
                    "AI",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(4.dp))
                Row {
                    Text(
                        text = text,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.width(2.dp))
                    Box(
                        modifier = Modifier
                            .width(8.dp)
                            .height(16.dp)
                            .background(
                                MaterialTheme.colorScheme.primary,
                                RoundedCornerShape(1.dp),
                            )
                    )
                }
            }
        }
    }
}

@Composable
private fun LoadingIndicator() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Start,
    ) {
        Surface(
            shape = RoundedCornerShape(4.dp, 16.dp, 16.dp, 16.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
        ) {
            Row(
                modifier = Modifier.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(8.dp))
                Text("Thinking...", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun ErrorBubble(error: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Start,
    ) {
        Surface(
            shape = RoundedCornerShape(8.dp),
            color = Color(0xFFFFEBEE),
        ) {
            Row(modifier = Modifier.padding(12.dp)) {
                Icon(
                    Icons.Outlined.Error,
                    contentDescription = null,
                    tint = Color(0xFFC62828),
                    modifier = Modifier.size(20.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = error,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFFC62828),
                )
            }
        }
    }
}

@Composable
private fun ChatInputBar(
    value: String,
    onValueChange: (String) -> Unit,
    onSend: () -> Unit,
    isStreaming: Boolean,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 3.dp,
        shadowElevation = 8.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier.weight(1f),
                placeholder = { Text("Type a message...") },
                textStyle = MaterialTheme.typography.bodyMedium,
                maxLines = 4,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { if (!isStreaming) onSend() }),
                shape = RoundedCornerShape(24.dp),
            )
            Spacer(Modifier.width(8.dp))
            FilledIconButton(
                onClick = onSend,
                enabled = value.isNotBlank() && !isStreaming,
                modifier = Modifier.size(48.dp),
                shape = RoundedCornerShape(24.dp),
            ) {
                Icon(Icons.Outlined.Send, "Send")
            }
        }
    }
}
