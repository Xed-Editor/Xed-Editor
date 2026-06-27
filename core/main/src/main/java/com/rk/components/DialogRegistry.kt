package com.rk.components

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.Composable

object DialogRegistry {
    val dialogs = mutableStateListOf<@Composable () -> Unit>()
}
