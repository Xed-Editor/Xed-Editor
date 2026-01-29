package com.rk.activities.main

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DrawerState
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.rememberNestedScrollInteropConnection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.rk.components.ResponsiveDrawer
import com.rk.filetree.DrawerContent
import com.rk.filetree.FileTreeViewModel
import com.rk.filetree.createServices
import com.rk.filetree.isLoading
import com.rk.filetree.restoreProjects
import com.rk.git.GitViewModel
import com.rk.resources.getString
import com.rk.resources.strings
import com.rk.tabs.editor.EditorTab
import com.rk.theme.XedTheme
import com.rk.utils.dialog
import java.lang.ref.WeakReference
import kotlinx.coroutines.launch

var fileTreeViewModel = WeakReference<FileTreeViewModel?>(null)
var gitViewModel = WeakReference<GitViewModel?>(null)
var navigationDrawerState = WeakReference<DrawerState?>(null)

var drawerStateRef: WeakReference<DrawerState?> = WeakReference(null)

@Composable
fun MainActivity.MainContentHost(
    modifier: Modifier = Modifier,
    fileTreeViewModel: FileTreeViewModel = viewModel(),
    gitViewModel: GitViewModel = viewModel(),
) {
    com.rk.activities.main.fileTreeViewModel = WeakReference(fileTreeViewModel)
    com.rk.activities.main.gitViewModel = WeakReference(gitViewModel)

    XedTheme {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
            val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)

            LaunchedEffect(drawerState) { drawerStateRef = WeakReference(drawerState) }

            LaunchedEffect(drawerState.isOpen) {
                if (drawerState.isOpen) {
                    MainActivity.instance?.viewModel?.currentTab?.let {
                        if (it is EditorTab) {
                            it.editorState.editor.get()?.clearFocus()
                        }
                    }
                } else if (drawerState.isClosed) {
                    MainActivity.instance?.viewModel?.currentTab?.let {
                        if (it is EditorTab) {
                            it.editorState.editor.get()?.apply {
                                requestFocus()
                                requestFocusFromTouch()
                            }
                        }
                    }
                }
            }

            navigationDrawerState = WeakReference(drawerState)
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

            val mainContent: @Composable () -> Unit = {
                Scaffold(
                    modifier = Modifier.nestedScroll(rememberNestedScrollInteropConnection()),
                    topBar = {
                        XedTopBar(
                            drawerState = drawerState,
                            viewModel = viewModel,
                            onDrag = { dragAmount ->
                                accumulator += dragAmount

                                viewModel.isDraggingPalette = true

                                scope.launch {
                                    val newProgress = (accumulator / hardThreshold).coerceIn(0f, 1f)
                                    viewModel.draggingPaletteProgress.snapTo(newProgress)
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
                    restoreProjects()
                    createServices()
                    isLoading = false
                }
                DrawerContent(modifier = Modifier.fillMaxSize().padding(top = 8.dp))
            }

            ResponsiveDrawer(drawerState = drawerState, mainContent = mainContent, sheetContent = sheetContent)
        }
    }
}
