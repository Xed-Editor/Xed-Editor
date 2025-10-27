package com.rk.components

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.DrawerState
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.PermanentDrawerSheet
import androidx.compose.material3.PermanentNavigationDrawer
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Composable
inline fun getDrawerWidth(): Dp {
    val density = LocalDensity.current
    val widthPx = LocalWindowInfo.current.containerSize.width
    val width = with(density){(widthPx * 0.83f).toDp()}
    return width
}

var isPermanentDrawer by mutableStateOf(false)
    private set

@Composable
fun ResponsiveDrawer(
    drawerState: DrawerState,
    mainContent: @Composable () -> Unit,
    sheetContent: @Composable ColumnScope.() -> Unit
) {
    val screenWidthDp = LocalWindowInfo.current.containerSize.width.dp

    //isPermanentDrawer = remember(screenWidthDp) { screenWidthDp >= 1080.dp }

    if (isPermanentDrawer) {
        PermanentNavigationDrawer(
            content = mainContent,
            modifier = Modifier
                .imePadding()
                .systemBarsPadding(),
            drawerContent = {
                PermanentDrawerSheet(
                    drawerShape = RectangleShape,
                    content = sheetContent
                )
            }
        )
    } else {
        ModalNavigationDrawer(
            modifier = Modifier
                .imePadding()
                .systemBarsPadding(),
            drawerState = drawerState,
            gesturesEnabled = drawerState.isOpen,
            content = mainContent,
            drawerContent = {
                ModalDrawerSheet(
                    modifier = Modifier.width(getDrawerWidth()),
                    drawerShape = RectangleShape,
                    content = sheetContent
                )
            }
        )
    }
}
