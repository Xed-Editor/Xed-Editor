package com.rk.activities.main

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.DrawerState
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LeadingIconTab
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mohamedrejeb.compose.dnd.reorder.ReorderContainer
import com.mohamedrejeb.compose.dnd.reorder.ReorderState
import com.mohamedrejeb.compose.dnd.reorder.ReorderableItem
import com.mohamedrejeb.compose.dnd.reorder.rememberReorderState
import com.rk.commands.CommandPalette
import com.rk.commands.CommandProvider
import com.rk.components.compose.utils.addIf
import com.rk.editor.preloadSelectionColor
import com.rk.filetree.FileAction
import com.rk.filetree.FileActionContext
import com.rk.filetree.FileActionDialogs
import com.rk.filetree.FileIcon
import com.rk.filetree.FileTreeViewModel
import com.rk.filetree.MultiFileAction
import com.rk.filetree.MultiFileActionContext
import com.rk.filetree.getActions
import com.rk.icons.XedIcon
import com.rk.resources.drawables
import com.rk.resources.getString
import com.rk.resources.strings
import com.rk.settings.Settings
import com.rk.tabs.base.Tab
import com.rk.tabs.editor.EditorTab
import com.rk.utils.dialog
import com.rk.utils.drawErrorUnderline
import com.rk.utils.getGitColor
import com.rk.utils.getUnderlineColor
import kotlinx.coroutines.launch

@Composable
fun MainContent(
    innerPadding: PaddingValues,
    mainViewModel: MainViewModel,
    fileTreeViewModel: FileTreeViewModel,
    drawerState: DrawerState,
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    preloadSelectionColor()

    FileActionDialogs(fileTreeViewModel, scope, context)

    if (mainViewModel.isDraggingPalette || mainViewModel.showCommandPalette) {
        val lastUsedCommand = CommandProvider.getForId(Settings.last_used_command)

        CommandPalette(
            progress = if (mainViewModel.showCommandPalette) 1f else mainViewModel.draggingPaletteProgress.value,
            commands = CommandProvider.commandList,
            lastUsedCommand = lastUsedCommand,
            initialChildCommands = mainViewModel.commandPaletteInitialChildCommands,
            initialPlaceholder = mainViewModel.commandPaletteInitialPlaceholder,
            onDismissRequest = { scope.launch { mainViewModel.closeCommandPalette() } },
        )
    }

    Column(Modifier.fillMaxSize().padding(innerPadding)) {
        if (mainViewModel.tabs.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                TextButton(onClick = { scope.launch { drawerState.open() } }) {
                    Text(text = stringResource(strings.click_open), style = MaterialTheme.typography.bodyLarge)
                }
            }
        } else {
            val pagerState = rememberPagerState(pageCount = { mainViewModel.tabs.size })

            LaunchedEffect(mainViewModel.currentTabIndex) {
                if (
                    mainViewModel.tabs.isNotEmpty() &&
                        mainViewModel.currentTabIndex < mainViewModel.tabs.size &&
                        pagerState.currentPage != mainViewModel.currentTabIndex
                ) {
                    if (Settings.smooth_tabs) {
                        pagerState.animateScrollToPage(mainViewModel.currentTabIndex)
                    } else {
                        pagerState.scrollToPage(mainViewModel.currentTabIndex)
                    }
                }
            }

            val reorderState = rememberReorderState<Tab>(dragAfterLongPress = true)

            ReorderContainer(state = reorderState) {
                PrimaryScrollableTabRow(
                    selectedTabIndex =
                        if (mainViewModel.currentTabIndex < mainViewModel.tabs.size) mainViewModel.currentTabIndex
                        else 0,
                    modifier = Modifier.fillMaxWidth(),
                    edgePadding = 0.dp,
                    divider = {},
                ) {
                    mainViewModel.tabs.forEachIndexed { index, tabState ->
                        key(tabState) {
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
                                        dialog(
                                            title = strings.file_unsaved.getString(),
                                            msg = strings.ask_unsaved.getString(),
                                            onOk = { mainViewModel.tabManager.removeTab(tabIndex) },
                                            onCancel = {},
                                            okString = strings.discard,
                                        )
                                    } else {
                                        mainViewModel.tabManager.removeTab(tabIndex)
                                    }
                                },
                                onCloseOthers = { index ->
                                    mainViewModel.tabManager.setCurrentTab(index)

                                    val unsavedOtherTabs =
                                        mainViewModel.tabs.filterIndexed { tabIndex, tab ->
                                            tabIndex != index && (tab as? EditorTab)?.editorState?.isDirty == true
                                        }
                                    if (unsavedOtherTabs.isNotEmpty()) {
                                        dialog(
                                            title = strings.files_unsaved.getString(),
                                            msg = strings.ask_multiple_unsaved.getString(),
                                            onOk = { mainViewModel.tabManager.removeOtherTabs() },
                                            onCancel = {},
                                            okString = strings.discard,
                                        )
                                    } else {
                                        mainViewModel.tabManager.removeOtherTabs()
                                    }
                                },
                                onCloseAll = {
                                    val unsavedTabs =
                                        mainViewModel.tabs.filter { tab ->
                                            (tab as? EditorTab)?.editorState?.isDirty == true
                                        }
                                    if (unsavedTabs.isNotEmpty()) {
                                        dialog(
                                            title = strings.files_unsaved.getString(),
                                            msg = strings.ask_multiple_unsaved.getString(),
                                            onOk = { mainViewModel.tabManager.removeAllTabs() },
                                            onCancel = {},
                                            okString = strings.discard,
                                        )
                                    } else {
                                        mainViewModel.tabManager.removeAllTabs()
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
                beyondViewportPageCount = mainViewModel.tabs.size,
                userScrollEnabled = false,
            ) { page ->
                if (page < mainViewModel.tabs.size) {
                    mainViewModel.tabs[page].Content()
                }
            }
        }
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
    onCloseThis: (Int) -> Unit,
    onCloseOthers: (Int) -> Unit,
    onCloseAll: (Int) -> Unit,
) {
    var calculatedTabWidth by
        remember(
            tabState,
            tabState.tabTitle.value,
            tabState is EditorTab && tabState.editorState.isDirty,
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
                tabState = tabState,
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
            tabState = tabState,
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
    tabState: Tab,
    onCloseThis: (Int) -> Unit,
    onCloseOthers: (Int) -> Unit,
    onCloseAll: (Int) -> Unit,
    showIcon: Boolean,
    isDraggableContent: Boolean = false,
) {
    var showTabMenu by remember { mutableStateOf(false) }
    var showFileActionMenu by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val density = LocalDensity.current

    val isSelected = mainViewModel.currentTabIndex == index
    val backgroundColor = MaterialTheme.colorScheme.surfaceVariant

    val tabModifier =
        Modifier.let { modifier ->
                calculatedTabWidth?.let { width -> modifier.width(with(density) { width.toDp() }) } ?: modifier
            }
            .let { if (isDraggableContent) it.background(backgroundColor.copy(alpha = 0.4f)) else it }

    val onClick: () -> Unit = {
        if (isSelected) {
            showTabMenu = true
        } else {
            mainViewModel.tabManager.setCurrentTab(index)
        }
    }

    val underlineColor = getUnderlineColor(context, fileTreeViewModel, tabState.file)
    val tabText: @Composable () -> Unit = {
        Text(
            text =
                if (tabState is EditorTab && tabState.editorState.isDirty) {
                    "*${tabState.tabTitle.value}"
                } else {
                    tabState.tabTitle.value
                },
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.addIf(underlineColor != null) { drawErrorUnderline(underlineColor!!) },
        )

        DropdownMenu(expanded = showTabMenu, onDismissRequest = { showTabMenu = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(strings.close_this)) },
                onClick = {
                    showTabMenu = false
                    onCloseThis(index)
                },
            )
            DropdownMenuItem(
                text = { Text(stringResource(strings.close_others)) },
                onClick = {
                    showTabMenu = false
                    onCloseOthers(index)
                },
            )
            DropdownMenuItem(
                text = { Text(stringResource(strings.close_all)) },
                onClick = {
                    showTabMenu = false
                    onCloseAll(index)
                },
            )
            tabState.file?.let {
                DropdownMenuItem(
                    text = { Text(stringResource(strings.file_actions)) },
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

        tabState.file?.let {
            DropdownMenu(expanded = showFileActionMenu, onDismissRequest = { showFileActionMenu = false }) {
                val root = (tabState as? EditorTab)?.projectRoot
                val actions = remember(it) { getActions(it, root) }

                actions.forEach { action ->
                    when (action) {
                        is FileAction -> {
                            DropdownMenuItem(
                                text = { Text(action.title) },
                                leadingIcon = { XedIcon(action.icon, contentDescription = action.title) },
                                enabled = action.isEnabled(it),
                                onClick = {
                                    val context = FileActionContext(it, root, fileTreeViewModel, context)
                                    action.action(context)
                                    showFileActionMenu = false
                                },
                            )
                        }
                        is MultiFileAction -> {
                            val files = listOf(it)
                            DropdownMenuItem(
                                text = { Text(action.title) },
                                leadingIcon = { XedIcon(action.icon, contentDescription = action.title) },
                                enabled = action.isEnabled(files),
                                onClick = {
                                    val context = MultiFileActionContext(files, root, fileTreeViewModel, context)
                                    action.action(context)
                                    showFileActionMenu = false
                                },
                            )
                        }
                    }
                }
            }
        }
    }

    val gitColor = getGitColor(tabState.file)
    val activeColor = gitColor ?: MaterialTheme.colorScheme.primary
    val inactiveColor = gitColor ?: MaterialTheme.colorScheme.onSurfaceVariant

    if (showIcon && tabState.file != null) {
        LeadingIconTab(
            modifier = tabModifier,
            selected = isSelected,
            onClick = onClick,
            icon = { FileIcon(file = tabState.file!!, iconTint = LocalContentColor.current) },
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
