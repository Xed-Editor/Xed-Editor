package com.rk.git

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SecondaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.TriStateCheckbox
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import com.rk.activities.main.MainActivity
import com.rk.activities.main.filesByTab
import com.rk.activities.main.ui.drawerStateRef
import com.rk.activities.main.ui.fileTreeViewModel
import com.rk.components.SingleInputDialog
import com.rk.components.XedDropdownMenuItem
import com.rk.components.getDrawerWidth
import com.rk.drawer.DrawerTab
import com.rk.feature.FeatureRegistry
import com.rk.file.toFileWrapper
import com.rk.filetree.FileTreeTab
import com.rk.icons.Icon
import com.rk.resources.drawables
import com.rk.resources.getString
import com.rk.resources.strings
import com.rk.theme.greenStatus
import com.rk.utils.getUnderlineColor
import kotlinx.coroutines.launch
import org.eclipse.jgit.storage.file.FileRepositoryBuilder
import java.io.File

class GitTab(val viewModel: GitViewModel) : DrawerTab() {
    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    override fun Content(modifier: Modifier) {
        var showBranchesMenu by remember { mutableStateOf(false) }
        var showNewBranchDialog by remember { mutableStateOf(false) }

        var showDeleteBranchDialog by remember { mutableStateOf(false) }
        var branchToDelete by remember { mutableStateOf("") }

        var showRenameBranchDialog by remember { mutableStateOf(false) }
        var branchToRename by remember { mutableStateOf("") }
        var newBranchName by remember { mutableStateOf("") }
        var renameBranchError by remember { mutableStateOf<String?>(null) }

        var showPushConfirmDialog by remember { mutableStateOf(false) }
        var selectedTabIndex by remember { mutableIntStateOf(0) }

        var force by remember { mutableStateOf(false) }

        val interactionSource = remember { MutableInteractionSource() }
        val scope = rememberCoroutineScope()

        var newBranch by remember { mutableStateOf("") }
        var newBranchError by remember { mutableStateOf<String?>(null) }

        val gitChanges = viewModel.currentRoot.value?.absolutePath?.let { viewModel.changes[it] } ?: emptyList()
        val hasCheckedChanges by remember(gitChanges) { derivedStateOf { gitChanges.count { it.isChecked } > 0 } }

        val changes by
            remember(gitChanges) {
                derivedStateOf {
                    gitChanges.filter {
                        it.type in
                            listOf(
                                ChangeType.ADDED,
                                ChangeType.MODIFIED,
                                ChangeType.RENAMED,
                                ChangeType.DELETED,
                            )
                    }
                }
            }
        val conflicts by
            remember(gitChanges) {
                derivedStateOf { gitChanges.filter { it.type == ChangeType.CONFLICTING } }
            }
        val untracked by
            remember(gitChanges) {
                derivedStateOf { gitChanges.filter { it.type == ChangeType.UNTRACKED } }
            }

        var changesExpanded by remember { mutableStateOf(true) }
        var untrackedExpanded by remember { mutableStateOf(true) }
        var conflictsExpanded by remember { mutableStateOf(true) }

        val commitMessage = viewModel.currentRoot.value?.absolutePath?.let { viewModel.commitMessages[it] } ?: ""
        val amend = viewModel.currentRoot.value?.absolutePath?.let { viewModel.amends[it] } ?: false

        var selectedCommit by remember { mutableStateOf<GitCommit?>(null) }

        Column(modifier = modifier.fillMaxSize()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(modifier = Modifier.weight(1f)) {
                    TextButton(onClick = { showBranchesMenu = true }, enabled = !viewModel.isLoading) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.wrapContentWidth()) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f, fill = false),
                            ) {
                                Icon(painterResource(drawables.branch), contentDescription = null)
                                Spacer(Modifier.size(8.dp))
                                Text(
                                    viewModel.currentBranch,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    softWrap = false,
                                )
                            }

                            Spacer(Modifier.size(4.dp))
                            Icon(painterResource(drawables.chevron_down), contentDescription = null)
                        }
                    }

                    DropdownMenu(expanded = showBranchesMenu, onDismissRequest = { showBranchesMenu = false }) {
                        viewModel.getBranchList().forEach { branch ->
                            var showBranchOptions by remember { mutableStateOf(false) }

                            Box(modifier = Modifier.fillMaxWidth()) {
                                XedDropdownMenuItem(
                                    text = {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            RadioButton(
                                                selected = branch == viewModel.currentBranch,
                                                onClick = null,
                                            )
                                            Spacer(Modifier.width(12.dp))
                                            Text(branch, modifier = Modifier.weight(1f))
                                        }
                                    },
                                    onClick = { showBranchOptions = true },
                                    trailingIcon = {
                                        Icon(
                                            painterResource(drawables.chevron_right),
                                            contentDescription = null,
                                        )
                                    },
                                )

                                DropdownMenu(
                                    expanded = showBranchOptions,
                                    onDismissRequest = { showBranchOptions = false },
                                    offset = DpOffset(x = 220.dp, y = 0.dp),
                                ) {
                                    XedDropdownMenuItem(
                                        leadingIcon = {
                                            Icon(
                                                imageVector = Icons.Outlined.Check,
                                                contentDescription = null,
                                            )
                                        },
                                        text = { Text(stringResource(strings.checkout)) },
                                        onClick = {
                                            viewModel.checkout(branch)
                                            showBranchOptions = false
                                            showBranchesMenu = false
                                        },
                                    )
                                    XedDropdownMenuItem(
                                        leadingIcon = {
                                            Icon(painterResource(drawables.merge), contentDescription = null)
                                        },
                                        text = { Text(stringResource(strings.merge)) },
                                        onClick = {
                                            viewModel.merge(branch)
                                            showBranchOptions = false
                                            showBranchesMenu = false
                                        },
                                    )
                                    XedDropdownMenuItem(
                                        leadingIcon = {
                                            Icon(painterResource(drawables.rebase), contentDescription = null)
                                        },
                                        text = { Text(stringResource(strings.rebase)) },
                                        onClick = {
                                            viewModel.rebase(branch)
                                            showBranchOptions = false
                                            showBranchesMenu = false
                                        },
                                    )

                                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                                    XedDropdownMenuItem(
                                        leadingIcon = {
                                            Icon(
                                                imageVector = Icons.Outlined.Edit,
                                                contentDescription = null,
                                            )
                                        },
                                        text = { Text(stringResource(strings.rename)) },
                                        onClick = {
                                            branchToRename = branch
                                            newBranchName = branch
                                            showRenameBranchDialog = true
                                            showBranchOptions = false
                                            showBranchesMenu = false
                                        },
                                    )
                                    XedDropdownMenuItem(
                                        enabled = branch != viewModel.currentBranch,
                                        colors =
                                            MenuDefaults.itemColors()
                                                .copy(
                                                    textColor = MaterialTheme.colorScheme.error,
                                                    leadingIconColor = MaterialTheme.colorScheme.error,
                                                    disabledTextColor =
                                                        MaterialTheme.colorScheme.error.copy(alpha = 0.4f),
                                                    disabledLeadingIconColor =
                                                        MaterialTheme.colorScheme.error.copy(alpha = 0.4f),
                                                ),
                                        leadingIcon = {
                                            Icon(
                                                imageVector = Icons.Outlined.Delete,
                                                contentDescription = stringResource(strings.delete),
                                            )
                                        },
                                        text = {
                                            Text(stringResource(strings.delete))
                                        },
                                        onClick = {
                                            branchToDelete = branch
                                            showDeleteBranchDialog = true
                                            showBranchOptions = false
                                            showBranchesMenu = false
                                        },
                                    )
                                }
                            }
                        }
                        XedDropdownMenuItem(
                            text = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(imageVector = Icons.Outlined.Add, contentDescription = null)
                                    Spacer(Modifier.width(12.dp))
                                    Text(stringResource(strings.new_branch))
                                }
                            },
                            onClick = {
                                showBranchesMenu = false
                                showNewBranchDialog = true
                            },
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    TooltipBox(
                        positionProvider =
                            TooltipDefaults.rememberTooltipPositionProvider(positioning = TooltipAnchorPosition.Below),
                        tooltip = { PlainTooltip { Text(stringResource(strings.pull)) } },
                        state = rememberTooltipState(),
                    ) {
                        IconButton(
                            onClick = {
                                scope.launch {
                                    viewModel.pull().join()

                                    val mainViewModel = MainActivity.instance?.viewModel ?: return@launch
                                    mainViewModel.editorTabs.filesByTab().forEach { (tab, file) ->
                                        findGitRoot(file.getAbsolutePath())?.let { tab.refresh() }
                                    }
                                }
                            },
                            enabled = !viewModel.isLoading,
                        ) {
                            BadgedBox(
                                badge = {
                                    if (viewModel.behindCount > 0) {
                                        Badge(containerColor = MaterialTheme.colorScheme.primary)
                                    }
                                }
                            ) {
                                Icon(painterResource(drawables.pull), contentDescription = stringResource(strings.pull))
                            }
                        }
                    }

                    TooltipBox(
                        positionProvider =
                            TooltipDefaults.rememberTooltipPositionProvider(positioning = TooltipAnchorPosition.Below),
                        tooltip = { PlainTooltip { Text(stringResource(strings.fetch)) } },
                        state = rememberTooltipState(),
                    ) {
                        IconButton(onClick = { viewModel.fetch() }, enabled = !viewModel.isLoading) {
                            Icon(painterResource(drawables.fetch), contentDescription = stringResource(strings.fetch))
                        }
                    }

                    TooltipBox(
                        positionProvider =
                            TooltipDefaults.rememberTooltipPositionProvider(positioning = TooltipAnchorPosition.Below),
                        tooltip = { PlainTooltip { Text(stringResource(strings.push)) } },
                        state = rememberTooltipState(),
                    ) {
                        BadgedBox(
                            badge = {
                                if (viewModel.aheadCount > 0) {
                                    Badge(containerColor = MaterialTheme.colorScheme.greenStatus)
                                }
                            }
                        ) {
                            IconButton(onClick = { showPushConfirmDialog = true }, enabled = !viewModel.isLoading) {
                                Icon(painterResource(drawables.push), contentDescription = stringResource(strings.push))
                            }
                        }
                    }
                }
            }

            SecondaryTabRow(
                selectedTabIndex = selectedTabIndex,
                modifier = Modifier.fillMaxWidth(),
                divider = {},
            ) {
                Tab(
                    selected = selectedTabIndex == 0,
                    onClick = { selectedTabIndex = 0 },
                    selectedContentColor = MaterialTheme.colorScheme.primary,
                    unselectedContentColor = LocalContentColor.current,
                    text = {
                        Text(
                            stringResource(strings.changes),
                            style = MaterialTheme.typography.titleSmall,
                        )
                    },
                )
                Tab(
                    selected = selectedTabIndex == 1,
                    onClick = {
                        selectedTabIndex = 1
                        viewModel.loadHistory()
                    },
                    selectedContentColor = MaterialTheme.colorScheme.primary,
                    unselectedContentColor = LocalContentColor.current,
                    text = {
                        Text(
                            stringResource(strings.history),
                            style = MaterialTheme.typography.titleSmall,
                        )
                    },
                )
            }

            Box(modifier = Modifier.fillMaxWidth().height(4.dp)) {
                if (viewModel.isLoading) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxSize())
                } else {
                    HorizontalDivider()
                }
            }

            if (selectedTabIndex == 0) {
                if (gitChanges.isNotEmpty()) {
                    LazyColumn(
                        modifier = Modifier.weight(1f, fill = true).horizontalScroll(rememberScrollState()),
                        state = rememberLazyListState(),
                        contentPadding = PaddingValues(top = 8.dp),
                    ) {
                        item { ConflictsList(conflicts, conflictsExpanded) { conflictsExpanded = !conflictsExpanded } }
                        item { ChangesList(changes, changesExpanded) { changesExpanded = !changesExpanded } }
                        item { UntrackedList(untracked, untrackedExpanded) { untrackedExpanded = !untrackedExpanded } }
                    }
                } else {
                    Column(
                        modifier = Modifier.fillMaxSize().weight(1f),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Icon(
                            painter = painterResource(drawables.file),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurface,
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(stringResource(strings.no_changes), color = MaterialTheme.colorScheme.onSurface)
                    }
                }

                HorizontalDivider()

                Row(
                    modifier =
                        Modifier.fillMaxWidth()
                            .height(40.dp)
                            .toggleable(
                                value = amend,
                                enabled = !viewModel.isLoading,
                                onValueChange = { viewModel.toggleAmend(it) },
                                role = Role.Checkbox,
                                indication = null,
                                interactionSource = interactionSource,
                            )
                            .padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(
                        checked = amend,
                        enabled = !viewModel.isLoading,
                        interactionSource = interactionSource,
                        onCheckedChange = null,
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(strings.amend))
                }
                OutlinedTextField(
                    enabled = !viewModel.isLoading,
                    modifier = Modifier.fillMaxWidth().height(120.dp),
                    value = commitMessage,
                    onValueChange = { viewModel.changeCommitMessage(it) },
                    placeholder = { Text(stringResource(strings.commit_message)) },
                )

                Column(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                    Button(
                        enabled = !viewModel.isLoading && commitMessage.isNotBlank() && hasCheckedChanges,
                        modifier = Modifier.fillMaxWidth(),
                        onClick = { viewModel.commit() },
                        contentPadding = ButtonDefaults.ButtonWithIconContentPadding,
                    ) {
                        Icon(
                            painterResource(drawables.commit),
                            contentDescription = stringResource(strings.commit),
                            modifier = Modifier.size(ButtonDefaults.IconSize),
                        )
                        Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                        Text(
                            stringResource(if (amend) strings.amend_commit else strings.commit),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    OutlinedButton(
                        enabled = !viewModel.isLoading && commitMessage.isNotBlank() && hasCheckedChanges,
                        modifier = Modifier.fillMaxWidth(),
                        onClick = {
                            scope.launch {
                                viewModel.commit().join()
                                showPushConfirmDialog = true
                            }
                        },
                        contentPadding = ButtonDefaults.ButtonWithIconContentPadding,
                    ) {
                        Icon(
                            painterResource(drawables.push),
                            contentDescription = stringResource(strings.push),
                            modifier = Modifier.size(ButtonDefaults.IconSize),
                        )
                        Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                        Text(
                            stringResource(if (amend) strings.amend_commit_and_push else strings.commit_and_push),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            } else {
                GitGraphView(
                    commits = viewModel.commitHistory,
                    modifier = Modifier.weight(1f),
                    onCommitClick = { selectedCommit = it },
                )
            }
        }

        if (showNewBranchDialog) {
            SingleInputDialog(
                title = stringResource(id = strings.new_branch),
                inputLabel = stringResource(id = strings.new_branch_label, viewModel.currentBranch),
                inputValue = newBranch,
                errorMessage = newBranchError,
                confirmText = stringResource(strings.ok),
                onInputValueChange = {
                    newBranch = it
                    newBranchError =
                        when {
                            newBranch.isBlank() -> strings.value_empty_err.getString()
                            else -> null
                        }
                },
                onConfirm = { viewModel.checkoutNew(newBranch, viewModel.currentBranch) },
                onFinish = {
                    newBranch = ""
                    newBranchError = null
                    showNewBranchDialog = false
                },
                confirmEnabled = newBranchError == null && newBranch.isNotBlank(),
            )
        }

        if (showDeleteBranchDialog) {
            DeleteBranchConfirmDialog(
                branch = branchToDelete,
                onDismiss = { showDeleteBranchDialog = false },
                onConfirm = {
                    viewModel.deleteBranch(branchToDelete)
                    showDeleteBranchDialog = false
                },
            )
        }

        if (showRenameBranchDialog) {
            SingleInputDialog(
                title = stringResource(id = strings.rename_branch),
                inputLabel = stringResource(id = strings.rename_branch_label, branchToRename),
                inputValue = newBranchName,
                errorMessage = renameBranchError,
                confirmText = stringResource(strings.ok),
                onInputValueChange = {
                    newBranchName = it
                    renameBranchError =
                        when {
                            newBranchName.isBlank() -> strings.value_empty_err.getString()
                            else -> null
                        }
                },
                onConfirm = { viewModel.renameBranch(branchToRename, newBranchName) },
                onFinish = {
                    newBranchName = ""
                    renameBranchError = null
                    showRenameBranchDialog = false
                },
                confirmEnabled = renameBranchError == null && newBranchName.isNotBlank(),
            )
        }

        selectedCommit?.let { commit ->
            CommitDetailsDialog(
                commit = commit,
                viewModel = viewModel,
                onDismiss = { selectedCommit = null },
                onOpenFileDiff = { change ->
                    viewModel.getDiff(change, commit) { diff ->
                        MainActivity.instance
                            ?.viewModel
                            ?.editorManager
                            ?.addPreviewTab(
                                title = change.fileName,
                                content = diff,
                                extension = "diff",
                            )
                    }
                    selectedCommit = null
                    scope.launch { drawerStateRef.get()?.close() }
                },
            )
        }

        if (showPushConfirmDialog) {
            val commitCount = viewModel.aheadCount
            AlertDialog(
                onDismissRequest = {
                    showPushConfirmDialog = false
                    force = false
                },
                title = { Text(stringResource(strings.push)) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            stringResource(
                                if (commitCount > 0) strings.push_dialog_message_commits
                                else strings.push_dialog_message_empty,
                                commitCount,
                                viewModel.currentBranch,
                            )
                        )
                        if (commitCount > 0) {
                            Row(
                                modifier =
                                    Modifier.fillMaxWidth()
                                        .height(40.dp)
                                        .toggleable(
                                            value = force,
                                            onValueChange = { force = it },
                                            role = Role.Checkbox,
                                            indication = null,
                                            interactionSource = interactionSource,
                                        )
                                        .padding(horizontal = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Checkbox(checked = force, interactionSource = interactionSource, onCheckedChange = null)
                                Spacer(Modifier.width(8.dp))
                                Text(stringResource(strings.push_dialog_checkbox_force))
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(
                        enabled = commitCount > 0,
                        onClick = {
                            showPushConfirmDialog = false
                            viewModel.push(force)
                            force = false
                        },
                    ) {
                        Text(stringResource(strings.push))
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = {
                            showPushConfirmDialog = false
                            force = false
                        }
                    ) {
                        Text(stringResource(strings.cancel))
                    }
                },
            )
        }
    }

    @Composable
    private fun ColumnScope.ConflictsList(
        conflicts: List<GitChange>,
        conflictsExpanded: Boolean,
        onToggleExpansion: () -> Unit,
    ) {
        if (conflicts.isEmpty()) return

        val conflictsSelectionState =
            when {
                conflicts.all { it.isChecked } -> ToggleableState.On
                conflicts.none { it.isChecked } -> ToggleableState.Off
                else -> ToggleableState.Indeterminate
            }

        Row(
            modifier =
                Modifier.width((getDrawerWidth() - 61.dp))
                    .combinedClickable(onClick = onToggleExpansion)
                    .padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val rotationDegree by
                animateFloatAsState(targetValue = if (!conflictsExpanded) 0f else 90f, label = "rotation")

            Icon(
                painterResource(drawables.chevron_right),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                modifier = Modifier.size(16.dp).rotate(rotationDegree),
            )
            Spacer(Modifier.width(4.dp))

            TriStateCheckbox(
                enabled = !viewModel.isLoading,
                state = conflictsSelectionState,
                onClick = {
                    if (conflictsSelectionState == ToggleableState.On) {
                        conflicts.forEach { viewModel.removeChange(it) }
                    } else {
                        conflicts.forEach { viewModel.addChange(it) }
                    }
                },
                modifier = Modifier.size(20.dp),
            )

            Spacer(Modifier.width(8.dp))

            Text(
                text = stringResource(strings.conflicts),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }

        AnimatedVisibility(visible = conflictsExpanded) { ChangesItemList(conflicts) }
        Spacer(modifier = Modifier.height(8.dp))
    }

    @Composable
    private fun ColumnScope.ChangesList(
        changes: List<GitChange>,
        changesExpanded: Boolean,
        onToggleExpansion: () -> Unit,
    ) {
        if (changes.isEmpty()) return

        val changesSelectionState =
            when {
                changes.all { it.isChecked } -> ToggleableState.On
                changes.none { it.isChecked } -> ToggleableState.Off
                else -> ToggleableState.Indeterminate
            }

        Row(
            modifier =
                Modifier.width((getDrawerWidth() - 61.dp))
                    .combinedClickable(onClick = onToggleExpansion)
                    .padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val rotationDegree by
                animateFloatAsState(targetValue = if (!changesExpanded) 0f else 90f, label = "rotation")

            Icon(
                painterResource(drawables.chevron_right),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                modifier = Modifier.size(16.dp).rotate(rotationDegree),
            )
            Spacer(Modifier.width(4.dp))

            TriStateCheckbox(
                enabled = !viewModel.isLoading,
                state = changesSelectionState,
                onClick = {
                    if (changesSelectionState == ToggleableState.On) {
                        changes.forEach { viewModel.removeChange(it) }
                    } else {
                        changes.forEach { viewModel.addChange(it) }
                    }
                },
                modifier = Modifier.size(20.dp),
            )

            Spacer(Modifier.width(8.dp))

            Text(
                text = stringResource(strings.changes),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }

        AnimatedVisibility(visible = changesExpanded) { ChangesItemList(changes) }
        Spacer(modifier = Modifier.height(8.dp))
    }

    @Composable
    private fun ColumnScope.UntrackedList(
        untracked: List<GitChange>,
        untrackedExpanded: Boolean,
        onToggleExpansion: () -> Unit,
    ) {
        if (untracked.isEmpty()) return

        val untrackedSelectionState =
            when {
                untracked.all { it.isChecked } -> ToggleableState.On
                untracked.none { it.isChecked } -> ToggleableState.Off
                else -> ToggleableState.Indeterminate
            }

        Row(
            modifier =
                Modifier.width((getDrawerWidth() - 61.dp))
                    .combinedClickable(onClick = onToggleExpansion)
                    .padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val rotationDegree by
                animateFloatAsState(targetValue = if (!untrackedExpanded) 0f else 90f, label = "rotation")

            Icon(
                painterResource(drawables.chevron_right),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                modifier = Modifier.size(16.dp).rotate(rotationDegree),
            )
            Spacer(Modifier.width(4.dp))

            TriStateCheckbox(
                enabled = !viewModel.isLoading,
                state = untrackedSelectionState,
                onClick = {
                    if (untrackedSelectionState == ToggleableState.On) {
                        untracked.forEach { viewModel.removeChange(it) }
                    } else {
                        untracked.forEach { viewModel.addChange(it) }
                    }
                },
                modifier = Modifier.size(20.dp),
            )

            Spacer(Modifier.width(8.dp))

            Text(
                text = stringResource(strings.untracked),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }

        AnimatedVisibility(visible = untrackedExpanded) { ChangesItemList(untracked) }
        Spacer(modifier = Modifier.height(8.dp))
    }

    @Composable
    private fun ChangesItemList(items: List<GitChange>) {
        val context = LocalContext.current
        val scope = rememberCoroutineScope()

        Column(modifier = Modifier.padding(start = 40.dp)) {
            items.forEach { change ->
                var showRollbackDialog by remember { mutableStateOf(false) }
                if (showRollbackDialog) {
                    RollbackConfirmDialog(
                        change = change,
                        onDismiss = { showRollbackDialog = false },
                        onConfirm = {
                            viewModel.discard(change)
                            showRollbackDialog = false
                        },
                    )
                }

                val file = File(change.absolutePath).toFileWrapper()

                ChangesFileRow(
                    change = change,
                    underlineColor = fileTreeViewModel.get()?.let { getUnderlineColor(context, it, file) },
                    checked = change.isChecked,
                    onCheckedChange = { viewModel.toggleChange(change) },
                    enabled = !viewModel.isLoading,
                    onClick = {
                        viewModel.getDiff(change) { diff ->
                            MainActivity.instance
                                ?.viewModel
                                ?.editorManager
                                ?.addPreviewTab(
                                    title = change.fileName,
                                    content = diff,
                                    extension = "diff",
                                )
                            scope.launch { drawerStateRef.get()?.close() }
                        }
                    },
                ) {
                    showRollbackDialog = true
                }
            }
        }
    }

    @Composable
    private fun DeleteBranchConfirmDialog(branch: String, onDismiss: () -> Unit, onConfirm: () -> Unit) {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(stringResource(strings.delete_branch)) },
            text = { Text(stringResource(strings.delete_branch_msg, branch)) },
            confirmButton = {
                TextButton(onClick = onConfirm) {
                    Text(stringResource(strings.delete), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(strings.cancel)) } },
        )
    }

    @Composable
    private fun RollbackConfirmDialog(change: GitChange, onDismiss: () -> Unit, onConfirm: () -> Unit) {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(stringResource(strings.discard_changes)) },
            text = { Text(stringResource(strings.discard_changes_msg, change.fileName)) },
            confirmButton = { TextButton(onClick = onConfirm) { Text(stringResource(strings.discard)) } },
            dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(strings.cancel)) } },
        )
    }

    override fun getName(): String {
        return strings.git.getString()
    }

    override fun getIcon(): Icon {
        return Icon.ResourceIcon(drawables.git)
    }

    override fun isSupported(): Boolean {
        if (!FeatureRegistry.isEnabled("enable_git")) return false
        val drawerViewModel = MainActivity.instance?.drawerViewModel ?: return false
        val tab = drawerViewModel.currentDrawerTab ?: return false
        if (tab !is FileTreeTab) return false

        val rootDir = File(tab.root.getAbsolutePath())
        return FileRepositoryBuilder().findGitDir(rootDir).gitDir != null
    }
}
