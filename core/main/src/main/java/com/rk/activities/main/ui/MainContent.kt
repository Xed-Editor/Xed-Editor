package com.rk.activities.main.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.DrawerState
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LeadingIconTab
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mohamedrejeb.compose.dnd.reorder.ReorderContainer
import com.mohamedrejeb.compose.dnd.reorder.ReorderState
import com.mohamedrejeb.compose.dnd.reorder.ReorderableItem
import com.mohamedrejeb.compose.dnd.reorder.rememberReorderState
import com.rk.activities.main.MainActivity
import com.rk.activities.main.MainViewModel
import com.rk.commands.CommandPalette
import com.rk.commands.CommandProvider
import com.rk.components.XedDropdownMenuItem
import com.rk.components.compose.utils.addIf
import com.rk.drawer.DrawerViewModel
import com.rk.editor.preloadSelectionColor
import com.rk.extension.api.TaskRegistry
import com.rk.filetree.BaseFileAction
import com.rk.filetree.FileAction
import com.rk.filetree.FileActionContext
import com.rk.filetree.FileActionDialogs
import com.rk.filetree.FileActionProvider
import com.rk.filetree.FileIcon
import com.rk.filetree.FileTreeViewModel
import com.rk.filetree.MultiFileAction
import com.rk.filetree.MultiFileActionContext
import com.rk.icons.XedIcon
import com.rk.resources.drawables
import com.rk.resources.getString
import com.rk.resources.strings
import com.rk.settings.Settings
import com.rk.tabs.base.Tab
import com.rk.tabs.editor.EditorTab
import com.rk.utils.dialogRes
import com.rk.utils.drawErrorUnderline
import com.rk.utils.getFileColor
import com.rk.utils.getUnderlineColor
import kotlinx.coroutines.launch

@Composable
fun MainContent(
    innerPadding: PaddingValues,
    mainViewModel: MainViewModel,
    drawerViewModel: DrawerViewModel,
    fileTreeViewModel: FileTreeViewModel,
    drawerState: DrawerState,
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    preloadSelectionColor()

    FileActionDialogs(drawerViewModel, fileTreeViewModel, scope, context)

    val isDraggingPalette by mainViewModel.isDraggingPalette.collectAsStateWithLifecycle()
    val showCommandPalette by mainViewModel.showCommandPalette.collectAsStateWithLifecycle()
    val initialChildCommands by mainViewModel.commandPaletteInitialChildCommands.collectAsStateWithLifecycle()
    val initialPlaceholder by mainViewModel.commandPaletteInitialPlaceholder.collectAsStateWithLifecycle()

    if (isDraggingPalette || showCommandPalette) {
        val lastUsedCommand = CommandProvider.getForId(Settings.last_used_command)
        val commands by CommandProvider.commandList.collectAsStateWithLifecycle()

        CommandPalette(
            progress = if (showCommandPalette) 1f else mainViewModel.draggingPaletteProgress.value,
            commands = commands,
            lastUsedCommand = lastUsedCommand,
            initialChildCommands = initialChildCommands,
            initialPlaceholder = initialPlaceholder,
            onDismissRequest = { scope.launch { mainViewModel.closeCommandPalette() } },
        )
    }

    val tasks = TaskRegistry.tasks.collectAsStateWithLifecycle().value
    LaunchedEffect(tasks.size) {
        TaskOutputState.updateActiveTask()
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().padding(innerPadding)) {
            if (mainViewModel.visibleTabs.isEmpty()) {
                WelcomeScreen(drawerViewModel, drawerState, scope)
            } else {
                val pagerState = rememberPagerState(pageCount = { mainViewModel.visibleTabs.size })

                LaunchedEffect(mainViewModel.visibleCurrentTabIndex) {
                    if (
                        mainViewModel.visibleTabs.isNotEmpty() &&
                            mainViewModel.visibleCurrentTabIndex < mainViewModel.visibleTabs.size &&
                            pagerState.currentPage != mainViewModel.visibleCurrentTabIndex
                    ) {
                        if (Settings.smooth_tabs) {
                            pagerState.animateScrollToPage(mainViewModel.visibleCurrentTabIndex)
                        } else {
                            pagerState.scrollToPage(mainViewModel.visibleCurrentTabIndex)
                        }
                    }
                }

                val reorderState = rememberReorderState<Tab>(dragAfterLongPress = true)

                ReorderContainer(state = reorderState) {
                    PrimaryScrollableTabRow(
                        selectedTabIndex =
                            if (mainViewModel.visibleCurrentTabIndex < mainViewModel.visibleTabs.size)
                                mainViewModel.visibleCurrentTabIndex
                            else 0,
                        modifier = Modifier.fillMaxWidth(),
                        edgePadding = 0.dp,
                        divider = {},
                    ) {
                        mainViewModel.visibleTabs.forEachIndexed { index, tabState ->
                            key(tabState.id) {
                                TabItem(
                                    mainViewModel = mainViewModel,
                                    fileTreeViewModel = fileTreeViewModel,
                                    reorderState = reorderState,
                                    tabState = tabState,
                                    index = index,
                                    showIcon = Settings.show_tab_icons,
                                    onCloseThis = {
                                        val tabIndex = mainViewModel.tabs.indexOf(tabState)
                                        if (tabIndex == -1) return@TabItem

                                        if (tabState is EditorTab && tabState.editorState.isDirty) {
                                            dialogRes(
                                                title = strings.file_unsaved.getString(),
                                                msg = strings.ask_unsaved.getString(),
                                                onOk = { mainViewModel.tabManager.removeTab(tabIndex) },
                                                onCancel = {},
                                                okRes = strings.discard,
                                            )
                                        } else {
                                            mainViewModel.tabManager.removeTab(tabIndex)
                                        }
                                    },
                                    onCloseOthers = {
                                        val tabIndex = mainViewModel.tabs.indexOf(tabState)
                                        if (tabIndex == -1) return@TabItem
                                        mainViewModel.tabManager.setCurrentTab(tabIndex)

                                        val visibleTabs = mainViewModel.visibleTabs
                                        val unsavedOtherTabs =
                                            visibleTabs.filter { tab ->
                                                tab != tabState && (tab as? EditorTab)?.editorState?.isDirty == true
                                            }
                                        if (unsavedOtherTabs.isNotEmpty()) {
                                            dialogRes(
                                                title = strings.files_unsaved.getString(),
                                                msg = strings.ask_multiple_unsaved.getString(),
                                                onOk = {
                                                    mainViewModel.tabManager.removeOtherTabs(tabState, visibleTabs)
                                                },
                                                onCancel = {},
                                                okRes = strings.discard,
                                            )
                                        } else {
                                            mainViewModel.tabManager.removeOtherTabs(tabState, visibleTabs)
                                        }
                                    },
                                    onCloseAll = {
                                        val visibleTabs = mainViewModel.visibleTabs
                                        val unsavedTabs =
                                            visibleTabs.filter { tab ->
                                                (tab as? EditorTab)?.editorState?.isDirty == true
                                            }
                                        if (unsavedTabs.isNotEmpty()) {
                                            dialogRes(
                                                title = strings.files_unsaved.getString(),
                                                msg = strings.ask_multiple_unsaved.getString(),
                                                onOk = { mainViewModel.tabManager.removeAllTabs(visibleTabs) },
                                                onCancel = {},
                                                okRes = strings.discard,
                                            )
                                        } else {
                                            mainViewModel.tabManager.removeAllTabs(visibleTabs)
                                        }
                                    },
                                )
                            }
                        }
                    }
                }

                HorizontalDivider()

                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxSize().clipToBounds(),
                    beyondViewportPageCount = mainViewModel.visibleTabs.size.coerceAtLeast(1),
                    userScrollEnabled = false,
                    key = { page -> mainViewModel.visibleTabs.getOrNull(page)?.id ?: "" },
                ) { page ->
                    if (page < mainViewModel.visibleTabs.size) {
                        mainViewModel.visibleTabs[page].Content()
                    }
                }
            }
        }

        TaskOutputView(modifier = Modifier.align(Alignment.BottomCenter))
    }
}

@Composable
private fun TabItem(
    mainViewModel: MainViewModel,
    fileTreeViewModel: FileTreeViewModel,
    reorderState: ReorderState<Tab>,
    tabState: Tab,
    index: Int,
    showIcon: Boolean,
    onCloseThis: () -> Unit,
    onCloseOthers: () -> Unit,
    onCloseAll: () -> Unit,
) {
    var calculatedTabWidth by
        remember(
            tabState,
            tabState.title,
            tabState is EditorTab && tabState.editorState.isDirty,
            tabState is EditorTab && tabState.editorState.editable,
            Settings.show_tab_icons,
        ) {
            mutableStateOf<Int?>(null)
        }

    ReorderableItem(
        state = reorderState,
        key = tabState,
        data = tabState,
        onDragEnter = { state ->
            val index = mainViewModel.tabs.indexOf(tabState)
            val oldIndex = mainViewModel.tabs.indexOf(state.data)

            mainViewModel.tabManager.moveTab(oldIndex, index)
        },
        draggableContent = {
            TabItemContent(
                mainViewModel = mainViewModel,
                fileTreeViewModel = fileTreeViewModel,
                index = index,
                calculatedTabWidth = calculatedTabWidth,
                tab = tabState,
                onCloseThis = onCloseThis,
                onCloseOthers = onCloseOthers,
                onCloseAll = onCloseAll,
                showIcon = showIcon,
                isDraggableContent = true,
            )
        },
        modifier = Modifier.fillMaxWidth().onSizeChanged { size -> calculatedTabWidth = size.width },
    ) {
        TabItemContent(
            mainViewModel = mainViewModel,
            fileTreeViewModel = fileTreeViewModel,
            index = index,
            calculatedTabWidth = calculatedTabWidth,
            tab = tabState,
            onCloseThis = onCloseThis,
            onCloseOthers = onCloseOthers,
            onCloseAll = onCloseAll,
            showIcon = showIcon,
        )
    }
}

@Composable
private fun TabItemContent(
    mainViewModel: MainViewModel,
    fileTreeViewModel: FileTreeViewModel,
    index: Int,
    calculatedTabWidth: Int?,
    tab: Tab,
    onCloseThis: () -> Unit,
    onCloseOthers: () -> Unit,
    onCloseAll: () -> Unit,
    showIcon: Boolean,
    isDraggableContent: Boolean = false,
) {
    var showTabMenu by remember { mutableStateOf(false) }
    var showFileActionMenu by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val density = LocalDensity.current

    val drawerViewModel = (context as MainActivity).drawerViewModel

    val isSelected = mainViewModel.visibleCurrentTabIndex == index
    val backgroundColor = MaterialTheme.colorScheme.surfaceVariant

    val tabModifier =
        Modifier.let { modifier ->
                calculatedTabWidth?.let { width -> modifier.width(with(density) { width.toDp() }) } ?: modifier
            }
            .let { if (isDraggableContent) it.background(backgroundColor.copy(alpha = 0.4f)) else it }
            .let {
                if (tab is EditorTab && !tab.editorState.editable)
                    it.background(MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.4f))
                else it
            }

    val onClick: () -> Unit = {
        if (isSelected) {
            showTabMenu = true
        } else {
            val globalIndex = mainViewModel.tabs.indexOf(tab)
            if (globalIndex != -1) {
                mainViewModel.tabManager.setCurrentTab(globalIndex)
            }
        }
    }

    val underlineColor = getUnderlineColor(context, fileTreeViewModel, tab.file)
    val tabText: @Composable () -> Unit = {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (tab is EditorTab && !tab.editorState.editable) {
                Icon(
                    painter = painterResource(drawables.lock),
                    contentDescription = null,
                    modifier = Modifier.size(16.dp).padding(end = 4.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (tab is EditorTab && tab.editorState.isDirty) {
                Icon(
                    painter = painterResource(drawables.circle),
                    contentDescription = null,
                    modifier = Modifier.size(12.dp).padding(end = 4.dp),
                )
            }

            Text(
                text = tab.title,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.addIf(underlineColor != null) { drawErrorUnderline(underlineColor!!) },
            )
        }

        DropdownMenu(expanded = showTabMenu, onDismissRequest = { showTabMenu = false }) {
            XedDropdownMenuItem(
                text = { Text(stringResource(strings.close_this)) },
                onClick = {
                    showTabMenu = false
                    onCloseThis()
                },
            )
            XedDropdownMenuItem(
                text = { Text(stringResource(strings.close_others)) },
                onClick = {
                    showTabMenu = false
                    onCloseOthers()
                },
            )
            XedDropdownMenuItem(
                text = { Text(stringResource(strings.close_all)) },
                onClick = {
                    showTabMenu = false
                    onCloseAll()
                },
            )
            tab.file?.let {
                val fileExists by produceState(false) { value = it.exists() }
                XedDropdownMenuItem(
                    text = { Text(stringResource(strings.file_actions)) },
                    enabled = fileExists,
                    trailingIcon = {
                        Icon(
                            painter = painterResource(drawables.chevron_right),
                            contentDescription = stringResource(strings.open),
                        )
                    },
                    onClick = {
                        showTabMenu = false
                        showFileActionMenu = true
                    },
                )
            }
        }

        tab.file?.let {
            DropdownMenu(expanded = showFileActionMenu, onDismissRequest = { showFileActionMenu = false }) {
                val root = (tab as? EditorTab)?.projectRoot
                val scope = rememberCoroutineScope()
                var actions by remember(it) { mutableStateOf<List<BaseFileAction>>(emptyList()) }
                var enabledActions by remember(it) { mutableStateOf<Set<BaseFileAction>>(emptySet()) }

                LaunchedEffect(it, root) {
                    actions = FileActionProvider.getActions(it, root)
                    enabledActions =
                        actions
                            .filter { action ->
                                when (action) {
                                    is FileAction -> action.isEnabled(it, root)
                                    is MultiFileAction -> action.isEnabled(listOf(it), root)
                                    else -> true
                                }
                            }
                            .toSet()
                }

                actions.forEach { action ->
                    when (action) {
                        is FileAction -> {
                            XedDropdownMenuItem(
                                text = { Text(action.title) },
                                leadingIcon = { XedIcon(action.icon, contentDescription = action.title) },
                                enabled = action in enabledActions,
                                onClick = {
                                    val context =
                                        FileActionContext(it, root, fileTreeViewModel, drawerViewModel, context)
                                    scope.launch { action.execute(context) }
                                    showFileActionMenu = false
                                },
                            )
                        }
                        is MultiFileAction -> {
                            val files = listOf(it)
                            XedDropdownMenuItem(
                                text = { Text(action.title) },
                                leadingIcon = { XedIcon(action.icon, contentDescription = action.title) },
                                enabled = action in enabledActions,
                                onClick = {
                                    val context =
                                        MultiFileActionContext(files, root, fileTreeViewModel, drawerViewModel, context)
                                    scope.launch { action.execute(context) }
                                    showFileActionMenu = false
                                },
                            )
                        }
                    }
                }
            }
        }
    }

    val fileColor = getFileColor(tab.file)
    val activeColor = fileColor ?: MaterialTheme.colorScheme.primary
    val inactiveColor = fileColor ?: MaterialTheme.colorScheme.onSurfaceVariant

    val file = tab.file
    if (showIcon && file != null) {
        LeadingIconTab(
            modifier = tabModifier,
            selected = isSelected,
            onClick = onClick,
            icon = { FileIcon(file = file, iconTint = LocalContentColor.current) },
            text = tabText,
            selectedContentColor = activeColor,
            unselectedContentColor = inactiveColor,
        )
    } else {
        Tab(
            modifier = tabModifier,
            selected = isSelected,
            onClick = onClick,
            text = tabText,
            selectedContentColor = activeColor,
            unselectedContentColor = inactiveColor,
        )
    }
}
