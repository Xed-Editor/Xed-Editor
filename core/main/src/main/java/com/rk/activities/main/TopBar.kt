package com.rk.activities.main

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material3.DrawerState
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.rk.components.GlobalToolbarActions
import com.rk.components.isPermanentDrawer
import com.rk.resources.strings
import com.rk.terminal.isV
import com.rk.theme.DesignTokens
import com.rk.utils.toast
import kotlinx.coroutines.launch

private val DRAG_STRIP_HEIGHT = 10.dp

@Composable
fun XedTopBar(
    drawerState: DrawerState,
    viewModel: MainViewModel,
    fullScreen: Boolean,
    onDrag: (Float) -> Unit = {},
    onDragEnd: () -> Unit = {},
) {
    val scope = rememberCoroutineScope()

    AnimatedVisibility(visible = viewModel.showTopBar, enter = expandVertically(), exit = shrinkVertically()) {
        Surface(
            tonalElevation = DesignTokens.Elevation.none,
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding(),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(DesignTokens.TopBarSize.compactHeight)
                        .padding(horizontal = 4.dp),
                ) {
                    if (!isPermanentDrawer) {
                        IconButton(
                            onClick = {
                                scope.launch { if (drawerState.isClosed) drawerState.open() else drawerState.close() }
                            },
                        ) {
                            Icon(
                                Icons.Outlined.Menu,
                                contentDescription = "Open navigation drawer",
                                modifier = Modifier.size(22.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }

                    Spacer(Modifier.width(2.dp))

                    GlobalToolbarActions(viewModel)

                    Spacer(Modifier.weight(1f))

                    if (viewModel.tabs.isNotEmpty()) {
                        val tab =
                            if (isV) {
                                viewModel.tabs[viewModel.currentTabIndex]
                            } else {
                                viewModel.tabs.getOrNull(viewModel.currentTabIndex)
                            }

                        if (tab != null) {
                            tab.apply { Actions() }
                        } else {
                            toast(strings.unknown_error)
                        }
                    }
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(DRAG_STRIP_HEIGHT)
                        .align(Alignment.TopCenter)
                        .pointerInput(Unit) {
                            detectVerticalDragGestures(
                                onVerticalDrag = { _, dragAmount -> onDrag(dragAmount) },
                                onDragEnd = { onDragEnd() },
                                onDragCancel = { onDragEnd() },
                            )
                        },
                )
            }
        }
    }
}
