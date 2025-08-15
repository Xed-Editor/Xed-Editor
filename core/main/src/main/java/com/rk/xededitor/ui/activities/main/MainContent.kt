package com.rk.xededitor.ui.activities.main

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DrawerState
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ScrollableTabRow
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import com.rk.compose.filetree.currentProject
import com.rk.file.FileObject
import com.rk.resources.strings
import com.rk.xededitor.ui.components.FileActionDialog
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.withLock

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
            ScrollableTabRow(
                selectedTabIndex = if (viewModel.currentTabIndex < viewModel.tabs.size) viewModel.currentTabIndex else 0,
                modifier = Modifier.fillMaxWidth(),
                edgePadding = 0.dp,
                divider = {}
            ) {
                viewModel.tabs.forEachIndexed { index, tabState ->
                    key(tabState) {
                        var showTabMenu by remember { mutableStateOf(false) }
                        Tab(
                            selected = viewModel.currentTabIndex== index,
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
                                        "*${tabState.title.value}"
                                    } else {
                                        tabState.title.value
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
                                            viewModel.removeTab(index)
                                        }
                                    )

                                    DropdownMenuItem(
                                        text = { Text(stringResource(strings.close_others)) },
                                        onClick = {
                                            showTabMenu = false
                                            // Set the current tab to the one we're closing others from
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

            viewModel.currentTab?.let { tab ->
                key(tab) {
                    tab.content()
                }
            }

            if (fileActionDialog != null && currentProject != null){
                FileActionDialog(modifier = Modifier, file = fileActionDialog!!, root = currentProject!!, onDismissRequest = {
                    fileActionDialog = null
                },fileTreeContext = false)
            }



        }

    }
}