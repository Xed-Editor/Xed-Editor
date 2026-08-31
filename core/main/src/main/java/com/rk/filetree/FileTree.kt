package com.rk.filetree

import android.os.Environment
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.rk.activities.main.MainActivity
import com.rk.activities.main.ui.searchViewModel
import com.rk.components.XedDropdownMenuItem
import com.rk.drawer.DrawerViewModel
import com.rk.file.FileObject
import com.rk.icons.XedIcon
import com.rk.resources.getString
import com.rk.resources.strings
import com.rk.settings.Settings
import kotlinx.coroutines.launch
import kotlin.math.min

enum class SortMode(val stringRes: Int) {
    SORT_BY_NAME(strings.sort_by_name),
    SORT_BY_SIZE(strings.sort_by_size),
    SORT_BY_DATE(strings.sort_by_date),
}

@Composable
fun FileTree(
    rootNode: FileTreeNode,
    modifier: Modifier = Modifier,
    onFileClick: FileTreeNode.(FileTreeNode) -> Unit,
    onSearchClick: () -> Unit = {},
    viewModel: FileTreeViewModel,
    drawerViewModel: DrawerViewModel,
) {
    val sortMode by viewModel.sortMode.collectAsStateWithLifecycle()
    val isRefreshing by viewModel.isRefreshing.collectAsStateWithLifecycle()
    val selectedFiles by viewModel.selectedFiles.collectAsStateWithLifecycle()
    val fileOperationsCount by viewModel.fileOperationsCount.collectAsStateWithLifecycle()
    val searchVM = searchViewModel.get()
    val isIndexingMap =
        searchVM?.isIndexing?.collectAsStateWithLifecycle(initialValue = emptyMap())?.value ?: emptyMap()
    val isIndexingRoot = isIndexingMap[rootNode.file] == true
    val isAnyFileSelected = selectedFiles[rootNode.file]?.isNotEmpty() == true
    val selectionCount = selectedFiles[rootNode.file]?.size ?: 0
    val isFileOperationInProgress = fileOperationsCount > 0

    // Auto-expand root node on first composition
    LaunchedEffect(rootNode.file) {
        if (!viewModel.isNodeExpanded(rootNode.file, rootNode.file)) {
            viewModel.toggleNodeExpansion(rootNode.file, rootNode.file)
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

    LaunchedEffect(sortMode) { viewModel.refreshEverything() }

    Column(modifier = modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (isAnyFileSelected) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { viewModel.unselectAllFiles(rootNode.file) }) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(strings.go_prev))
                    }

                    Text(selectionCount.toString())
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.End,
                modifier = Modifier.weight(1f),
            ) {
                if (isAnyFileSelected) {
                    SelectionActions(viewModel, drawerViewModel, rootNode, selectedFiles[rootNode.file] ?: emptyList())
                } else {
                    FileTreeActions(viewModel, onSearchClick)
                }
            }
        }

        Box(modifier = Modifier.fillMaxWidth().height(4.dp)) {
            if (isFileOperationInProgress || isIndexingRoot) {
                LinearProgressIndicator(modifier = Modifier.fillMaxSize())
            } else {
                HorizontalDivider()
            }
        }

        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = { viewModel.viewModelScope.launch { viewModel.refreshEverything(wasPulled = true) } },
            modifier = Modifier.fillMaxWidth().weight(1f),
        ) {
            Column(
                modifier =
                    Modifier.fillMaxSize()
                        .horizontalScroll(rememberScrollState())
                        .verticalScroll(rememberScrollState())
                        .padding(vertical = 8.dp)
            ) {
                key(rootNode.file.hashCode(), rootNode.name) {
                    FileTreeNodeItem(
                        modifier = Modifier.fillMaxWidth(),
                        root = rootNode.file,
                        node = rootNode,
                        depth = 0,
                        onFileClick = { rootNode.onFileClick(it) },
                        viewModel = viewModel,
                    )
                }
            }
        }
    }
}

@Composable
private fun SelectionActions(
    viewModel: FileTreeViewModel,
    drawerViewModel: DrawerViewModel,
    rootNode: FileTreeNode,
    selectedFiles: List<FileObject>,
) {
    val scope = rememberCoroutineScope()
    var actions by remember { mutableStateOf<List<BaseFileAction>>(emptyList()) }
    var enabledActions by remember { mutableStateOf<Set<BaseFileAction>>(emptySet()) }
    var expanded by remember { mutableStateOf(false) }

    LaunchedEffect(selectedFiles, rootNode.file) {
        val newActions = FileActionProvider.getActions(selectedFiles, rootNode.file)
        val newEnabled =
            newActions
                .filter { action ->
                    when (action) {
                        is FileAction -> action.isEnabled(selectedFiles.first(), rootNode.file)
                        is MultiFileAction -> action.isEnabled(selectedFiles, rootNode.file)
                        else -> true
                    }
                }
                .toSet()

        actions = newActions
        enabledActions = newEnabled
    }

    val context = LocalContext.current

    BoxWithConstraints {
        val itemWidth = 64.dp
        val availableWidth = maxWidth - 48.dp
        val maxVisibleCount = (availableWidth / itemWidth).toInt().coerceAtLeast(0)

        // Calculate actual number of actions to show in toolbar
        var actualVisibleCount = min(actions.size, maxVisibleCount)

        // Make sure that the dropdown menu never contains only one entry
        if (actions.size - actualVisibleCount == 1) {
            actualVisibleCount += 1
        }

        val toolbarActions = actions.sortedByDescending { it.importance }.take(actualVisibleCount)
        val dropdownActions = actions - toolbarActions.toSet()

        Row(verticalAlignment = Alignment.CenterVertically) {
            toolbarActions.forEach { action ->
                when (action) {
                    is FileAction -> {
                        val file = selectedFiles.first() // Is safe because of the check in getActions()
                        IconButton(
                            enabled = action in enabledActions,
                            onClick = {
                                val context =
                                    FileActionContext(file, rootNode.file, viewModel, drawerViewModel, context)
                                scope.launch { action.execute(context) }

                                viewModel.unselectAllFiles(rootNode.file)
                            },
                        ) {
                            XedIcon(action.icon, contentDescription = action.title)
                        }
                    }
                    is MultiFileAction -> {
                        IconButton(
                            enabled = action in enabledActions,
                            onClick = {
                                val context =
                                    MultiFileActionContext(
                                        selectedFiles,
                                        rootNode.file,
                                        viewModel,
                                        drawerViewModel,
                                        context,
                                    )
                                scope.launch { action.execute(context) }

                                viewModel.unselectAllFiles(rootNode.file)
                            },
                        ) {
                            XedIcon(action.icon, contentDescription = action.title)
                        }
                    }
                }
            }

            if (dropdownActions.isNotEmpty()) {
                Box(modifier = Modifier.wrapContentSize(Alignment.TopEnd)) {
                    IconButton(onClick = { expanded = true }, modifier = Modifier.size(48.dp)) {
                        Icon(Icons.Outlined.MoreVert, stringResource(strings.more))
                    }

                    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                        dropdownActions.forEach { action ->
                            when (action) {
                                is FileAction -> {
                                    val file = selectedFiles.first() // Is safe because of the check in getActions()
                                    XedDropdownMenuItem(
                                        text = { Text(action.title) },
                                        leadingIcon = { XedIcon(action.icon, contentDescription = action.title) },
                                        enabled = action in enabledActions,
                                        onClick = {
                                            val context =
                                                FileActionContext(
                                                    file,
                                                    rootNode.file,
                                                    viewModel,
                                                    drawerViewModel,
                                                    context,
                                                )
                                            scope.launch { action.execute(context) }

                                            viewModel.unselectAllFiles(rootNode.file)
                                            expanded = false
                                        },
                                    )
                                }
                                is MultiFileAction -> {
                                    XedDropdownMenuItem(
                                        text = { Text(action.title) },
                                        leadingIcon = { XedIcon(action.icon, contentDescription = action.title) },
                                        enabled = action in enabledActions,
                                        onClick = {
                                            val context =
                                                MultiFileActionContext(
                                                    selectedFiles,
                                                    rootNode.file,
                                                    viewModel,
                                                    drawerViewModel,
                                                    context,
                                                )
                                            scope.launch { action.execute(context) }

                                            viewModel.unselectAllFiles(rootNode.file)
                                            expanded = false
                                        },
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FileTreeActions(viewModel: FileTreeViewModel, onSearchClick: () -> Unit) {
    var showOptionsMenu by remember { mutableStateOf(false) }
    val sortMode by viewModel.sortMode.collectAsStateWithLifecycle()

    IconButton(onClick = { viewModel.viewModelScope.launch { viewModel.refreshEverything() } }) {
        Icon(Icons.Outlined.Refresh, stringResource(strings.refresh))
    }

    IconButton(onClick = onSearchClick) { Icon(Icons.Outlined.Search, stringResource(strings.search)) }

    Box {
        IconButton(onClick = { showOptionsMenu = true }) { Icon(Icons.Outlined.MoreVert, stringResource(strings.more)) }

        DropdownMenu(expanded = showOptionsMenu, onDismissRequest = { showOptionsMenu = false }) {
            XedDropdownMenuItem(
                text = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = Settings.show_hidden_files_drawer, onCheckedChange = null)
                        Spacer(Modifier.width(12.dp))
                        Text(stringResource(strings.show_hidden_files))
                        Spacer(Modifier.width(8.dp))
                    }
                },
                onClick = {
                    Settings.show_hidden_files_drawer = !Settings.show_hidden_files_drawer
                    showOptionsMenu = false
                },
            )

            XedDropdownMenuItem(
                text = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = Settings.compact_folders_drawer, onCheckedChange = null)
                        Spacer(Modifier.width(12.dp))
                        Text(stringResource(strings.compact_folders))
                        Spacer(Modifier.width(8.dp))
                    }
                },
                onClick = {
                    Settings.compact_folders_drawer = !Settings.compact_folders_drawer
                    showOptionsMenu = false
                },
            )

            XedDropdownMenuItem(
                text = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = sortMode == SortMode.SORT_BY_NAME, onClick = null)
                        Spacer(Modifier.width(12.dp))
                        Text(stringResource(strings.sort_by_name))
                        Spacer(Modifier.width(8.dp))
                    }
                },
                onClick = {
                    viewModel.setSortMode(SortMode.SORT_BY_NAME)
                    Settings.sort_mode = SortMode.SORT_BY_NAME.ordinal
                    showOptionsMenu = false
                },
            )

            XedDropdownMenuItem(
                text = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = sortMode == SortMode.SORT_BY_SIZE, onClick = null)
                        Spacer(Modifier.width(12.dp))
                        Text(stringResource(strings.sort_by_size))
                        Spacer(Modifier.width(8.dp))
                    }
                },
                onClick = {
                    viewModel.setSortMode(SortMode.SORT_BY_SIZE)
                    Settings.sort_mode = SortMode.SORT_BY_SIZE.ordinal
                    showOptionsMenu = false
                },
            )

            XedDropdownMenuItem(
                text = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = sortMode == SortMode.SORT_BY_DATE, onClick = null)
                        Spacer(Modifier.width(12.dp))
                        Text(stringResource(strings.sort_by_date))
                        Spacer(Modifier.width(8.dp))
                    }
                },
                onClick = {
                    viewModel.setSortMode(SortMode.SORT_BY_DATE)
                    Settings.sort_mode = SortMode.SORT_BY_DATE.ordinal
                    showOptionsMenu = false
                },
            )
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
