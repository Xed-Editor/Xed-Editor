package com.rk.components

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.DrawerDefaults
import androidx.compose.material3.DrawerState
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.PermanentDrawerSheet
import androidx.compose.material3.PermanentNavigationDrawer
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.rk.settings.Settings

@Composable
inline fun getDrawerWidth(): Dp {
    val density = LocalDensity.current
    val widthPx = LocalWindowInfo.current.containerSize.width
    val width = with(density) { (widthPx * 0.78f).toDp().coerceIn(280.dp, 380.dp) }
    return width
}

var isPermanentDrawer by mutableStateOf(false)
    private set

@Composable
fun ResponsiveDrawer(
    drawerState: DrawerState,
    fullscreen: Boolean,
    mainContent: @Composable () -> Unit,
    sheetContent: @Composable ColumnScope.() -> Unit,
) {
    if (Settings.desktop_mode) {
        val screenWidthDp = LocalWindowInfo.current.containerSize.width.dp
        isPermanentDrawer = remember(screenWidthDp) { screenWidthDp >= 1080.dp }
    }

    if (isPermanentDrawer) {
        PermanentNavigationDrawer(
            content = mainContent,
            modifier = Modifier.imePadding(),
            drawerContent = {
                PermanentDrawerSheet(
                    windowInsets = if (fullscreen) WindowInsets() else DrawerDefaults.windowInsets,
                    drawerShape = RectangleShape,
                    modifier = Modifier.width(320.dp),
                    content = sheetContent,
                )
            },
        )
    } else {
        ModalNavigationDrawer(
            modifier = Modifier.imePadding(),
            drawerState = drawerState,
            gesturesEnabled = drawerState.isOpen,
            content = mainContent,
            drawerContent = {
                ModalDrawerSheet(
                    windowInsets = if (fullscreen) WindowInsets() else DrawerDefaults.windowInsets,
                    modifier = Modifier.width(getDrawerWidth()),
                    drawerShape = RectangleShape,
                    content = sheetContent,
                )
            },
        )
    }
}
