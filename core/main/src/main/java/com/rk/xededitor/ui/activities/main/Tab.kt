package com.rk.xededitor.ui.activities.main

import androidx.compose.foundation.layout.RowScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.ui.graphics.vector.ImageVector
import com.rk.file.FileObject


abstract class Tab() {
    abstract val name: String
    abstract val icon: ImageVector

    abstract var tabTitle: MutableState<String>
    abstract fun onTabRemoved()
    abstract fun release()
    abstract fun shouldOpenForFile(fileObject: FileObject): Boolean

    @Composable
    abstract fun content()

    @Composable
    abstract fun RowScope.actions()
}