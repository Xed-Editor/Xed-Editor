package com.rk.commands

import android.app.Activity
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import com.rk.activities.main.MainActivity
import com.rk.activities.main.MainViewModel
import com.rk.editor.Editor
import com.rk.icons.Icon
import com.rk.lsp.BaseLspConnector
import com.rk.tabs.editor.EditorTab

/// **
// * Represents an executable command or a submenu within the command palette.
// *
// * @property id Unique identifier for the command.
// * @property label The display text shown in the UI.
// * @property action The logic executed when triggered (unless [childCommands] are present).
// * @property isEnabled Whether this command is enabled (will be greyed out if false).
// * @property isSupported Whether this command is supported (will be hidden or greyed out if false).
// * @property icon The icon displayed in the UI.
// * @property childCommands If provided, selecting this command opens a submenu with these items.
// * @property childSearchPlaceholder The placeholder text for the search field in the submenu of this command.
// * @property sectionEndsBelow If true, draws a divider after this command.
// * @property defaultKeybinds Optional default key combination for this command.
// */
// data class Command(
//    val id: String,
//    val prefix: String? = null,
//    val label: State<String>,
//    val action: (MainViewModel, Activity?) -> Unit,
//    val isEnabled: State<Boolean>,
//    val isSupported: State<Boolean>,
//    val icon: State<Icon>,
//    val childCommands: List<Command> = emptyList(),
//    val childSearchPlaceholder: String? = null,
//    val sectionEndsBelow: Boolean = false,
//    val defaultKeybinds: KeyCombination? = null,
// ) {
//    /** Executes this command's action, or opens a submenu if [childCommands] are present. */
//    fun performCommand(viewModel: MainViewModel, activity: Activity?) {
//        if (childCommands.isNotEmpty()) {
//            viewModel.showCommandPaletteWithChildren(childSearchPlaceholder, childCommands)
//        } else {
//            action(viewModel, activity)
//        }
//    }
// }

data class CommandContext(val mainActivity: MainActivity, val mainViewModel: MainViewModel)

data class ActionContext(val currentActivity: Activity)

data class EditorActionContext(val currentActivity: Activity, val editorTab: EditorTab, val editor: Editor)

data class EditorNonActionContext(val editorTab: EditorTab, val editor: Editor)

data class LspActionContext(
    val currentActivity: Activity,
    val editorTab: EditorTab,
    val editor: Editor,
    val baseLspConnector: BaseLspConnector?,
)

data class LspNonActionContext(val editorTab: EditorTab, val editor: Editor, val baseLspConnector: BaseLspConnector)

abstract class Command(val commandContext: CommandContext) {
    abstract val id: String
    open val prefix: String? = null

    protected abstract fun getLabel(): String

    val label: State<String> = derivedStateOf { getLabel() }

    abstract fun action(actionContext: ActionContext)

    protected open fun isEnabled(): Boolean = true

    val isEnabled: State<Boolean> = derivedStateOf { isEnabled() }

    protected open fun isSupported(): Boolean = true

    val isSupported: State<Boolean> = derivedStateOf { isSupported() }

    protected abstract fun getIcon(): Icon

    val icon: State<Icon> = derivedStateOf { getIcon() }

    open val childCommands: List<Command> = emptyList()

    protected open fun getChildSearchPlaceholder(): String? = null

    val childSearchPlaceholder: State<String?> = derivedStateOf { getChildSearchPlaceholder() }

    open val sectionEndsBelow: Boolean = false
    open val defaultKeybinds: KeyCombination? = null

    /** Executes this command's action, or opens a submenu if [childCommands] are present. */
    fun performCommand(actionContext: ActionContext) {
        if (childCommands.isNotEmpty()) {
            commandContext.mainViewModel.showCommandPaletteWithChildren(childSearchPlaceholder.value, childCommands)
        } else {
            action(actionContext)
        }
    }

    fun copy(
        id: String = this.id,
        prefix: String? = this.prefix,
        label: () -> String = { this.getLabel() },
        action: (ActionContext) -> Unit = { ctx -> this.action(ctx) },
        isEnabled: () -> Boolean = { this.isEnabled() },
        isSupported: () -> Boolean = { this.isSupported() },
        icon: () -> Icon = { this.getIcon() },
        childCommands: List<Command> = this.childCommands,
        childSearchPlaceholder: () -> String? = { this.getChildSearchPlaceholder() },
        sectionEndsBelow: Boolean = this.sectionEndsBelow,
        defaultKeybinds: KeyCombination? = this.defaultKeybinds,
    ): Command {
        return object : Command(commandContext) {
            override val id: String = id

            override val prefix: String? = prefix

            override fun getLabel(): String = label()

            override fun action(actionContext: ActionContext) = action(actionContext)

            override fun isSupported(): Boolean = isSupported()

            override fun isEnabled(): Boolean = isEnabled()

            override val childCommands: List<Command> = childCommands

            override fun getChildSearchPlaceholder(): String? = childSearchPlaceholder()

            override val sectionEndsBelow: Boolean = sectionEndsBelow

            override val defaultKeybinds: KeyCombination? = defaultKeybinds

            override fun getIcon(): Icon = icon()
        }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as Command
        return id == other.id
    }

    override fun hashCode(): Int {
        var result = sectionEndsBelow.hashCode()
        result = 31 * result + commandContext.hashCode()
        result = 31 * result + (prefix?.hashCode() ?: 0)
        result = 31 * result + label.hashCode()
        result = 31 * result + isEnabled.hashCode()
        result = 31 * result + isSupported.hashCode()
        result = 31 * result + icon.hashCode()
        result = 31 * result + childCommands.hashCode()
        result = 31 * result + childSearchPlaceholder.hashCode()
        result = 31 * result + (defaultKeybinds?.hashCode() ?: 0)
        result = 31 * result + id.hashCode()
        return result
    }
}

abstract class GlobalCommand(commandContext: CommandContext) : Command(commandContext)

abstract class EditorCommand(commandContext: CommandContext) : Command(commandContext) {
    final override fun action(actionContext: ActionContext) {
        val currentTab = commandContext.mainViewModel.currentTab
        val editor = (currentTab as? EditorTab)?.editorState?.editor?.get() ?: return
        action(EditorActionContext(actionContext.currentActivity, currentTab, editor))
    }

    abstract fun action(editorActionContext: EditorActionContext)

    final override fun isSupported(): Boolean {
        val currentTab = commandContext.mainViewModel.currentTab
        if (currentTab !is EditorTab) return false

        val editor = currentTab.editorState.editor.get() ?: return false
        return isSupported(EditorNonActionContext(currentTab, editor))
    }

    final override fun isEnabled(): Boolean {
        val currentTab = commandContext.mainViewModel.currentTab
        if (currentTab !is EditorTab) return false

        val editor = currentTab.editorState.editor.get() ?: return false
        return isEnabled(EditorNonActionContext(currentTab, editor))
    }

    open fun isSupported(editorNonActionContext: EditorNonActionContext): Boolean = true

    open fun isEnabled(editorNonActionContext: EditorNonActionContext): Boolean = true
}

abstract class LspCommand(commandContext: CommandContext) : EditorCommand(commandContext) {
    final override fun action(editorActionContext: EditorActionContext) {
        val currentTab = editorActionContext.editorTab
        val editor = editorActionContext.editor
        val baseLspConnector = currentTab.baseLspConnector
        action(LspActionContext(editorActionContext.currentActivity, currentTab, editor, baseLspConnector))
    }

    abstract fun action(lspActionContext: LspActionContext)

    final override fun isSupported(editorNonActionContext: EditorNonActionContext): Boolean {
        val currentTab = editorNonActionContext.editorTab
        val editor = editorNonActionContext.editor
        val baseLspConnector = currentTab.baseLspConnector ?: return false
        return isSupported(LspNonActionContext(currentTab, editor, baseLspConnector))
    }

    final override fun isEnabled(editorNonActionContext: EditorNonActionContext): Boolean {
        val currentTab = editorNonActionContext.editorTab
        val editor = editorNonActionContext.editor
        val baseLspConnector = currentTab.baseLspConnector ?: return false
        return isEnabled(LspNonActionContext(currentTab, editor, baseLspConnector))
    }

    open fun isSupported(lspNonActionContext: LspNonActionContext): Boolean = true

    open fun isEnabled(lspNonActionContext: LspNonActionContext): Boolean = true
}
