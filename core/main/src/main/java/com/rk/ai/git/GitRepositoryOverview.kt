package com.rk.ai.git

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.rk.git.GitViewModel
import com.rk.resources.drawables
import com.rk.resources.strings
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun RepositoryOverview(
    gitViewModel: GitViewModel,
    colorScheme: ColorScheme,
) {
    val commitLog = gitViewModel.commitLog
    val branchCount = remember { derivedStateOf { gitViewModel.getBranchList().size } }
    val commitCount = remember { derivedStateOf { gitViewModel.getCommitCount() } }

    val changesList = gitViewModel.currentRoot.value?.absolutePath?.let { gitViewModel.changes[it] } ?: emptyList()
    val hasChanges = changesList.isNotEmpty()
    val stashes = gitViewModel.stashList.value

    var stashMessage by remember { mutableStateOf("") }

    LaunchedEffect(gitViewModel.currentRoot.value) {
        if (gitViewModel.currentRoot.value != null) {
            gitViewModel.loadStashList()
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxWidth(),
        state = rememberLazyListState(),
    ) {
        item {
            Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        painterResource(drawables.branch),
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = colorScheme.primary,
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        gitViewModel.currentBranch.ifBlank { "unknown" },
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                        color = colorScheme.onSurface,
                    )
                    Spacer(Modifier.width(8.dp))
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = colorScheme.primaryContainer.copy(alpha = 0.5f),
                    ) {
                        Text(
                            "up to date",
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = colorScheme.onPrimaryContainer,
                        )
                    }
                }

                Spacer(Modifier.height(12.dp))

                Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = colorScheme.surfaceContainerHigh,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                    ) {
                        StatItem(value = "${commitCount.value.coerceAtLeast(0)}", label = "commits", colorScheme = colorScheme)
                        StatItem(value = "${branchCount.value}", label = "branches", colorScheme = colorScheme)
                    }
                }

                // Stash current changes section
                if (hasChanges) {
                    Spacer(Modifier.height(16.dp))
                    Text(
                        "Stash Changes",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = stashMessage,
                            onValueChange = { stashMessage = it },
                            placeholder = { Text("Stash message (optional)", style = MaterialTheme.typography.bodySmall, color = colorScheme.onSurfaceVariant.copy(alpha = 0.5f)) },
                            modifier = Modifier.weight(1f).height(48.dp),
                            shape = MaterialTheme.shapes.medium,
                            textStyle = MaterialTheme.typography.bodySmall,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = colorScheme.primary.copy(alpha = 0.5f),
                                unfocusedBorderColor = colorScheme.outlineVariant,
                            ),
                        )
                        Button(
                            onClick = {
                                gitViewModel.stashChanges(stashMessage)
                                stashMessage = ""
                            },
                            enabled = !gitViewModel.isLoading,
                            shape = MaterialTheme.shapes.medium,
                            modifier = Modifier.height(48.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp)
                        ) {
                            Text("Stash", style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }

                // Stashed list section
                if (stashes.isNotEmpty()) {
                    Spacer(Modifier.height(16.dp))
                    Text(
                        "Stashed Changes (${stashes.size})",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(8.dp))
                    stashes.forEach { stash ->
                        Surface(
                            shape = MaterialTheme.shapes.small,
                            color = colorScheme.surfaceContainerLow,
                            modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        stash.message.ifBlank { "Stash #${stash.index}" },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = colorScheme.onSurface,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            "stash@{${stash.index}}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = colorScheme.primary.copy(alpha = 0.7f),
                                        )
                                        Spacer(Modifier.width(8.dp))
                                        Text(
                                            formatTimeAgo(stash.date),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                                        )
                                    }
                                }

                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    TextButton(
                                        onClick = { gitViewModel.applyStash(stash.index) },
                                        enabled = !gitViewModel.isLoading,
                                        contentPadding = PaddingValues(horizontal = 8.dp),
                                        modifier = Modifier.height(28.dp)
                                    ) {
                                        Text("Apply", style = MaterialTheme.typography.labelSmall)
                                    }

                                    TextButton(
                                        onClick = { gitViewModel.popStash(stash.index) },
                                        enabled = !gitViewModel.isLoading,
                                        contentPadding = PaddingValues(horizontal = 8.dp),
                                        modifier = Modifier.height(28.dp)
                                    ) {
                                        Text("Pop", style = MaterialTheme.typography.labelSmall)
                                    }

                                    IconButton(
                                        onClick = { gitViewModel.dropStash(stash.index) },
                                        enabled = !gitViewModel.isLoading,
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(
                                            painter = painterResource(drawables.close),
                                            contentDescription = "Drop Stash",
                                            tint = colorScheme.error.copy(alpha = 0.7f),
                                            modifier = Modifier.size(14.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))

                if (commitLog.value.isNotEmpty()) {
                    Text(
                        "Recent Activity",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(8.dp))

                    commitLog.value.take(5).forEach { commit ->
                        Surface(
                            shape = MaterialTheme.shapes.small,
                            color = colorScheme.surfaceContainerLow,
                            modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.Top,
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        commit.message,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = colorScheme.onSurface,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    Spacer(Modifier.height(2.dp))
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            commit.hash,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = colorScheme.primary.copy(alpha = 0.7f),
                                        )
                                        Spacer(Modifier.width(8.dp))
                                        Text(
                                            commit.author,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                        )
                                        Spacer(Modifier.width(8.dp))
                                        Text(
                                            formatTimeAgo(commit.date),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                                        )
                                    }
                                }
                            }
                        }
                    }
                } else if (gitViewModel.isLogLoading) {
                    Box(modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    }
                } else {
                    Box(modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                painter = painterResource(drawables.file),
                                contentDescription = null,
                                tint = colorScheme.onSurface.copy(alpha = 0.2f),
                            )
                            Spacer(Modifier.height(8.dp))
                            Text(
                                stringResource(strings.no_changes),
                                color = colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StatItem(value: String, label: String, colorScheme: ColorScheme) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            value,
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            color = colorScheme.onSurface,
        )
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
        )
    }
}

private fun formatTimeAgo(date: Date): String {
    val now = System.currentTimeMillis()
    val diff = now - date.time
    val minutes = diff / 60000
    val hours = minutes / 60
    val days = hours / 24
    return when {
        minutes < 1 -> "just now"
        minutes < 60 -> "${minutes}m ago"
        hours < 24 -> "${hours}h ago"
        days < 7 -> "${days}d ago"
        else -> SimpleDateFormat("MMM d", Locale.getDefault()).format(date)
    }
}
