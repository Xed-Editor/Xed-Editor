package com.rk.commands

import android.app.Activity
import androidx.compose.runtime.State
import com.rk.activities.main.MainViewModel

/**
 * Represents an executable command or a submenu within the command palette.
 *
 * @property id Unique identifier for the command.
 * @property label The display text shown in the UI.
 * @property action The logic executed when triggered (unless [childCommands] are present).
 * @property childCommands If provided, selecting this command opens a submenu with these items.
 * @property sectionEndsBelow If true, draws a divider after this command.
 * @property keybinds Optional shortcut string (e.g., "Ctrl+S").
 */
data class Command(
    val id: String,
    val prefix: String? = null,
    val label: State<String>,
    val action: (MainViewModel, Activity?) -> Unit,
    val isEnabled: State<Boolean>,
    val isSupported: State<Boolean>,
    val icon: State<Int>,
    val childCommands: List<Command> = emptyList(),
    val childSearchPlaceholder: String? = null,
    val sectionEndsBelow: Boolean = false,
    val keybinds: String? = null,
) {
    /** Executes this command's action, or opens a submenu if [childCommands] are present. */
    fun performCommand(viewModel: MainViewModel, activity: Activity?) {
        if (childCommands.isNotEmpty()) {
            viewModel.showCommandPaletteWithChildren(childSearchPlaceholder, childCommands)
        } else {
            action(viewModel, activity)
        }
    }
}
