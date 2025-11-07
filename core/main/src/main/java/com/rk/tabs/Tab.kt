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

    /**
     * Can be null if tab is not file-related.
     * */
    open val file: FileObject? = null

    abstract val tabTitle: MutableState<String>
    open fun onTabRemoved() {}

    @Composable
    abstract fun Content()

    @Composable
    open fun RowScope.Actions() {}

    open val showGlobalActions: Boolean = true
}