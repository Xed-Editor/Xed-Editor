package com.rk.activities.main

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DrawerState
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.rk.ai.InlineAgentBar
import com.rk.ai.UnifiedToolSheet
import com.rk.commands.CommandPalette
import com.rk.commands.CommandProvider
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
import com.rk.theme.DesignTokens
import com.rk.utils.dialog
import com.rk.utils.getGitColor
import kotlinx.coroutines.launch

private val TAB_HEIGHT = 36.dp
private val TAB_MIN_WIDTH = 80.dp
private val TAB_MAX_WIDTH = 200.dp

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

    Box(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            if (mainViewModel.tabs.isEmpty()) {
                EmptyEditorState(
                    onNewFile = {
                        com.rk.activities.main.MainActivity.instance?.apply {
                            fileManager.createNewFile(mimeType = "*/*", title = "newfile.txt") { file ->
                                if (file != null) {
                                    mainViewModel.editorManager.addEditorTab(file, null, true)
                                }
                            }
                        }
                    },
                    onOpenFile = { scope.launch { drawerState.open() } },
                )
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

                EditorTabBar(
                    mainViewModel = mainViewModel,
                    fileTreeViewModel = fileTreeViewModel,
                )

                HorizontalDivider(thickness = DesignTokens.Divider.thin)

                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxSize().clipToBounds(),
                    beyondViewportPageCount = mainViewModel.tabs.size,
                    userScrollEnabled = false,
                    key = { it },
                ) { page ->
                    if (page < mainViewModel.tabs.size) {
                        mainViewModel.tabs[page].Content()
                    }
                }
            }
        }

        AnimatedVisibility(
            visible = mainViewModel.showBottomPanel,
            enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            UnifiedToolSheet(
                viewModel = mainViewModel,
                onDismissRequest = { mainViewModel.showBottomPanel = false }
            )
        }

        InlineAgentBar(
            viewModel = mainViewModel,
            visible = mainViewModel.showInlineAgent && !mainViewModel.showBottomPanel,
            onDismiss = { mainViewModel.showInlineAgent = false },
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }
}

@Composable
private fun EditorTabBar(
    mainViewModel: MainViewModel,
    fileTreeViewModel: FileTreeViewModel,
) {
    Surface(
        tonalElevation = DesignTokens.Elevation.none,
        color = MaterialTheme.colorScheme.surface,
    ) {
        LazyRow(
            modifier = Modifier
                .fillMaxWidth()
                .height(TAB_HEIGHT),
        ) {
            itemsIndexed(mainViewModel.tabs, key = { index, _ -> index }) { index, tabState ->
                CompactTabItem(
                    mainViewModel = mainViewModel,
                    fileTreeViewModel = fileTreeViewModel,
                    tabState = tabState,
                    index = index,
                )
            }
        }
    }
}

@Composable
private fun CompactTabItem(
    mainViewModel: MainViewModel,
    fileTreeViewModel: FileTreeViewModel,
    tabState: Tab,
    index: Int,
) {
    var showTabMenu by remember { mutableStateOf(false) }
    var showFileActionMenu by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val isSelected = mainViewModel.currentTabIndex == index

    val bgColor = when {
        isSelected -> MaterialTheme.colorScheme.surfaceContainerHigh
        else -> MaterialTheme.colorScheme.surface
    }

    val gitColor = getGitColor(tabState.file)
    val activeColor = gitColor ?: MaterialTheme.colorScheme.primary
    val inactiveColor = gitColor ?: MaterialTheme.colorScheme.onSurfaceVariant

    Box(
        modifier = Modifier
            .width(TAB_MIN_WIDTH.coerceAtMost(TAB_MAX_WIDTH))
            .fillMaxHeight()
            .background(bgColor)
            .clickable {
                if (isSelected) showTabMenu = true
                else mainViewModel.tabManager.setCurrentTab(index)
            }
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxSize()
                .padding(start = 8.dp, end = 4.dp),
        ) {
            if (Settings.show_tab_icons && tabState.file != null) {
                FileIcon(
                    file = tabState.file!!,
                    iconTint = if (isSelected) activeColor else inactiveColor,
                )
                Spacer(Modifier.width(6.dp))
            }

            Text(
                text = buildString {
                    if (tabState is EditorTab && tabState.editorState.isDirty) append("*")
                    append(tabState.tabTitle.value)
                },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodySmall,
                color = if (isSelected) activeColor else inactiveColor,
                modifier = Modifier.weight(1f),
            )

            IconButton(
                onClick = { onCloseTab(mainViewModel, tabState) },
                modifier = Modifier.size(28.dp),
            ) {
                Icon(
                    painter = painterResource(drawables.close),
                    contentDescription = stringResource(strings.close_this),
                    modifier = Modifier.size(14.dp),
                    tint = if (isSelected) activeColor.copy(alpha = 0.6f) else inactiveColor.copy(alpha = 0.3f),
                )
            }
        }

        if (isSelected) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(2.dp)
                    .background(activeColor),
            )
        }

        DropdownMenu(expanded = showTabMenu, onDismissRequest = { showTabMenu = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(strings.close_this)) },
                onClick = {
                    showTabMenu = false
                    onCloseTab(mainViewModel, tabState)
                },
            )
            DropdownMenuItem(
                text = { Text(stringResource(strings.close_others)) },
                onClick = {
                    showTabMenu = false
                    closeOthers(mainViewModel, index)
                },
            )
            DropdownMenuItem(
                text = { Text(stringResource(strings.close_all)) },
                onClick = {
                    showTabMenu = false
                    closeAll(mainViewModel)
                },
            )
            tabState.file?.let {
                val fileExists by produceState(false) { value = it.exists() }
                DropdownMenuItem(
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
                                    val ctx = FileActionContext(it, root, fileTreeViewModel, context)
                                    action.action(ctx)
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
                                    val ctx = MultiFileActionContext(files, root, fileTreeViewModel, context)
                                    action.action(ctx)
                                    showFileActionMenu = false
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun onCloseTab(mainViewModel: MainViewModel, tabState: Tab) {
    val tabIndex = mainViewModel.tabs.indexOf(tabState)
    if (tabIndex == -1) return

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
}

private fun closeOthers(mainViewModel: MainViewModel, index: Int) {
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
}

private fun closeAll(mainViewModel: MainViewModel) {
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
}

@Composable
private fun EmptyEditorState(
    onNewFile: () -> Unit,
    onOpenFile: () -> Unit,
) {
    Box(
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(32.dp),
        ) {
            Icon(
                painter = painterResource(drawables.edit_note),
                contentDescription = null,
                modifier = Modifier.size(72.dp),
                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f),
            )

            Spacer(Modifier.height(16.dp))

            Text(
                text = stringResource(strings.app_name),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )

            Spacer(Modifier.height(8.dp))

            Text(
                text = stringResource(strings.app_tagline),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )

            Spacer(Modifier.height(32.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Card(
                    modifier = Modifier
                        .size(width = 140.dp, height = 100.dp)
                        .clickable(onClick = onNewFile),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    ),
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        Icon(
                            painter = painterResource(drawables.add),
                            contentDescription = null,
                            modifier = Modifier.size(28.dp),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = stringResource(strings.new_file),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }

                Card(
                    modifier = Modifier
                        .size(width = 140.dp, height = 100.dp)
                        .clickable(onClick = onOpenFile),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    ),
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        Icon(
                            painter = painterResource(drawables.folder),
                            contentDescription = null,
                            modifier = Modifier.size(28.dp),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = stringResource(strings.open_file),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
            }

            Spacer(Modifier.height(32.dp))

            Text(
                text = stringResource(strings.click_open),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                textAlign = TextAlign.Center,
            )
        }
    }
}
