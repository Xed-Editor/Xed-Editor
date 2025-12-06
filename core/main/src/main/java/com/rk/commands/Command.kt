package com.rk.commands

import android.app.Activity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.ui.graphics.vector.ImageVector
import com.rk.activities.main.MainViewModel

data class Command(
    val id: String,
    val prefix: String? = null,
    val label: State<String>,
    val action: (MainViewModel, Activity?) -> Unit,
    val isEnabled: State<Boolean>,
    val isSupported: State<Boolean>,
    val icon: State<ImageVector>,
    val childCommands: @Composable () -> List<Command> = { emptyList() },
    val childSearchPlaceholder: String? = null,
    val parentCommand: Command? = null,
    val sectionEndsBelow: Boolean = false,
    val keybinds: String? = null,
)
