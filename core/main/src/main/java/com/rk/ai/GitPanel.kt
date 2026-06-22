package com.rk.ai

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.rk.activities.main.MainActivity
import com.rk.ai.git.*
import com.rk.filetree.FileTreeTab
import com.rk.filetree.currentDrawerTab
import com.rk.git.ChangeType
import com.rk.git.GitChange
import com.rk.git.GitViewModel
import com.rk.resources.drawables
import com.rk.resources.strings
import com.rk.tabs.editor.EditorTab
import com.rk.utils.findGitRoot
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

    val staged = remember(gitChanges) { gitChanges.filter { it.isChecked } }
    val unstaged = remember(gitChanges) { gitChanges.filter { !it.isChecked && it.type != ChangeType.UNTRACKED && it.type != ChangeType.CONFLICTING } }
    val untrackedChanges = remember(gitChanges) { gitChanges.filter { !it.isChecked && it.type == ChangeType.UNTRACKED } }
    val conflicts = remember(gitChanges) { gitChanges.filter { it.type == ChangeType.CONFLICTING } }

    var stagedExpanded by remember { mutableStateOf(true) }
    var changesExpanded by remember { mutableStateOf(true) }
    var untrackedExpanded by remember { mutableStateOf(true) }
    var conflictsExpanded by remember { mutableStateOf(true) }

    LaunchedEffect(gitViewModel.currentRoot.value) {
        if (gitViewModel.currentRoot.value != null) {
            gitViewModel.loadCommitLog(maxCount = 10)
        }
    }

    val commitMessage = gitViewModel.currentRoot.value?.absolutePath?.let { gitViewModel.commitMessages[it] } ?: ""
    val amend = gitViewModel.currentRoot.value?.absolutePath?.let { gitViewModel.amends[it] } ?: false

    Column(modifier = Modifier.fillMaxSize().background(colorScheme.surface)) {
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

            HorizontalDivider(color = colorScheme.outlineVariant.copy(alpha = 0.1f), thickness = 0.5.dp)

            if (gitViewModel.isLoading) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth().height(2.dp))
            }

            if (gitChanges.isNotEmpty()) {
                LazyColumn(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    state = rememberLazyListState(),
                ) {
                    if (conflicts.isNotEmpty()) {
                        item {
                            ConflictGroup(conflicts, conflictsExpanded, gitViewModel) { conflictsExpanded = !conflictsExpanded }
                        }
                    }
                    if (staged.isNotEmpty()) {
                        item {
                            ChangeGroup(
                                label = "Staged Changes",
                                items = staged,
                                expanded = stagedExpanded,
                                gitViewModel = gitViewModel,
                                isStaged = true,
                                onToggle = { stagedExpanded = !stagedExpanded },
                            )
                        }
                    }
                    if (unstaged.isNotEmpty()) {
                        item {
                            ChangeGroup(
                                label = stringResource(strings.changes),
                                items = unstaged,
                                expanded = changesExpanded,
                                gitViewModel = gitViewModel,
                                onToggle = { changesExpanded = !changesExpanded },
                            )
                        }
                    }
                    if (untrackedChanges.isNotEmpty()) {
                        item {
                            ChangeGroup(
                                label = stringResource(strings.untracked),
                                items = untrackedChanges,
                                expanded = untrackedExpanded,
                                gitViewModel = gitViewModel,
                                onToggle = { untrackedExpanded = !untrackedExpanded },
                            )
                        }
                    }
                }
            } else {
                Box(Modifier.weight(1f)) {
                    RepositoryOverview(gitViewModel = gitViewModel, colorScheme = colorScheme)
                }
            }

            HorizontalDivider(color = colorScheme.outlineVariant.copy(alpha = 0.1f), thickness = 0.5.dp)

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

    Box(modifier = Modifier.fillMaxWidth().padding(vertical = 48.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(
                painter = painterResource(drawables.git),
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f),
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
                Button(onClick = { gitViewModel.loadRepository(root!!.absolutePath) },
                    shape = MaterialTheme.shapes.medium,
                ) {
                    Text("Load repository")
                }
            }
        }
    }
}
