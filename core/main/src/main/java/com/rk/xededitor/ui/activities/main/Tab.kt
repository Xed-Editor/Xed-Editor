package com.rk.xededitor.ui.activities.main

import androidx.compose.foundation.layout.RowScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf


abstract class Tab() {
    abstract var title: MutableState<String>
    abstract val content:@Composable ()-> Unit
    abstract val actions:@Composable RowScope.() -> Unit
    abstract fun onTabRemoved()
    abstract fun release()
}