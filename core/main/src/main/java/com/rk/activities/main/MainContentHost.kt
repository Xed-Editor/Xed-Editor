package com.rk.activities.main

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.PermanentDrawerSheet
import androidx.compose.material3.PermanentNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.*
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.rememberNestedScrollInteropConnection
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.rk.components.ResponsiveDrawer
import com.rk.components.getDrawerWidth
import com.rk.filetree.DrawerContent
import com.rk.filetree.isLoading
import com.rk.filetree.restoreProjects
import com.rk.resources.getString
import com.rk.resources.strings
import com.rk.settings.Settings
import com.rk.theme.XedTheme
import com.rk.utils.dialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun MainActivity.MainContentHost(modifier: Modifier = Modifier) {
    XedTheme {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.surface
        ) {
            val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
            val scope = rememberCoroutineScope()

            BackHandler {
                if (drawerState.isOpen) {
                    scope.launch {
                        drawerState.close()
                    }
                }else if (viewModel.tabs.isNotEmpty()){
                    dialog(title = strings.attention.getString(), msg = strings.confirm_exit.getString(), onCancel = {}, onOk = {
                        finish()
                    }, okString = strings.exit)
                }else{
                    finish()
                }

            }


            val mainContent: @Composable ()-> Unit = {
                Scaffold(
                    modifier = Modifier.nestedScroll(
                        rememberNestedScrollInteropConnection()
                    ),
                    topBar = {
                        XedTopBar(drawerState = drawerState, viewModel = viewModel)
                    }
                ) { innerPadding ->
                    MainContent(
                        innerPadding = innerPadding,
                        drawerState = drawerState,
                        viewModel = viewModel
                    )
                }
            }

            val sheetContent: @Composable ColumnScope.()-> Unit = {
                LaunchedEffect(Unit) {
                    isLoading = true
                    restoreProjects()
                    isLoading = false
                }
                DrawerContent(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(top = 8.dp),
                    onFileSelected = { file ->
                        scope.launch(Dispatchers.IO) {
                            if (file.isFile()) {
                                viewModel.newTab(file, switchToTab = true)
                            }

                            delay(60)
                            if (Settings.keep_drawer_locked.not()){
                                drawerState.close()
                            }

                        }
                    }
                )
            }


            ResponsiveDrawer(
                drawerState = drawerState,
                mainContent = mainContent,
                sheetContent = sheetContent
            )

        }

    }
}