@file:OptIn(ExperimentalUuidApi::class)
package com.rk.ai.nativeagent.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.rk.ai.nativeagent.engine.SessionNode
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@Composable
internal fun VibeCodingSessionTabs(
    sessionTree: List<SessionNode>,
    activeSessionId: Uuid?,
    isProcessing: Boolean,
    onSwitchSession: (Uuid) -> Unit,
    onNewBranch: () -> Unit,
    onRenameSession: ((Uuid, String) -> Unit)? = null,
    onCloseSession: ((Uuid) -> Unit)? = null,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
    ) {
        LazyRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            items(sessionTree) { node ->
                val isActive = node.id == activeSessionId
                SessionTab(
                    title = node.title,
                    isActive = isActive,
                    isBranch = node.parentId != null,
                    onClick = { onSwitchSession(node.id) },
                    onRename = { onRenameSession?.invoke(node.id, node.title) },
                    onClose = { onCloseSession?.invoke(node.id) },
                )
            }
            item {
                FilledTonalIconButton(
                    onClick = onNewBranch,
                    modifier = Modifier.size(24.dp),
                    enabled = !isProcessing,
                ) {
                    Icon(
                        Icons.Outlined.Add,
                        contentDescription = "New Branch",
                        modifier = Modifier.size(12.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun SessionTab(
    title: String,
    isActive: Boolean,
    isBranch: Boolean,
    onClick: () -> Unit,
    onRename: (() -> Unit)? = null,
    onClose: (() -> Unit)? = null,
) {
    val colorScheme = MaterialTheme.colorScheme
    val bg = if (isActive) colorScheme.surfaceContainerHigh
    else colorScheme.surfaceContainerLow

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(6.dp),
        color = bg,
        modifier = Modifier.widthIn(max = 160.dp),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (isBranch) {
                Icon(
                    Icons.Outlined.SubdirectoryArrowRight,
                    contentDescription = null,
                    modifier = Modifier.size(10.dp),
                    tint = colorScheme.primary,
                )
                Spacer(Modifier.width(2.dp))
            }
            Text(
                text = title,
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
            if (isActive && onRename != null) {
                IconButton(
                    onClick = onRename,
                    modifier = Modifier.size(16.dp),
                ) {
                    Icon(
                        Icons.Outlined.Edit,
                        contentDescription = "Rename",
                        modifier = Modifier.size(10.dp),
                        tint = colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                    )
                }
            }
            if (onClose != null) {
                IconButton(
                    onClick = onClose,
                    modifier = Modifier.size(16.dp),
                ) {
                    Icon(
                        Icons.Outlined.Close,
                        contentDescription = "Close session",
                        modifier = Modifier.size(10.dp),
                        tint = colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                    )
                }
            }
        }
    }
}
