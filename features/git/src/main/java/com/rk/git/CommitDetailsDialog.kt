package com.rk.git

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.rk.resources.drawables
import com.rk.resources.strings
import com.rk.utils.copyToClipboard
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Detail view for a single commit: full hash (with copy), author, date, full message, changed files, and an action to
 * check the commit out.
 */
@Composable
fun CommitDetailsDialog(
    commit: GitCommit,
    viewModel: GitViewModel,
    onDismiss: () -> Unit,
    onOpenFileDiff: (GitChange) -> Unit = {},
) {
    val dateFormatter = remember { SimpleDateFormat("EEE MMM dd, yyyy HH:mm", Locale.getDefault()) }
    var changes by remember(commit.hash) { mutableStateOf<List<GitChange>?>(null) }
    val isLoading by viewModel.isLoading.collectAsState()

    LaunchedEffect(commit.hash) {
        viewModel.getChangesForCommit(commit) { result -> changes = result }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = commit.message,
                style = MaterialTheme.typography.titleMedium,
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                if (commit.author.isNotBlank()) {
                    Text(
                        text = commit.author,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                Text(
                    text = dateFormatter.format(Date(commit.date)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = commit.hash,
                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )

                    Spacer(Modifier.width(4.dp))

                    IconButton(onClick = { copyToClipboard("Commit hash", commit.hash) }) {
                        Icon(
                            painter = painterResource(drawables.copy),
                            contentDescription = stringResource(strings.copy_hash),
                        )
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                Text(
                    text = stringResource(strings.changes),
                    style = MaterialTheme.typography.labelLarge,
                )

                val currentChanges = changes
                when {
                    currentChanges == null -> {
                        Row(
                            modifier = Modifier.fillMaxWidth().height(48.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            CircularProgressIndicator(modifier = Modifier.height(20.dp))
                        }
                    }
                    currentChanges.isEmpty() -> {
                        Text(
                            text = stringResource(strings.no_changes),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    else -> {
                        LazyColumn {
                            items(currentChanges, key = { it.path }) { change ->
                                ChangesFileRow(
                                    change = change,
                                    underlineColor = null,
                                    onClick = { onOpenFileDiff(change) },
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = !isLoading,
                onClick = {
                    viewModel.checkout(commit.hash)
                    onDismiss()
                },
            ) {
                Text(stringResource(strings.checkout))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(strings.close))
            }
        },
    )
}
