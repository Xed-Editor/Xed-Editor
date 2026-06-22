package com.rk.ai.git

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.rk.git.GitViewModel
import com.rk.resources.drawables
import com.rk.resources.strings

@Composable
fun GitBranchHeader(
    gitViewModel: GitViewModel,
    showBranchesMenu: Boolean,
    onToggleBranchesMenu: () -> Unit,
    onSelectBranch: (String) -> Unit,
    onNewBranch: () -> Unit,
    onPull: () -> Unit,
    onFetch: () -> Unit,
    onPush: () -> Unit,
    onRefresh: () -> Unit,
) {
    val colorScheme = MaterialTheme.colorScheme

    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(modifier = Modifier.weight(1f)) {
            TextButton(
                onClick = onToggleBranchesMenu,
                enabled = !gitViewModel.isLoading,
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                modifier = Modifier.fillMaxWidth().height(36.dp),
                shape = MaterialTheme.shapes.medium,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(painterResource(drawables.branch), contentDescription = null, modifier = Modifier.size(18.dp),
                        tint = colorScheme.primary)
                    Spacer(Modifier.width(6.dp))
                    Text(
                        gitViewModel.currentBranch.ifBlank { "unknown" },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                        color = colorScheme.onSurface,
                    )
                    Spacer(Modifier.width(4.dp))
                    Icon(painterResource(drawables.chevron_down), contentDescription = null, modifier = Modifier.size(18.dp),
                        tint = colorScheme.onSurfaceVariant)
                }
            }

            DropdownMenu(expanded = showBranchesMenu, onDismissRequest = onToggleBranchesMenu) {
                gitViewModel.getBranchList().forEach { branch ->
                    DropdownMenuItem(
                        text = { Text(branch) },
                        onClick = { onSelectBranch(branch) },
                    )
                }
                HorizontalDivider(modifier = Modifier.padding(horizontal = 12.dp))
                DropdownMenuItem(
                    text = { Text(stringResource(strings.new_branch), color = colorScheme.primary) },
                    onClick = onNewBranch,
                )
            }
        }

        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            CompactGitIconButton(onClick = onPull, enabled = !gitViewModel.isLoading) {
                Icon(painterResource(drawables.pull), contentDescription = stringResource(strings.pull), modifier = Modifier.size(18.dp),
                    tint = colorScheme.onSurfaceVariant)
            }
            CompactGitIconButton(onClick = onFetch, enabled = !gitViewModel.isLoading) {
                Icon(painterResource(drawables.fetch), contentDescription = stringResource(strings.fetch), modifier = Modifier.size(18.dp),
                    tint = colorScheme.onSurfaceVariant)
            }
            CompactGitIconButton(onClick = onPush, enabled = !gitViewModel.isLoading) {
                Icon(painterResource(drawables.push), contentDescription = stringResource(strings.push), modifier = Modifier.size(18.dp),
                    tint = colorScheme.onSurfaceVariant)
            }
            CompactGitIconButton(onClick = onRefresh, enabled = !gitViewModel.isLoading) {
                Icon(Icons.Outlined.Refresh, contentDescription = "Refresh", modifier = Modifier.size(18.dp),
                    tint = colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun CompactGitIconButton(
    onClick: () -> Unit,
    enabled: Boolean,
    content: @Composable () -> Unit,
) {
    IconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.size(34.dp),
    ) {
        content()
    }
}

@Composable
fun NewBranchDialog(
    currentBranch: String,
    newBranch: String,
    error: String?,
    onValueChange: (String) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(strings.new_branch)) },
        text = {
            OutlinedTextField(
                value = newBranch,
                onValueChange = onValueChange,
                label = { Text(stringResource(strings.new_branch_label, currentBranch)) },
                isError = error != null,
                supportingText = if (error != null) { { Text(error) } } else null,
                shape = MaterialTheme.shapes.medium,
            )
        },
        confirmButton = {
            TextButton(enabled = newBranch.isNotBlank() && error == null, onClick = onConfirm) {
                Text(stringResource(android.R.string.ok))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(strings.cancel))
            }
        },
    )
}
