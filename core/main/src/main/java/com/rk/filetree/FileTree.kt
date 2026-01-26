package com.rk.filetree

import android.os.Environment
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewModelScope
import com.rk.activities.main.MainActivity
import com.rk.components.isPermanentDrawer
import com.rk.file.FileObject
import com.rk.resources.drawables
import com.rk.resources.getString
import com.rk.resources.strings
import com.rk.settings.ReactiveSettings
import com.rk.settings.Settings
import kotlinx.coroutines.launch

enum class SortMode {
    SORT_BY_NAME,
    SORT_BY_SIZE,
    SORT_BY_DATE,
}

@Composable
fun FileTree(
    rootNode: FileTreeNode,
    modifier: Modifier = Modifier,
    onFileClick: FileTreeNode.(FileTreeNode) -> Unit,
    onFileLongClick: FileTreeNode.(FileTreeNode) -> Unit = {},
    onSearchClick: () -> Unit = {},
    onGitClick: () -> Unit = {},
    viewModel: FileTreeViewModel,
) {
    var showOptionsMenu by remember { mutableStateOf(false) }

    // Auto-expand root node on first composition
    LaunchedEffect(rootNode.file) {
        if (!viewModel.isNodeExpanded(rootNode.file)) {
            viewModel.toggleNodeExpansion(rootNode.file)
            viewModel.loadChildrenForNode(rootNode)
        }
    }

    LaunchedEffect(MainActivity.instance) {
        MainActivity.instance?.foregroundListener["fileTreeRefresh"] = { resumed ->
            if (resumed) {
                viewModel.refreshEverything()
            }
        }
    }

    LaunchedEffect(viewModel.sortMode) { viewModel.refreshEverything() }

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
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = { viewModel.viewModelScope.launch { viewModel.refreshEverything() } }) {
                    Icon(Icons.Outlined.Refresh, stringResource(strings.refresh))
                }

                IconButton(onClick = onSearchClick) { Icon(Icons.Outlined.Search, stringResource(strings.search)) }

                IconButton(onClick = onGitClick) {
                    Icon(
                        painterResource(id = drawables.git),
                        stringResource(strings.git)
                    )
                }

                Box {
                    IconButton(onClick = { showOptionsMenu = true }) {
                        Icon(Icons.Outlined.MoreVert, stringResource(strings.more))
                    }

                    DropdownMenu(expanded = showOptionsMenu, onDismissRequest = { showOptionsMenu = false }) {
                        DropdownMenuItem(
                            text = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Checkbox(checked = ReactiveSettings.showHiddenFilesDrawer, onCheckedChange = null)
                                    Spacer(Modifier.width(12.dp))
                                    Text(stringResource(strings.show_hidden_files))
                                    Spacer(Modifier.width(8.dp))
                                }
                            },
                            onClick = {
                                Settings.show_hidden_files_drawer = !Settings.show_hidden_files_drawer
                                ReactiveSettings.update()
                                showOptionsMenu = false
                            },
                        )

                        DropdownMenuItem(
                            text = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    RadioButton(selected = viewModel.sortMode == SortMode.SORT_BY_NAME, onClick = null)
                                    Spacer(Modifier.width(12.dp))
                                    Text(stringResource(strings.sort_by_name))
                                    Spacer(Modifier.width(8.dp))
                                }
                            },
                            onClick = {
                                viewModel.sortMode = SortMode.SORT_BY_NAME
                                Settings.sort_mode = viewModel.sortMode.ordinal
                                showOptionsMenu = false
                            },
                        )

                        DropdownMenuItem(
                            text = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    RadioButton(selected = viewModel.sortMode == SortMode.SORT_BY_SIZE, onClick = null)
                                    Spacer(Modifier.width(12.dp))
                                    Text(stringResource(strings.sort_by_size))
                                    Spacer(Modifier.width(8.dp))
                                }
                            },
                            onClick = {
                                viewModel.sortMode = SortMode.SORT_BY_SIZE
                                Settings.sort_mode = viewModel.sortMode.ordinal
                                showOptionsMenu = false
                            },
                        )

                        DropdownMenuItem(
                            text = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    RadioButton(selected = viewModel.sortMode == SortMode.SORT_BY_DATE, onClick = null)
                                    Spacer(Modifier.width(12.dp))
                                    Text(stringResource(strings.sort_by_date))
                                    Spacer(Modifier.width(8.dp))
                                }
                            },
                            onClick = {
                                viewModel.sortMode = SortMode.SORT_BY_DATE
                                Settings.sort_mode = viewModel.sortMode.ordinal
                                showOptionsMenu = false
                            },
                        )
                    }
                }
            }

            Box(modifier = Modifier.horizontalScroll(rememberScrollState()).verticalScroll(rememberScrollState())) {
                FileTreeNodeItem(
                    modifier = Modifier.fillMaxWidth(),
                    node = rootNode,
                    depth = 0,
                    onFileClick = { rootNode.onFileClick(it) },
                    onFileLongClick = { rootNode.onFileLongClick(it) },
                    viewModel = viewModel,
                )
            }
        }
    }
}

fun FileObject.getAppropriateName(): String {
    return if (getAbsolutePath() == Environment.getExternalStorageDirectory().absolutePath) {
        strings.storage.getString()
    } else {
        getName()
    }
}
