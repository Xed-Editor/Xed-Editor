package com.rk.activities.main

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DrawerState
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScaffoldDefaults
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.rememberNestedScrollInteropConnection
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.rk.components.ResponsiveDrawer
import com.rk.filetree.DrawerContent
import com.rk.filetree.DrawerPersistence
import com.rk.filetree.FileTreeViewModel
import com.rk.filetree.createServices
import com.rk.filetree.isLoading
import com.rk.git.GitViewModel
import com.rk.resources.getString
import com.rk.resources.strings
import com.rk.search.SearchViewModel
import com.rk.settings.ReactiveSettings
import com.rk.settings.Settings
import com.rk.tabs.editor.EditorTab
import com.rk.theme.XedTheme
import com.rk.utils.dialog
import java.lang.ref.WeakReference
import kotlinx.coroutines.launch

var fileTreeViewModel = WeakReference<FileTreeViewModel?>(null)
var gitViewModel = WeakReference<GitViewModel?>(null)
var searchViewModel = WeakReference<SearchViewModel?>(null)

var snackbarHostStateRef: WeakReference<SnackbarHostState?> = WeakReference(null)
var drawerStateRef: WeakReference<DrawerState?> = WeakReference(null)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MainActivity.MainContentHost(
    gitViewModel: GitViewModel = viewModel(),
    fileTreeViewModel: FileTreeViewModel = viewModel(),
    searchViewModel: SearchViewModel = viewModel(),
) {
    com.rk.activities.main.fileTreeViewModel = WeakReference(fileTreeViewModel)
    com.rk.activities.main.gitViewModel = WeakReference(gitViewModel)
    com.rk.activities.main.searchViewModel = WeakReference(searchViewModel)

    XedTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
            val snackbarHostState = remember { SnackbarHostState() }

            LaunchedEffect(drawerState) { drawerStateRef = WeakReference(drawerState) }
            LaunchedEffect(snackbarHostState) { snackbarHostStateRef = WeakReference(snackbarHostState) }

            LaunchedEffect(drawerState.isOpen) {
                val viewModel = MainActivity.instance?.viewModel ?: return@LaunchedEffect
                if (drawerState.isOpen) {
                    viewModel.tabManager.currentTab?.let {
                        if (it is EditorTab) {
                            it.editorState.editor.get()?.clearFocus()
                        }
                    }
                } else if (drawerState.isClosed) {
                    viewModel.tabManager.currentTab?.let {
                        if (it is EditorTab) {
                            it.editorState.editor.get()?.apply {
                                requestFocus()
                                requestFocusFromTouch()
                            }
                        }
                    }
                }
            }

            LaunchedEffect(ReactiveSettings.fullscreen) {
                val controller = WindowCompat.getInsetsController(window, window.decorView)
                val statusBarType = WindowInsetsCompat.Type.statusBars()
                if (ReactiveSettings.fullscreen) {
                    controller.hide(statusBarType)
                } else {
                    controller.show(statusBarType)
                }
            }

            val keyboardShown = WindowInsets.isImeVisible
            LaunchedEffect(keyboardShown, ReactiveSettings.smartToolbar) {
                viewModel.showTopBar = !ReactiveSettings.smartToolbar || !keyboardShown
            }

            val scope = rememberCoroutineScope()

            BackHandler {
                if (drawerState.isOpen) {
                    scope.launch { drawerState.close() }
                } else if (viewModel.tabs.isNotEmpty()) {
                    dialog(
                        title = strings.attention.getString(),
                        msg = strings.confirm_exit.getString(),
                        onCancel = {},
                        onOk = { finish() },
                        okString = strings.exit,
                    )
                } else {
                    finish()
                }
            }

            val density = LocalDensity.current
            var accumulator = 0f
            val softThreshold = with(density) { 50.dp.toPx() }
            val hardThreshold = with(density) { 100.dp.toPx() }

            val snackbarBottomPadding =
                if (Settings.show_extra_keys) {
                    if (Settings.split_extra_keys) 88.dp else 48.dp
                } else 0.dp

            val mainContent: @Composable () -> Unit = {
                Scaffold(
                    contentWindowInsets =
                        if (ReactiveSettings.fullscreen) WindowInsets() else ScaffoldDefaults.contentWindowInsets,
                    snackbarHost = {
                        SnackbarHost(
                            hostState = snackbarHostState,
                            modifier = Modifier.padding(bottom = snackbarBottomPadding),
                        )
                    },
                    modifier = Modifier.nestedScroll(rememberNestedScrollInteropConnection()),
                    topBar = {
                        XedTopBar(
                            drawerState = drawerState,
                            viewModel = viewModel,
                            fullScreen = ReactiveSettings.fullscreen,
                            onDrag = { dragAmount ->
                                accumulator += dragAmount

                                viewModel.isDraggingPalette = true

                                scope.launch {
                                    val newProgress = (accumulator / hardThreshold).coerceIn(0f, 1f)
                                    viewModel.draggingPaletteProgress.snapTo(newProgress) // TODO
                                }
                            },
                            onDragEnd = {
                                val shouldOpen = accumulator >= softThreshold
                                scope.launch {
                                    viewModel.isDraggingPalette = shouldOpen
                                    viewModel.draggingPaletteProgress.animateTo(
                                        if (shouldOpen) 1f else 0f,
                                        animationSpec = spring(stiffness = 800f),
                                    )
                                }
                                accumulator = 0f
                            },
                        )
                    },
                ) { innerPadding ->
                    MainContent(
                        innerPadding = innerPadding,
                        drawerState = drawerState,
                        mainViewModel = viewModel,
                        fileTreeViewModel = fileTreeViewModel,
                    )
                }
            }

            val sheetContent: @Composable ColumnScope.() -> Unit = {
                LaunchedEffect(Unit) {
                    isLoading = true
                    DrawerPersistence.restoreState()
                    createServices()
                    isLoading = false
                }
                DrawerContent(ReactiveSettings.fullscreen)
            }

            ResponsiveDrawer(
                drawerState = drawerState,
                fullscreen = ReactiveSettings.fullscreen,
                mainContent = mainContent,
                sheetContent = sheetContent,
            )
        }
    }
}
