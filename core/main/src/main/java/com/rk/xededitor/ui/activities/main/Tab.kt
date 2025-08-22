package com.rk.xededitor.ui.activities.main

import androidx.compose.foundation.layout.RowScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState


abstract class Tab() {
    abstract var title: MutableState<String>
    abstract fun onTabRemoved()
    abstract fun release()

    @Composable
    abstract fun content()

    @Composable
    abstract fun RowScope.actions()
}