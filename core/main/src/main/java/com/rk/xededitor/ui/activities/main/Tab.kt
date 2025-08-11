package com.rk.xededitor.ui.activities.main

import androidx.compose.foundation.layout.RowScope
import androidx.compose.runtime.Composable

abstract class Tab() {
    abstract val viewPagerId: Int
    abstract val title: String
    abstract val content:@Composable ()-> Unit
    abstract val actions:@Composable RowScope.() -> Unit
    abstract fun release()
}