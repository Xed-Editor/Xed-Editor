package com.rk.xededitor.ui.activities.main

import android.app.Activity
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import com.rk.activities.main.MainViewModel

data class Command(
    val id: String,
    val prefix: String? = null,
    val label: String? = null,
    val labelProvider: @Composable ((MainViewModel, Activity?) -> String)? = null,
    val description: String? = null,
    val action: (MainViewModel, Activity?) -> Unit,
    val isEnabled: (MainViewModel, Activity?) -> Boolean = { _, _ -> true },
    val isSupported: (MainViewModel, Activity?) -> Boolean = { _, _ -> true },
    val icon: ImageVector? = null,
    val iconProvider: @Composable ((MainViewModel, Activity?) -> ImageVector)? = null,
    val keybinds: String? = null
) {
    @Composable
    fun getLabel(viewModel: MainViewModel, activity: Activity?): String {
        return label ?: labelProvider?.invoke(viewModel, activity) ?: throw Error("Either label or labelProvider has to be defined.")
    }

    @Composable
    fun getIcon(viewModel: MainViewModel, activity: Activity?): ImageVector? {
        return icon ?: iconProvider?.invoke(viewModel, activity)
    }
}