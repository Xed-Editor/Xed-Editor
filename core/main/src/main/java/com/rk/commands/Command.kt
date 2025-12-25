package com.rk.commands

import android.app.Activity
import androidx.compose.runtime.State
import com.rk.activities.main.MainViewModel
import com.rk.icons.Icon

/**
 * Represents an executable command or a submenu within the command palette.
 *
 * @property id Unique identifier for the command.
 * @property label The display text shown in the UI.
 * @property action The logic executed when triggered (unless [childCommands] are present).
 * @property isEnabled Whether this command is enabled (will be greyed out if false).
 * @property isSupported Whether this command is supported (will be hidden or greyed out if false).
 * @property icon The icon displayed in the UI.
 * @property childCommands If provided, selecting this command opens a submenu with these items.
 * @property childSearchPlaceholder The placeholder text for the search field in the submenu of this command.
 * @property sectionEndsBelow If true, draws a divider after this command.
 * @property defaultKeybinds Optional default key combination for this command.
 */
data class Command(
    val id: String,
    val prefix: String? = null,
    val label: State<String>,
    val action: (MainViewModel, Activity?) -> Unit,
    val isEnabled: State<Boolean>,
    val isSupported: State<Boolean>,
    val icon: State<Icon>,
    val childCommands: List<Command> = emptyList(),
    val childSearchPlaceholder: String? = null,
    val sectionEndsBelow: Boolean = false,
    val defaultKeybinds: KeyCombination? = null,
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
