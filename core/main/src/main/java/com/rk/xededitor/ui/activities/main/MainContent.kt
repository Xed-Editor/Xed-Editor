package com.rk.xededitor.ui.activities.main

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.DrawerState
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import com.rk.compose.filetree.currentProject
import com.rk.file.FileObject
import com.rk.libcommons.dialog
import com.rk.resources.getString
import com.rk.resources.strings
import com.rk.settings.Settings
import com.rk.tabs.EditorTab
import com.rk.xededitor.ui.components.FileActionDialog
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.withLock

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun MainContent(modifier: Modifier = Modifier,innerPadding: PaddingValues,viewModel: MainViewModel,drawerState: DrawerState) {
    val scope = rememberCoroutineScope()
    var fileActionDialog by remember { mutableStateOf<FileObject?>(null) }

    Column(
        Modifier
            .fillMaxSize()
            .padding(innerPadding)
    ) {
        if (viewModel.tabs.isEmpty()) {
            Box(
                Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                TextButton(
                    onClick = { scope.launch { drawerState.open() } }
                ) {
                    Text(
                        text = stringResource(strings.click_open),
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
            }
        } else {
            val pagerState = rememberPagerState(pageCount = { viewModel.tabs.size })

            LaunchedEffect(viewModel.currentTabIndex) {
                if (viewModel.tabs.isNotEmpty() &&
                    viewModel.currentTabIndex < viewModel.tabs.size &&
                    pagerState.currentPage != viewModel.currentTabIndex) {
                    if (Settings.smooth_tabs){
                        pagerState.animateScrollToPage(viewModel.currentTabIndex)
                    }else{
                        pagerState.scrollToPage(viewModel.currentTabIndex)
                    }
                }
            }

            LaunchedEffect(viewModel.tabs) {
                if (viewModel.tabs.size != pagerState.pageCount) {
                    if (Settings.smooth_tabs){
                        pagerState.animateScrollToPage(viewModel.currentTabIndex)
                    }else{
                        pagerState.scrollToPage(viewModel.currentTabIndex)
                    }
                }
            }

            LaunchedEffect(pagerState) {
                snapshotFlow { pagerState.settledPage }
                    .collect { settledPage ->
                        if (viewModel.tabs.isNotEmpty() &&
                            settledPage < viewModel.tabs.size &&
                            viewModel.currentTabIndex != settledPage
                        ) {
                            viewModel.currentTabIndex = settledPage
                        }
                    }
            }

            //HorizontalDivider()

            PrimaryScrollableTabRow(
                selectedTabIndex = if (viewModel.currentTabIndex < viewModel.tabs.size) viewModel.currentTabIndex else 0,
                modifier = Modifier.fillMaxWidth(),
                edgePadding = 0.dp,
                divider = {}
            ) {
                viewModel.tabs.forEachIndexed { index, tabState ->
                    key(tabState) {
                        var showTabMenu by remember { mutableStateOf(false) }
                        Tab(
                            modifier = Modifier.combinedClickable(onLongClick = {
                                if (viewModel.currentTabIndex == index) {
                                    showTabMenu = true
                                }
                            }, onClick = {}),
                            selected = viewModel.currentTabIndex == index,
                            onClick = {
                                if (viewModel.currentTabIndex == index) {
                                    showTabMenu = true
                                } else {
                                    viewModel.currentTabIndex = index
                                }
                            },
                            text = {
                                Text(
                                    text = if (tabState is EditorTab && tabState.editorState.isDirty) {
                                        "*${tabState.tabTitle.value}"
                                    } else {
                                        tabState.tabTitle.value
                                    },
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                DropdownMenu(
                                    expanded = showTabMenu,
                                    offset = DpOffset((-22).dp,15.dp),
                                    onDismissRequest = { showTabMenu = false },
                                    modifier = Modifier
                                ) {
                                    DropdownMenuItem(
                                        text = { Text(stringResource(strings.close_this)) },
                                        onClick = {
                                            showTabMenu = false
                                            val tabToClose = tabState
                                            val tabIndex = viewModel.tabs.indexOf(tabToClose)

                                            if (tabIndex != -1) {
                                                if (tabToClose is EditorTab && tabToClose.editorState.isDirty){
                                                    dialog(
                                                        title = strings.file_unsaved.getString(),
                                                        msg = strings.ask_unsaved.getString(),
                                                        onOk = { viewModel.removeTab(tabIndex) },
                                                        onCancel = {},
                                                        okString = strings.close
                                                    )
                                                } else {
                                                    viewModel.removeTab(tabIndex)
                                                }
                                            }
                                        }
                                    )

                                    DropdownMenuItem(
                                        text = { Text(stringResource(strings.close_others)) },
                                        onClick = {
                                            showTabMenu = false
                                            viewModel.setCurrentTabIndex(index)
                                            viewModel.removeOtherTabs()
                                        }
                                    )

                                    DropdownMenuItem(
                                        text = { Text(stringResource(strings.close_all)) },
                                        onClick = {
                                            showTabMenu = false
                                            viewModel.closeAllTabs()
                                        }
                                    )

                                    if (tabState is EditorTab){
                                        DropdownMenuItem(
                                            text = { Text(stringResource(strings.more)) },
                                            onClick = {
                                                showTabMenu = false
                                                fileActionDialog = tabState.file
                                            }
                                        )
                                    }
                                }
                            }
                        )
                    }
                }
            }

            HorizontalDivider()

            var cachedTabSize by rememberSaveable { mutableIntStateOf(1) }

            LaunchedEffect(Unit) {
                delay(500)
                cachedTabSize = viewModel.tabs.size
            }

            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize().clipToBounds(),
                beyondViewportPageCount = cachedTabSize,
                userScrollEnabled = false,
                key = { index -> viewModel.tabs[index].refreshKey }
            ) { page ->
                if (page < viewModel.tabs.size) {
                    val tab = viewModel.tabs[page]
                    viewModel.tabs[page].Content()
                }
            }

            if (fileActionDialog != null && currentProject != null){
                FileActionDialog(
                    modifier = Modifier,
                    file = fileActionDialog!!,
                    root = currentProject!!,
                    onDismissRequest = {
                        fileActionDialog = null
                    },
                    fileTreeContext = false
                )
            }
        }
    }
}