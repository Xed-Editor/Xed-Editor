package com.rk.xededitor.ui.activities.main

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
import com.rk.xededitor.ui.components.GlobalActions
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun XedTopBar(modifier: Modifier = Modifier,drawerState: DrawerState, viewModel: MainViewModel) {
    val scope = rememberCoroutineScope()
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
            GlobalActions(viewModel)

            if (viewModel.tabs.isNotEmpty()){
                viewModel.tabs[viewModel.currentTabIndex].apply {
                    Actions()
                }
            }


        }
    )
}