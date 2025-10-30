package com.rk.commands

import android.app.Activity
import androidx.compose.runtime.State
import androidx.compose.ui.graphics.vector.ImageVector
import com.rk.activities.main.MainViewModel

data class Command(
    val id: String,
    val prefix: String? = null,
    val label: State<String>,
    val description: String? = null,
    val action: (MainViewModel, Activity?) -> Unit,
    val isEnabled: State<Boolean>,
    val isSupported: State<Boolean>,
    val icon: State<ImageVector>,
    val keybinds: String? = null
)