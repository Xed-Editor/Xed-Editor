package com.rk.activities.main

import androidx.compose.animation.core.spring
import androidx.compose.foundation.ExperimentalFoundationApi
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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mohamedrejeb.compose.dnd.reorder.ReorderContainer
import com.mohamedrejeb.compose.dnd.reorder.ReorderState
import com.mohamedrejeb.compose.dnd.reorder.ReorderableItem
import com.mohamedrejeb.compose.dnd.reorder.rememberReorderState
import com.rk.commands.CommandPalette
import com.rk.commands.CommandProvider
import com.rk.components.FileActionDialog
import com.rk.file.FileObject
import com.rk.filetree.FileIcon
import com.rk.filetree.FileTreeTab
import com.rk.filetree.FileTreeViewModel
import com.rk.filetree.currentTab
import com.rk.resources.getString
import com.rk.resources.strings
import com.rk.settings.Settings
import com.rk.tabs.base.Tab
import com.rk.tabs.editor.EditorTab
import com.rk.utils.dialog
import com.rk.utils.preloadSelectionColor
import kotlinx.coroutines.launch

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun MainContent(
    modifier: Modifier = Modifier,
    innerPadding: PaddingValues,
    mainViewModel: MainViewModel,
    fileTreeViewModel: FileTreeViewModel,
    drawerState: DrawerState,
) {
    val scope = rememberCoroutineScope()
    var fileActionDialog by remember { mutableStateOf<FileObject?>(null) }

    preloadSelectionColor()

    Column(Modifier.fillMaxSize().padding(innerPadding)) {
        if (mainViewModel.isDraggingPalette || mainViewModel.showCommandPalette) {
            val lastUsedCommand = CommandProvider.getForId(Settings.last_used_command)

            CommandPalette(
                progress = if (mainViewModel.showCommandPalette) 1f else mainViewModel.draggingPaletteProgress.value,
                commands = CommandProvider.commandList,
                lastUsedCommand = lastUsedCommand,
                initialChildCommands = mainViewModel.commandPaletteInitialChildCommands,
                initialPlaceholder = mainViewModel.commandPaletteInitialPlaceholder,
                onDismissRequest = {
                    mainViewModel.isDraggingPalette = false
                    mainViewModel.showCommandPalette = false
                    mainViewModel.commandPaletteInitialChildCommands = null
                    mainViewModel.commandPaletteInitialPlaceholder = null

                    scope.launch {
                        mainViewModel.draggingPaletteProgress.animateTo(0f, animationSpec = spring(stiffness = 800f))
                    }
                },
            )
        }

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

            //            LaunchedEffect(mainViewModel.tabs) {
            //                if (mainViewModel.tabs.size != pagerState.pageCount) {
            //                    if (Settings.smooth_tabs) {
            //                        pagerState.animateScrollToPage(mainViewModel.currentTabIndex)
            //                    } else {
            //                        pagerState.scrollToPage(mainViewModel.currentTabIndex)
            //                    }
            //                }
            //            }

            //            LaunchedEffect(pagerState) {
            //                snapshotFlow { pagerState.settledPage }
            //                    .collect { settledPage ->
            //                        if (
            //                            mainViewModel.tabs.isNotEmpty() &&
            //                                settledPage < mainViewModel.tabs.size &&
            //                                mainViewModel.currentTabIndex != settledPage
            //                        ) {
            //                            mainViewModel.currentTabIndex = settledPage
            //                        }
            //                    }
            //            }

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
                                            onOk = { mainViewModel.removeTab(tabIndex) },
                                            onCancel = {},
                                            okString = strings.discard,
                                        )
                                    } else {
                                        mainViewModel.removeTab(tabIndex)
                                    }
                                },
                                onCloseOthers = { index ->
                                    mainViewModel.setCurrentTabIndex(index)
                                    mainViewModel.removeOtherTabs()
                                },
                                onCloseAll = { mainViewModel.closeAllTabs() },
                                showFileActionDialog = { fileActionDialog = it },
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

            if (fileActionDialog != null) {
                FileActionDialog(
                    modifier = Modifier,
                    file = fileActionDialog!!,
                    root = (currentTab as? FileTreeTab)?.root,
                    onDismissRequest = { fileActionDialog = null },
                    fileTreeContext = false,
                    fileTreeViewModel = fileTreeViewModel,
                )
            }
        }
    }
}

@Composable
private fun TabItem(
    mainViewModel: MainViewModel,
    reorderState: ReorderState<Tab>,
    tabState: Tab,
    index: Int,
    showIcon: Boolean,
    onCloseThis: (Int) -> Unit,
    onCloseOthers: (Int) -> Unit,
    onCloseAll: (Int) -> Unit,
    showFileActionDialog: (FileObject) -> Unit,
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

            mainViewModel.moveTab(oldIndex, index)
        },
        draggableContent = {
            TabItemContent(
                mainViewModel = mainViewModel,
                index = index,
                calculatedTabWidth = calculatedTabWidth,
                tabState = tabState,
                onCloseThis = onCloseThis,
                onCloseOthers = onCloseOthers,
                onCloseAll = onCloseAll,
                showFileActionDialog = showFileActionDialog,
                showIcon = showIcon,
                isDraggableContent = true,
            )
        },
        modifier = Modifier.fillMaxWidth().onSizeChanged { size -> calculatedTabWidth = size.width },
    ) {
        TabItemContent(
            mainViewModel = mainViewModel,
            index = index,
            calculatedTabWidth = calculatedTabWidth,
            tabState = tabState,
            onCloseThis = onCloseThis,
            onCloseOthers = onCloseOthers,
            onCloseAll = onCloseAll,
            showFileActionDialog = showFileActionDialog,
            showIcon = showIcon,
        )
    }
}

@Composable
private fun TabItemContent(
    mainViewModel: MainViewModel,
    index: Int,
    calculatedTabWidth: Int?,
    tabState: Tab,
    onCloseThis: (Int) -> Unit,
    onCloseOthers: (Int) -> Unit,
    onCloseAll: (Int) -> Unit,
    showFileActionDialog: (FileObject) -> Unit,
    showIcon: Boolean,
    isDraggableContent: Boolean = false,
) {
    var showTabMenu by remember { mutableStateOf(false) }

    val isSelected = mainViewModel.currentTabIndex == index
    val backgroundColor = MaterialTheme.colorScheme.surfaceVariant

    val tabModifier =
        Modifier.let { modifier ->
                calculatedTabWidth?.let { width -> modifier.width(with(LocalDensity.current) { width.toDp() }) }
                    ?: modifier
            }
            .let { if (isDraggableContent) it.background(backgroundColor.copy(alpha = 0.4f)) else it }

    val onClick = {
        if (isSelected) {
            showTabMenu = true
        } else {
            mainViewModel.currentTabIndex = index
        }
    }

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
        )

        DropdownMenu(expanded = showTabMenu, onDismissRequest = { showTabMenu = false }, modifier = Modifier) {
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
                    text = { Text(stringResource(strings.more)) },
                    onClick = {
                        showTabMenu = false
                        showFileActionDialog(it)
                    },
                )
            }
        }
    }

    val activeColor = MaterialTheme.colorScheme.primary
    val inactiveColor = MaterialTheme.colorScheme.onSurfaceVariant

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
