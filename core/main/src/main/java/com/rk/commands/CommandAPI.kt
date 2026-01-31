package com.rk.commands

import android.app.Activity
import com.rk.activities.main.MainViewModel
import com.rk.editor.Editor
import com.rk.icons.Icon
import com.rk.lsp.BaseLspConnector
import com.rk.tabs.editor.EditorTab

data class CommandContext(val mainViewModel: MainViewModel)

data class ActionContext(val currentActivity: Activity)

data class EditorActionContext(val currentActivity: Activity, val editorTab: EditorTab, val editor: Editor)

data class EditorNonActionContext(val editorTab: EditorTab)

data class LspActionContext(
    val currentActivity: Activity,
    val editorTab: EditorTab,
    val editor: Editor,
    val baseLspConnector: BaseLspConnector?,
)

data class LspNonActionContext(val editorTab: EditorTab, val baseLspConnector: BaseLspConnector)

abstract class Command(val commandContext: CommandContext) {
    abstract val id: String
    open val prefix: String? = null

    abstract fun getLabel(): String

    abstract fun action(actionContext: ActionContext)

    open fun isEnabled(): Boolean = true

    open fun isSupported(): Boolean = true

    abstract fun getIcon(): Icon

    open val childCommands: List<Command> = emptyList()

    open fun getChildSearchPlaceholder(): String? = null

    open val sectionEndsBelow: Boolean = false
    open val defaultKeybinds: KeyCombination? = null

    /** Executes this command's action, or opens a submenu if [childCommands] are present. */
    fun performCommand(actionContext: ActionContext) {
        if (childCommands.isNotEmpty()) {
            commandContext.mainViewModel.showCommandPaletteWithChildren(getChildSearchPlaceholder(), childCommands)
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

    override fun hashCode(): Int = id.hashCode()
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
        return isSupported(EditorNonActionContext(currentTab))
    }

    final override fun isEnabled(): Boolean {
        val currentTab = commandContext.mainViewModel.currentTab
        if (currentTab !is EditorTab) return false
        return isEnabled(EditorNonActionContext(currentTab))
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
        val baseLspConnector = currentTab.baseLspConnector ?: return false
        return isSupported(LspNonActionContext(currentTab, baseLspConnector))
    }

    final override fun isEnabled(editorNonActionContext: EditorNonActionContext): Boolean {
        val currentTab = editorNonActionContext.editorTab
        val baseLspConnector = currentTab.baseLspConnector ?: return false
        return isEnabled(LspNonActionContext(currentTab, baseLspConnector))
    }

    open fun isSupported(lspNonActionContext: LspNonActionContext): Boolean = true

    open fun isEnabled(lspNonActionContext: LspNonActionContext): Boolean = true
}
