package com.rk.tabs

import androidx.compose.foundation.layout.RowScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.ui.graphics.vector.ImageVector
import com.rk.file.FileObject
import kotlin.random.Random.Default.nextInt


abstract class Tab() {
    var refreshKey: Int = nextInt()
    abstract val name: String
    abstract val icon: ImageVector

    abstract var tabTitle: MutableState<String>
    abstract fun onTabRemoved()

    @Composable
    abstract fun Content()

    @Composable
    abstract fun RowScope.Actions()
}