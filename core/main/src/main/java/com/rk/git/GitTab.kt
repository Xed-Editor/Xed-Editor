package com.rk.git

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TriStateCheckbox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
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
import com.rk.components.SingleInputDialog
import com.rk.components.getDrawerWidth
import com.rk.components.isPermanentDrawer
import com.rk.filetree.DrawerTab
import com.rk.filetree.FileNameIcon
import com.rk.filetree.FileTreeTab
import com.rk.filetree.currentTab
import com.rk.icons.Icon
import com.rk.resources.drawables
import com.rk.resources.getString
import com.rk.resources.strings
import com.rk.tabs.editor.EditorTab
import com.rk.utils.findGitRoot
import com.rk.utils.getGitColor
import kotlinx.coroutines.launch

class GitTab(val viewModel: GitViewModel) : DrawerTab() {
    @Composable
    override fun Content(modifier: Modifier) {
        var showBranchesMenu by remember { mutableStateOf(false) }
        var showNewBranchDialog by remember { mutableStateOf(false) }

        val interactionSource = remember { MutableInteractionSource() }
        val scope = rememberCoroutineScope()

        var newBranch by remember { mutableStateOf("") }
        var newBranchError by remember { mutableStateOf<String?>(null) }

        var changesExpanded by remember { mutableStateOf(true) }
        var changes = viewModel.currentRoot.value?.absolutePath?.let { viewModel.currentChanges[it] } ?: emptyList()

        val allChangesSelectionState =
            when {
                changes.all { it.isChecked } -> ToggleableState.On
                changes.none { it.isChecked } -> ToggleableState.Off
                else -> ToggleableState.Indeterminate
            }

        Surface(
            modifier = modifier,
            color =
                if (isPermanentDrawer) {
                    MaterialTheme.colorScheme.surface
                } else {
                    MaterialTheme.colorScheme.surfaceContainerLow
                },
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box {
                        TextButton(onClick = { showBranchesMenu = true }, enabled = !viewModel.isLoading) {
                            Icon(painterResource(drawables.branch), contentDescription = null)
                            Spacer(Modifier.size(8.dp))
                            Text(viewModel.currentBranch)
                            Spacer(Modifier.size(4.dp))
                            Icon(painterResource(drawables.kbd_arrow_down), contentDescription = null)
                        }

                        DropdownMenu(expanded = showBranchesMenu, onDismissRequest = { showBranchesMenu = false }) {
                            viewModel.getBranchList().forEach { branch ->
                                DropdownMenuItem(
                                    text = {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            RadioButton(selected = branch == viewModel.currentBranch, onClick = null)
                                            Spacer(Modifier.width(12.dp))
                                            Text(branch)
                                        }
                                    },
                                    onClick = {
                                        viewModel.checkout(branch)
                                        showBranchesMenu = false
                                    },
                                )
                            }
                            DropdownMenuItem(
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

                    Spacer(modifier = Modifier.weight(1f))

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = {
                                scope.launch {
                                    viewModel.pull().join()
                                    MainActivity.instance!!.viewModel.tabs.filterIsInstance<EditorTab>().forEach {
                                        if (findGitRoot(it.file.getAbsolutePath()) != null) {
                                            it.refresh()
                                        }
                                    }
                                }
                            },
                            enabled = !viewModel.isLoading,
                        ) {
                            Icon(painterResource(drawables.pull), contentDescription = stringResource(strings.pull))
                        }

                        IconButton(onClick = { viewModel.fetch() }, enabled = !viewModel.isLoading) {
                            Icon(painterResource(drawables.fetch), contentDescription = stringResource(strings.fetch))
                        }

                        IconButton(onClick = { viewModel.push(force = false) }, enabled = !viewModel.isLoading) {
                            Icon(painterResource(drawables.push), contentDescription = stringResource(strings.push))
                        }
                    }
                }

                Box(modifier = Modifier.fillMaxWidth().height(4.dp)) {
                    if (viewModel.isLoading) {
                        LinearProgressIndicator(modifier = Modifier.fillMaxSize())
                    } else {
                        HorizontalDivider(modifier = Modifier.fillMaxSize().height(1.dp))
                    }
                }

                if (changes.isNotEmpty()) {
                    Column(
                        modifier =
                            Modifier.fillMaxSize()
                                .weight(1f)
                                .padding(top = 8.dp)
                                .horizontalScroll(rememberScrollState())
                    ) {
                        Row(
                            modifier =
                                Modifier.width((getDrawerWidth() - 61.dp))
                                    .combinedClickable(onClick = { changesExpanded = !changesExpanded })
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
                                state = allChangesSelectionState,
                                onClick = {
                                    if (allChangesSelectionState == ToggleableState.On) {
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

                        AnimatedVisibility(visible = changesExpanded) {
                            LazyColumn(modifier = Modifier.padding(start = 40.dp)) {
                                items(changes) { change ->
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier =
                                            Modifier.width((getDrawerWidth() - 61.dp))
                                                .clickable { viewModel.toggleChange(change) }
                                                .padding(vertical = 4.dp),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    ) {
                                        Checkbox(
                                            enabled = !viewModel.isLoading,
                                            checked = change.isChecked,
                                            onCheckedChange = { viewModel.toggleChange(change) },
                                            modifier = Modifier.size(20.dp),
                                        )

                                        val fileName = change.path.substringAfterLast("/")
                                        FileNameIcon(fileName = fileName, isDirectory = false)

                                        Text(
                                            text = fileName,
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = getGitColor(change.type),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )

                                        Text(
                                            text = change.path,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    }
                                }
                            }
                        }
                    }
                } else {
                    Column(
                        modifier = Modifier.fillMaxSize().weight(1f),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(stringResource(strings.no_changes), color = MaterialTheme.colorScheme.onSurface)
                    }
                }

                HorizontalDivider()

                Row(
                    modifier =
                        Modifier.fillMaxWidth()
                            .height(40.dp)
                            .toggleable(
                                value = viewModel.amend,
                                enabled = !viewModel.isLoading,
                                onValueChange = { viewModel.amend = it },
                                role = Role.Checkbox,
                                indication = null,
                                interactionSource = interactionSource,
                            )
                            .padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(
                        checked = viewModel.amend,
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
                    value = viewModel.commitMessage,
                    onValueChange = { viewModel.commitMessage = it },
                    placeholder = { Text(stringResource(strings.commit_message)) },
                )

                Column(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                    Button(
                        enabled = !viewModel.isLoading && viewModel.commitMessage.isNotBlank(),
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
                        Text(stringResource(strings.commit), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    OutlinedButton(
                        enabled = !viewModel.isLoading && viewModel.commitMessage.isNotBlank(),
                        modifier = Modifier.fillMaxWidth(),
                        onClick = {
                            scope.launch {
                                viewModel.commit().join()
                                viewModel.push(force = false)
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
                        Text(stringResource(strings.commit_and_push), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
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
    }

    override fun getName(): String {
        return strings.git.getString()
    }

    override fun getIcon(): Icon {
        return Icon.DrawableRes(drawables.git)
    }

    override fun isSupported(): Boolean {
        val tab = currentTab ?: return false
        if (tab !is FileTreeTab) return false
        return findGitRoot(tab.root.getAbsolutePath()) != null
    }
}
