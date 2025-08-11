package com.rk.xededitor.ui.activities.main

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.rememberNestedScrollInteropConnection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.rk.compose.filetree.DrawerContent
import com.rk.compose.filetree.isLoading
import com.rk.compose.filetree.restoreProjects
import com.rk.resources.strings
import com.rk.xededitor.ui.components.GlobalActions
import com.rk.xededitor.ui.theme.KarbonTheme
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.launch
import java.lang.ref.WeakReference


class MainActivity : ComponentActivity() {
    companion object {
        var isPaused = false
        private var activityRef = WeakReference<MainActivity?>(null)
        var instance: MainActivity?
            get() = activityRef.get()
            private set(value) {
                activityRef = WeakReference(value)
            }
    }

    @OptIn(DelicateCoroutinesApi::class)
    override fun onPause() {
        isPaused = true
        super.onPause()
    }

    override fun onResume() {
        super.onResume()
        isPaused = false
        instance = this
    }

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            val viewModel: MainViewModel = viewModel()

            KarbonTheme {
                val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
                val scope = rememberCoroutineScope()

                ModalNavigationDrawer(
                    modifier = Modifier.imePadding(),
                    drawerState = drawerState,
                    gesturesEnabled = drawerState.isOpen,
                    drawerContent = {
                        val configuration = LocalConfiguration.current
                        ModalDrawerSheet(
                            modifier = Modifier.width((configuration.screenWidthDp * 0.80).dp),
                            windowInsets = WindowInsets(top = 0.dp)
                        ) {
                            LaunchedEffect(Unit) {
                                isLoading = true
                                restoreProjects()
                                isLoading = false
                            }
                            DrawerContent(
                                modifier = Modifier.fillMaxSize().padding(top = 8.dp),
                                onFileSelected = { file ->
                                    scope.launch{
                                        if (file.isFile()) {
                                            viewModel.newEditorTab(file)
                                        }

                                    }

                                    scope.launch { drawerState.close() }
                                }
                            )
                        }
                    }
                ) {
                    Scaffold(modifier = Modifier.nestedScroll(rememberNestedScrollInteropConnection()),
                        topBar = {
                            TopAppBar(
                                title = {},
                                navigationIcon = {
                                    IconButton(
                                        onClick = {
                                            scope.launch {
                                                if (drawerState.isClosed) drawerState.open()
                                                else drawerState.close()
                                            }
                                        }
                                    ) {
                                        Icon(Icons.Outlined.Menu, null)
                                    }
                                },
                                actions = {
                                    this.GlobalActions(viewModel)
                                    viewModel.currentTab?.actions(this)
                                }
                            )
                        }
                    ) { innerPadding ->
                        Column(
                            Modifier
                                .fillMaxSize()
                                .padding(innerPadding)) {
                            if (viewModel.tabs.isEmpty()) {
                                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    TextButton(
                                        onClick = { scope.launch { drawerState.open() } }
                                    ) {
                                        Text(
                                            text = stringResource(strings.open_file_or_folder),
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
                                    edgePadding = 0.dp
                                ) {
                                    viewModel.tabs.forEachIndexed { index, tabState ->
                                        var showTabMenu by remember { mutableStateOf(false) }

                                        Tab(
                                            selected = pagerState.currentPage == index,
                                            onClick = {
                                                if (viewModel.currentTabIndex == index){
                                                    showTabMenu = true
                                                }else{
                                                    viewModel.currentTabIndex = index
                                                }
                                            },
                                            text = {
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    Text(
                                                        text = if (tabState is EditorTab && tabState.editorState.isDirty){"*${tabState.title}"}else{tabState.title},
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
                }
            }
        }
    }
}