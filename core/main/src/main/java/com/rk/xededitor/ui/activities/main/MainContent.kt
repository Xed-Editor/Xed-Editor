package com.rk.xededitor.ui.activities.main

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
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
import androidx.compose.ui.unit.dp
import com.rk.resources.strings
import kotlinx.coroutines.launch

@Composable
fun MainContent(modifier: Modifier = Modifier,innerPadding: PaddingValues,viewModel: MainViewModel,drawerState: DrawerState) {
    val scope = rememberCoroutineScope()
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
            val pagerState = rememberPagerState(
                pageCount = { viewModel.tabs.size },
                initialPage = if (viewModel.tabs.isNotEmpty() && viewModel.currentTabIndex < viewModel.tabs.size)
                    viewModel.currentTabIndex else 0
            )

            // Handle viewModel index changes
            LaunchedEffect(viewModel.currentTabIndex, viewModel.tabs.size) {
                if (viewModel.tabs.isNotEmpty() && viewModel.currentTabIndex < viewModel.tabs.size) {
                    pagerState.scrollToPage(viewModel.currentTabIndex)
                }
            }

            // Handle pager state changes - but only if it's within valid bounds
            LaunchedEffect(pagerState.currentPage, viewModel.tabs.size) {
                if (pagerState.currentPage < viewModel.tabs.size) {
                    viewModel.currentTabIndex = pagerState.currentPage
                }
            }

            ScrollableTabRow(
                selectedTabIndex = if (pagerState.currentPage < viewModel.tabs.size) pagerState.currentPage else 0,
                modifier = Modifier.fillMaxWidth(),
                edgePadding = 0.dp,
                divider = {}
            ) {
                viewModel.tabs.forEachIndexed { index, tabState ->
                    var showTabMenu by remember { mutableStateOf(false) }

                    Tab(
                        selected = pagerState.currentPage == index,
                        onClick = {
                            if (viewModel.currentTabIndex == index) {
                                showTabMenu = true
                            } else {
                                viewModel.currentTabIndex = index
                            }
                        },
                        text = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = if (tabState is EditorTab && tabState.editorState.isDirty) {
                                        "*${tabState.title}"
                                    } else {
                                        tabState.title
                                    },
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            DropdownMenu(
                                expanded = showTabMenu,
                                onDismissRequest = { showTabMenu = false }
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
                            }
                        }
                    )
                }
            }

            HorizontalDivider()

            if (viewModel.tabs.isNotEmpty()) {
                HorizontalPager(
                    state = pagerState,
                    userScrollEnabled = false,
                    modifier = Modifier,
                    beyondViewportPageCount = viewModel.tabs.size
                ) { page ->
                    // Add bounds checking here too
                    if (page < viewModel.tabs.size) {
                        key(viewModel.tabs[page].viewPagerId) {
                            viewModel.tabs[page].content()
                        }
                    }
                }
            }
        }
    }
}