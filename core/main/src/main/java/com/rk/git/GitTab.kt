package com.rk.git

import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.input.clearText
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.rk.components.isPermanentDrawer
import com.rk.filetree.DrawerTab
import com.rk.icons.Icon
import com.rk.resources.drawables
import com.rk.resources.strings

class GitTab(val viewModel: GitViewModel) : DrawerTab() {
    @Composable
    override fun Content(modifier: Modifier) {
        var showBranchesMenu by remember { mutableStateOf(false) }
        val commitMessageState = rememberTextFieldState()
        val (amendState, onStateChange) = remember { mutableStateOf(false) }

        val interactionSource = remember { MutableInteractionSource() }

        Surface(
            modifier = modifier,
            color =
                if (isPermanentDrawer) {
                    MaterialTheme.colorScheme.surface
                } else {
                    MaterialTheme.colorScheme.surfaceContainerLow
                },
        ) {
            Column(modifier = Modifier.fillMaxSize().padding(vertical = 8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box {
                        TextButton(onClick = { showBranchesMenu = true }, enabled = !viewModel.isLoading) {
                            Icon(painterResource(drawables.git), contentDescription = null)
                            Spacer(Modifier.size(8.dp))
                            Text(viewModel.currentBranch)
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
                                        viewModel.checkoutBranch(branch)
                                        showBranchesMenu = false
                                    },
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.weight(1f))

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { viewModel.pull() }, enabled = !viewModel.isLoading) {
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
                if (viewModel.isLoading) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                } else {
                    HorizontalDivider()
                }
                if (viewModel.currentChanges.size > 0) {
                    LazyColumn(modifier = Modifier.weight(1f, fill = true)) {
                        items(viewModel.currentChanges) { change ->
                            ListItem(
                                modifier = modifier.fillMaxWidth().clickable { viewModel.toggleChange(change) },
                                headlineContent = {
                                    Text(
                                        text = change.path.substringAfterLast("/"),
                                        style = MaterialTheme.typography.bodyLarge,
                                        fontWeight = FontWeight.Bold,
                                    )
                                },
                                supportingContent = {
                                    Text(
                                        text = change.path,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                },
                                trailingContent = {
                                    Icon(
                                        imageVector =
                                            when (change.type) {
                                                ChangeType.ADDED -> Icons.Outlined.Add
                                                ChangeType.DELETED -> Icons.Outlined.Delete
                                                ChangeType.MODIFIED -> Icons.Outlined.Edit
                                            },
                                        contentDescription = null,
                                        tint =
                                            when (change.type) {
                                                ChangeType.ADDED -> MaterialTheme.colorScheme.primary
                                                ChangeType.DELETED -> MaterialTheme.colorScheme.error
                                                ChangeType.MODIFIED -> MaterialTheme.colorScheme.secondary
                                            },
                                    )
                                },
                                leadingContent = {
                                    Checkbox(
                                        enabled = !viewModel.isLoading,
                                        checked = change.isChecked,
                                        onCheckedChange = { viewModel.toggleChange(change) },
                                    )
                                },
                            )
                            HorizontalDivider()
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
                                value = amendState,
                                onValueChange = { onStateChange(!amendState) },
                                role = Role.Checkbox,
                                indication = null,
                                interactionSource = interactionSource,
                            )
                            .padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(
                        enabled = !viewModel.isLoading,
                        checked = amendState,
                        interactionSource = interactionSource,
                        onCheckedChange = onStateChange,
                    )
                    Text(stringResource(strings.amend))
                }
                OutlinedTextField(
                    enabled = !viewModel.isLoading && viewModel.currentChanges.size > 0,
                    modifier = Modifier.fillMaxWidth().height(120.dp),
                    state = commitMessageState,
                    placeholder = { Text(stringResource(strings.commit_message)) },
                )
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        enabled = !viewModel.isLoading && commitMessageState.text.isNotBlank(),
                        modifier = modifier,
                        onClick = {
                            viewModel.commit(
                                message = commitMessageState.text.toString(),
                                changes = viewModel.currentChanges.filter { it.isChecked },
                                isAmend = amendState,
                            )
                            commitMessageState.clearText()
                        },
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
                        enabled = !viewModel.isLoading && commitMessageState.text.isNotBlank(),
                        modifier = modifier,
                        onClick = {
                            viewModel.commit(
                                message = commitMessageState.text.toString(),
                                changes = viewModel.currentChanges.filter { it.isChecked },
                                isAmend = amendState,
                            )
                            viewModel.push(force = false)
                            commitMessageState.clearText()
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
    }

    override fun getName(): String {
        return "Git"
    }

    override fun getIcon(): Icon {
        return Icon.DrawableRes(drawables.git)
    }
}
