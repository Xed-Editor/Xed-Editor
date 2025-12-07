package com.rk.commands

import android.app.Activity
import androidx.compose.runtime.State
import com.rk.activities.main.MainViewModel

data class Command(
    val id: String,
    val prefix: String? = null,
    val label: State<String>,
    val action: (MainViewModel, Activity?) -> Unit,
    val isEnabled: State<Boolean>,
    val isSupported: State<Boolean>,
    val icon: State<Int>,
    val childCommands: () -> List<Command> = { emptyList() },
    val childSearchPlaceholder: String? = null,
    val sectionEndsBelow: Boolean = false,
    val keybinds: String? = null,
)
