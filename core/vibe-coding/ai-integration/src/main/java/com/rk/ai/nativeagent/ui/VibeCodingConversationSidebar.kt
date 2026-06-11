@file:OptIn(ExperimentalUuidApi::class)

package com.rk.ai.nativeagent.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.rk.ai.models.Conversation
import com.rk.ai.persistence.repo.ConversationRepository
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@Composable
fun VibeCodingConversationSidebar(
    conversationRepo: ConversationRepository,
    currentConversationId: Uuid?,
    assistantId: Uuid,
    onSelectConversation: (Conversation) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colorScheme = MaterialTheme.colorScheme
    var conversations by remember { mutableStateOf<List<Conversation>>(emptyList()) }
    var searchQuery by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        conversations = conversationRepo.getRecentConversations(
            assistantId = assistantId,
            limit = 50,
        )
    }

    Surface(
        modifier = modifier.fillMaxHeight(),
        color = colorScheme.surfaceContainerLow,
        tonalElevation = 2.dp,
    ) {
        Column(modifier = Modifier.padding(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = "History",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                )
                FilledTonalIconButton(
                    onClick = onDismiss,
                    modifier = Modifier.size(24.dp),
                ) {
                    Text("✕", style = MaterialTheme.typography.labelSmall)
                }
            }

            Spacer(Modifier.height(8.dp))

            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Search by title...", style = MaterialTheme.typography.bodySmall) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                textStyle = MaterialTheme.typography.bodySmall,
            )

            Spacer(Modifier.height(8.dp))

            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                val filtered = if (searchQuery.isBlank()) {
                    conversations
                } else {
                    conversations.filter {
                        it.title.contains(searchQuery, ignoreCase = true)
                    }
                }

                if (filtered.isEmpty()) {
                    item {
                        Text(
                            text = "No conversations",
                            style = MaterialTheme.typography.bodySmall,
                            color = colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier.padding(8.dp),
                        )
                    }
                }

                items(filtered, key = { it.id.toString() }) { conv ->
                    val isSelected = conv.id == currentConversationId
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelectConversation(conv) },
                        shape = RoundedCornerShape(8.dp),
                        color = if (isSelected) {
                            colorScheme.primaryContainer.copy(alpha = 0.3f)
                        } else {
                            colorScheme.surface
                        },
                    ) {
                        Column(modifier = Modifier.padding(8.dp)) {
                            Text(
                                text = conv.title.ifBlank { "Untitled" },
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                                ),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                color = if (isSelected) colorScheme.primary else colorScheme.onSurface,
                            )
                        }
                    }
                }
            }
        }
    }
}
