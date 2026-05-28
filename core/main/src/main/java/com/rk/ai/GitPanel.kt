package com.rk.ai

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.rk.activities.main.MainActivity
import com.rk.activities.main.fileTreeViewModel
import com.rk.activities.main.gitViewModel
import com.rk.filetree.FileTreeTab
import com.rk.filetree.currentDrawerTab
import com.rk.git.ChangeType
import com.rk.git.GitChange
import com.rk.git.GitViewModel
import com.rk.resources.drawables
import com.rk.resources.strings
import com.rk.tabs.editor.EditorTab
import com.rk.utils.findGitRoot
import com.rk.utils.getGitColor
import java.io.File
import kotlinx.coroutines.launch
import org.eclipse.jgit.storage.file.FileRepositoryBuilder

@Composable
fun GitPanel(
    gitViewModel: GitViewModel,
    onRefresh: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val colorScheme = MaterialTheme.colorScheme

    var showBranchesMenu by remember { mutableStateOf(false) }
    var showNewBranchDialog by remember { mutableStateOf(false) }
    var newBranch by remember { mutableStateOf("") }
    var newBranchError by remember { mutableStateOf<String?>(null) }
    val invalidBranchMsg = stringResource(strings.value_invalid)

    val gitChanges = gitViewModel.currentRoot.value?.absolutePath?.let { gitViewModel.changes[it] } ?: emptyList()
    val hasCheckedChanges by remember(gitChanges) {
        derivedStateOf { gitChanges.count { it.isChecked } > 0 }
    }

    var changes by remember { mutableStateOf(listOf<GitChange>()) }
    var conflicts by remember { mutableStateOf(listOf<GitChange>()) }
    var untracked by remember { mutableStateOf(listOf<GitChange>()) }
    var changesExpanded by remember { mutableStateOf(true) }
    var untrackedExpanded by remember { mutableStateOf(true) }
    var conflictsExpanded by remember { mutableStateOf(true) }

    LaunchedEffect(gitChanges) {
        val trackedChanges = mutableListOf<GitChange>()
        val conflictingChanges = mutableListOf<GitChange>()
        val untrackedChanges = mutableListOf<GitChange>()
        for (change in gitChanges) {
            when (change.type) {
                ChangeType.ADDED, ChangeType.MODIFIED, ChangeType.DELETED -> trackedChanges.add(change)
                ChangeType.CONFLICTING -> conflictingChanges.add(change)
                ChangeType.UNTRACKED -> untrackedChanges.add(change)
            }
        }
        changes = trackedChanges
        conflicts = conflictingChanges
        untracked = untrackedChanges
    }

    val commitMessage = gitViewModel.currentRoot.value?.absolutePath?.let { gitViewModel.commitMessages[it] } ?: ""
    val amend = gitViewModel.currentRoot.value?.absolutePath?.let { gitViewModel.amends[it] } ?: false

    Column(modifier = Modifier.fillMaxSize()) {
        if (gitViewModel.currentRoot.value == null) {
            NoGitRepository(gitViewModel = gitViewModel)
        } else {
            GitBranchHeader(
                gitViewModel = gitViewModel,
                showBranchesMenu = showBranchesMenu,
                onToggleBranchesMenu = { showBranchesMenu = !showBranchesMenu },
                onSelectBranch = { branch ->
                    gitViewModel.checkout(branch)
                    showBranchesMenu = false
                },
                onNewBranch = { showNewBranchDialog = true },
                onPull = {
                    scope.launch {
                        gitViewModel.pull().join()
                        MainActivity.instance?.viewModel?.tabs?.filterIsInstance<EditorTab>()?.forEach {
                            if (findGitRoot(it.file.getAbsolutePath()) != null) it.refresh()
                        }
                    }
                },
                onFetch = { gitViewModel.fetch() },
                onPush = { gitViewModel.push(false) },
                onRefresh = onRefresh,
            )

            HorizontalDivider(color = colorScheme.outlineVariant.copy(alpha = 0.12f), thickness = 0.5.dp)

            if (gitViewModel.isLoading) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth().height(2.dp))
            }

            if (gitChanges.isNotEmpty()) {
                LazyColumn(
                    modifier = Modifier.weight(1f).horizontalScroll(rememberScrollState()),
                    state = rememberLazyListState(),
                    contentPadding = PaddingValues(top = 2.dp),
                ) {
                    if (conflicts.isNotEmpty()) {
                        item {
                            ConflictGroup(conflicts, conflictsExpanded, gitViewModel) { conflictsExpanded = !conflictsExpanded }
                        }
                    }
                    item {
                        ChangeGroup(
                            label = stringResource(strings.changes),
                            items = changes,
                            expanded = changesExpanded,
                            gitViewModel = gitViewModel,
                            onToggle = { changesExpanded = !changesExpanded },
                        )
                    }
                    item {
                        ChangeGroup(
                            label = stringResource(strings.untracked),
                            items = untracked,
                            expanded = untrackedExpanded,
                            gitViewModel = gitViewModel,
                            onToggle = { untrackedExpanded = !untrackedExpanded },
                        )
                    }
                }
            } else {
                Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            painter = painterResource(drawables.file),
                            contentDescription = null,
                            tint = colorScheme.onSurface.copy(alpha = 0.4f),
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            stringResource(strings.no_changes),
                            color = colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }

            HorizontalDivider()

            GitCommitArea(
                amend = amend,
                commitMessage = commitMessage,
                hasCheckedChanges = hasCheckedChanges,
                isLoading = gitViewModel.isLoading,
                onToggleAmend = { gitViewModel.toggleAmend(it) },
                onChangeCommitMessage = { gitViewModel.changeCommitMessage(it) },
                onCommit = { gitViewModel.commit() },
                onCommitAndPush = {
                    scope.launch {
                        gitViewModel.commit().join()
                        gitViewModel.push(false)
                    }
                },
            )
        }
    }

    if (showNewBranchDialog) {
        NewBranchDialog(
            currentBranch = gitViewModel.currentBranch,
            newBranch = newBranch,
            error = newBranchError,
            onValueChange = {
                newBranch = it
                newBranchError = if (it.isBlank()) invalidBranchMsg else null
            },
            onConfirm = {
                gitViewModel.checkoutNew(newBranch, gitViewModel.currentBranch)
                showNewBranchDialog = false
                newBranch = ""
                newBranchError = null
            },
            onDismiss = {
                showNewBranchDialog = false
                newBranch = ""
                newBranchError = null
            },
        )
    }
}

@Composable
private fun NoGitRepository(gitViewModel: GitViewModel) {
    val root = currentDrawerTab.let { tab ->
        if (tab is FileTreeTab) File(tab.root.getAbsolutePath()) else null
    }
    val hasGitDir = root != null && FileRepositoryBuilder().findGitDir(root).gitDir != null

    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(
                painter = painterResource(drawables.git),
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
            )
            Text(
                "No git repository",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                "Open a project with a .git directory",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
            )
            if (hasGitDir) {
                Button(onClick = { gitViewModel.loadRepository(root!!.absolutePath) }) {
                    Text("Load repository")
                }
            }
        }
    }
}

@Composable
private fun GitBranchHeader(
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
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(modifier = Modifier.weight(1f)) {
            TextButton(onClick = onToggleBranchesMenu, enabled = !gitViewModel.isLoading) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(painterResource(drawables.branch), contentDescription = null)
                    Spacer(Modifier.size(6.dp))
                    Text(
                        gitViewModel.currentBranch,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.size(4.dp))
                    Icon(painterResource(drawables.chevron_down), contentDescription = null)
                }
            }

            DropdownMenu(expanded = showBranchesMenu, onDismissRequest = onToggleBranchesMenu) {
                gitViewModel.getBranchList().forEach { branch ->
                    DropdownMenuItem(
                        text = { Text(branch) },
                        onClick = { onSelectBranch(branch) },
                    )
                }
                HorizontalDivider()
                DropdownMenuItem(
                    text = { Text(stringResource(strings.new_branch)) },
                    onClick = onNewBranch,
                )
            }
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onPull, enabled = !gitViewModel.isLoading) {
                Icon(painterResource(drawables.pull), contentDescription = stringResource(strings.pull))
            }
            IconButton(onClick = onFetch, enabled = !gitViewModel.isLoading) {
                Icon(painterResource(drawables.fetch), contentDescription = stringResource(strings.fetch))
            }
            IconButton(onClick = onPush, enabled = !gitViewModel.isLoading) {
                Icon(painterResource(drawables.push), contentDescription = stringResource(strings.push))
            }
            IconButton(onClick = onRefresh, enabled = !gitViewModel.isLoading) {
                Icon(Icons.Outlined.Refresh, contentDescription = "Refresh")
            }
        }
    }
}

@Composable
private fun ConflictGroup(
    conflicts: List<GitChange>,
    expanded: Boolean,
    gitViewModel: GitViewModel,
    onToggle: () -> Unit,
) {
    if (conflicts.isEmpty()) return

    val selectionState = when {
        conflicts.all { it.isChecked } -> ToggleableState.On
        conflicts.none { it.isChecked } -> ToggleableState.Off
        else -> ToggleableState.Indeterminate
    }

    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(onClick = onToggle)
                .padding(vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val rotation by animateFloatAsState(targetValue = if (!expanded) 0f else 90f, label = "rotation")
            Icon(
                painterResource(drawables.chevron_right),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                modifier = Modifier.size(14.dp).rotate(rotation),
            )
            Spacer(Modifier.width(3.dp))
            TriStateCheckbox(
                enabled = !gitViewModel.isLoading,
                state = selectionState,
                onClick = {
                    if (selectionState == ToggleableState.On) {
                        conflicts.forEach { gitViewModel.removeChange(it) }
                    } else {
                        conflicts.forEach { gitViewModel.addChange(it) }
                    }
                },
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = stringResource(strings.conflicts),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
            )
        }
        AnimatedVisibility(visible = expanded) { ChangesItemList(conflicts, gitViewModel) }
        Spacer(Modifier.height(4.dp))
    }
}

@Composable
private fun ChangeGroup(
    label: String,
    items: List<GitChange>,
    expanded: Boolean,
    gitViewModel: GitViewModel,
    onToggle: () -> Unit,
) {
    if (items.isEmpty()) return

    val selectionState = when {
        items.all { it.isChecked } -> ToggleableState.On
        items.none { it.isChecked } -> ToggleableState.Off
        else -> ToggleableState.Indeterminate
    }

    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(onClick = onToggle)
                .padding(vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val rotation by animateFloatAsState(targetValue = if (!expanded) 0f else 90f, label = "rotation")
            Icon(
                painterResource(drawables.chevron_right),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                modifier = Modifier.size(14.dp).rotate(rotation),
            )
            Spacer(Modifier.width(3.dp))
            TriStateCheckbox(
                enabled = !gitViewModel.isLoading,
                state = selectionState,
                onClick = {
                    if (selectionState == ToggleableState.On) {
                        items.forEach { gitViewModel.removeChange(it) }
                    } else {
                        items.forEach { gitViewModel.addChange(it) }
                    }
                },
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
        AnimatedVisibility(visible = expanded) { ChangesItemList(items, gitViewModel) }
        Spacer(Modifier.height(4.dp))
    }
}

@Composable
private fun ChangesItemList(items: List<GitChange>, gitViewModel: GitViewModel) {
    val colorScheme = MaterialTheme.colorScheme

    Column(modifier = Modifier.padding(start = 36.dp)) {
        items.forEach { change ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 1.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Checkbox(
                    enabled = !gitViewModel.isLoading,
                    checked = change.isChecked,
                    onCheckedChange = { gitViewModel.toggleChange(change) },
                    modifier = Modifier.size(20.dp),
                )

                val fileName = change.path.substringAfterLast("/")
                val gitColor = getGitColor(change.type)

                Text(
                    text = fileName,
                    style = MaterialTheme.typography.bodySmall,
                    color = gitColor ?: colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.weight(1f))
                Text(
                    text = change.path,
                    style = MaterialTheme.typography.labelSmall,
                    color = colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun GitCommitArea(
    amend: Boolean,
    commitMessage: String,
    hasCheckedChanges: Boolean,
    isLoading: Boolean,
    onToggleAmend: (Boolean) -> Unit,
    onChangeCommitMessage: (String) -> Unit,
    onCommit: () -> Unit,
    onCommitAndPush: () -> Unit,
) {
    Column(modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(28.dp)
                .toggleable(
                    value = amend,
                    enabled = !isLoading,
                    onValueChange = onToggleAmend,
                    role = Role.Checkbox,
                )
                .padding(horizontal = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Checkbox(
                checked = amend,
                enabled = !isLoading,
                onCheckedChange = null,
                modifier = Modifier.size(16.dp),
            )
            Spacer(Modifier.width(4.dp))
            Text(stringResource(strings.amend), style = MaterialTheme.typography.labelSmall)
        }
        OutlinedTextField(
            enabled = !isLoading,
            modifier = Modifier.fillMaxWidth().height(64.dp),
            value = commitMessage,
            onValueChange = onChangeCommitMessage,
            placeholder = { Text(stringResource(strings.commit_message)) },
            textStyle = MaterialTheme.typography.bodySmall,
        )
        Spacer(Modifier.height(2.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Button(
                enabled = !isLoading && commitMessage.isNotBlank() && hasCheckedChanges,
                modifier = Modifier.weight(1f),
                onClick = onCommit,
                contentPadding = ButtonDefaults.ButtonWithIconContentPadding,
            ) {
                Icon(
                    painterResource(drawables.commit),
                    contentDescription = null,
                    modifier = Modifier.size(ButtonDefaults.IconSize),
                )
                Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                Text(
                    stringResource(if (amend) strings.amend_commit else strings.commit),
                    maxLines = 1,
                    style = MaterialTheme.typography.labelMedium,
                )
            }
            OutlinedButton(
                enabled = !isLoading && commitMessage.isNotBlank() && hasCheckedChanges,
                modifier = Modifier.weight(1f),
                onClick = onCommitAndPush,
                contentPadding = ButtonDefaults.ButtonWithIconContentPadding,
            ) {
                Icon(
                    painterResource(drawables.push),
                    contentDescription = null,
                    modifier = Modifier.size(ButtonDefaults.IconSize),
                )
                Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                Text(
                    stringResource(if (amend) strings.amend_commit_and_push else strings.commit_and_push),
                    maxLines = 1,
                    style = MaterialTheme.typography.labelMedium,
                )
            }
        }
    }
}

@Composable
private fun NewBranchDialog(
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
                supportingText = if (error != null) {{ Text(error) }} else null,
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
