package com.rk.activities.main

import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material3.DrawerState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import com.rk.components.GlobalActions
import com.rk.components.isPermanentDrawer
import com.rk.resources.strings
import com.rk.terminal.isV
import com.rk.utils.toast
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun XedTopBar(
    modifier: Modifier = Modifier,
    drawerState: DrawerState,
    viewModel: MainViewModel,
    onDrag: (Float) -> Unit = {},
    onDragEnd: () -> Unit = {},
) {
    val scope = rememberCoroutineScope()

    TopAppBar(
        modifier =
            Modifier.pointerInput(Unit) {
                detectVerticalDragGestures(
                    onVerticalDrag = { _, dragAmount -> onDrag(dragAmount) },
                    onDragEnd = { onDragEnd() },
                    onDragCancel = { onDragEnd() },
                )
            },
        title = {},
        navigationIcon = {
            if (!isPermanentDrawer) {
                IconButton(
                    onClick = { scope.launch { if (drawerState.isClosed) drawerState.open() else drawerState.close() } }
                ) {
                    Icon(Icons.Outlined.Menu, null)
                }
            }
        },
        actions = {
            GlobalActions(viewModel)

            if (viewModel.tabs.isNotEmpty()) {

                val tab = if (isV){
                    viewModel.tabs[viewModel.currentTabIndex]
                }else{
                    viewModel.tabs.getOrNull(viewModel.currentTabIndex)
                }


                if (tab != null){
                    tab?.apply { Actions() }
                }else{
                    toast(strings.unknown_error)
                }

            }
        },
    )
}
