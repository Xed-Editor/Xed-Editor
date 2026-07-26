package com.rk.commands

import android.app.Activity
import com.rk.activities.main.MainActivity
import com.rk.activities.main.MainViewModel
import com.rk.drawer.DrawerViewModel
import com.rk.editor.Editor
import com.rk.file.FileObject
import com.rk.icons.Icon
import com.rk.lsp.LspConnector
import com.rk.tabs.editor.EditorTab

data class CommandContext(
    private val mainViewModelProvider: () -> MainViewModel,
    private val drawerViewModelProvider: () -> DrawerViewModel,
) {
    val mainViewModel: MainViewModel
        get() = mainViewModelProvider()

    val drawerViewModel: DrawerViewModel
        get() = drawerViewModelProvider()
}

open class ActionContext(open val currentActivity: Activity)

open class EditorActionContext(
    override val currentActivity: Activity,
    open val editorTab: EditorTab,
    open val editor: Editor,
) : ActionContext(currentActivity)

open class EditorNonActionContext(open val editorTab: EditorTab)

data class EditorFileActionContext(
    override val currentActivity: Activity,
    override val editorTab: EditorTab,
    override val editor: Editor,
    val file: FileObject,
) :
    EditorActionContext(
        currentActivity,
        editorTab,
        editor,
    )

data class EditorFileNonActionContext(
    override val editorTab: EditorTab,
    val file: FileObject,
) : EditorNonActionContext(editorTab)

data class LspActionContext(
    override val currentActivity: Activity,
    override val editorTab: EditorTab,
    override val editor: Editor,
    val lspConnector: LspConnector,
) :
    EditorActionContext(
        currentActivity,
        editorTab,
        editor,
    )

data class LspNonActionContext(
    override val editorTab: EditorTab,
    val lspConnector: LspConnector,
) : EditorNonActionContext(editorTab)

abstract class Command {
    protected val commandContext =
        CommandContext(
            mainViewModelProvider = { MainActivity.instance!!.viewModel },
            drawerViewModelProvider = { MainActivity.instance!!.drawerViewModel },
        )
    abstract val id: String
    open val prefix: String? = null

    abstract fun getLabel(): String

    abstract fun getIcon(): Icon

    abstract fun action(context: ActionContext)

    open fun isEnabled(): Boolean = true

    open fun isSupported(): Boolean = true

    open val preferText: Boolean = false

    open val childCommands: List<Command> = emptyList()

    open fun getChildSearchPlaceholder(): String? = null

    open val sectionId: Int = 0
    open val defaultKeybinds: KeyCombination? = null

    open val repeatOnHold: Boolean = false

    open fun onLongClick(context: ActionContext): Boolean = false

    /** Executes this command's action, or opens a submenu if [childCommands] are present. */
    fun performCommand(context: ActionContext) {
        if (childCommands.isNotEmpty()) {
            commandContext.mainViewModel.showCommandPaletteWithChildren(getChildSearchPlaceholder(), childCommands)
        } else {
            action(context)
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
        preferText: Boolean = this.preferText,
        childCommands: List<Command> = this.childCommands,
        childSearchPlaceholder: () -> String? = { this.getChildSearchPlaceholder() },
        sectionId: Int = this.sectionId,
        defaultKeybinds: KeyCombination? = this.defaultKeybinds,
        repeatOnHold: Boolean = this.repeatOnHold,
        onLongClick: (ActionContext) -> Boolean = { ctx -> this.onLongClick(ctx) },
    ): Command {
        return object : Command() {
            override val id: String = id

            override val prefix: String? = prefix

            override fun getLabel(): String = label()

            override fun action(context: ActionContext) = action(context)

            override fun isEnabled(): Boolean = isEnabled()

            override fun isSupported(): Boolean = isSupported()

            override fun getIcon(): Icon = icon()

            override val preferText: Boolean = preferText

            override val childCommands: List<Command> = childCommands

            override fun getChildSearchPlaceholder(): String? = childSearchPlaceholder()

            override val sectionId: Int = sectionId

            override val defaultKeybinds: KeyCombination? = defaultKeybinds

            override val repeatOnHold: Boolean = repeatOnHold

            override fun onLongClick(context: ActionContext): Boolean = onLongClick(context)
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

interface ToggleableCommand {
    fun isOn(): Boolean
}

abstract class GlobalCommand : Command()

abstract class EditorCommand : Command() {
    final override fun action(context: ActionContext) {
        val currentTab = commandContext.mainViewModel.currentTab
        val editor = (currentTab as? EditorTab)?.editorState?.editor?.get() ?: return
        action(EditorActionContext(context.currentActivity, currentTab, editor))
    }

    abstract fun action(context: EditorActionContext)

    final override fun onLongClick(context: ActionContext): Boolean {
        val currentTab = commandContext.mainViewModel.currentTab
        val editor = (currentTab as? EditorTab)?.editorState?.editor?.get() ?: return false
        return onLongClick(EditorActionContext(context.currentActivity, currentTab, editor))
    }

    open fun onLongClick(context: EditorActionContext): Boolean = false

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

    open fun isSupported(context: EditorNonActionContext): Boolean = true

    open fun isEnabled(context: EditorNonActionContext): Boolean = true
}

abstract class EditorFileCommand : EditorCommand() {
    final override fun action(context: EditorActionContext) {
        val currentTab = context.editorTab
        val editor = context.editor
        val file = currentTab.file ?: return
        action(EditorFileActionContext(context.currentActivity, currentTab, editor, file))
    }

    abstract fun action(context: EditorFileActionContext)

    final override fun onLongClick(context: EditorActionContext): Boolean {
        val currentTab = context.editorTab
        val editor = context.editor
        val file = currentTab.file ?: return false
        return onLongClick(EditorFileActionContext(context.currentActivity, currentTab, editor, file))
    }

    open fun onLongClick(context: EditorFileActionContext): Boolean = false

    final override fun isSupported(context: EditorNonActionContext): Boolean {
        val currentTab = context.editorTab
        val file = currentTab.file ?: return false
        return isSupported(EditorFileNonActionContext(currentTab, file))
    }

    final override fun isEnabled(context: EditorNonActionContext): Boolean {
        val currentTab = context.editorTab
        val file = currentTab.file ?: return false
        return isEnabled(EditorFileNonActionContext(currentTab, file))
    }

    open fun isSupported(context: EditorFileNonActionContext): Boolean = true

    open fun isEnabled(context: EditorFileNonActionContext): Boolean = true
}

abstract class LspCommand : EditorCommand() {
    final override fun action(context: EditorActionContext) {
        val currentTab = context.editorTab
        val editor = context.editor
        val baseLspConnector = currentTab.lspConnector ?: return
        action(LspActionContext(context.currentActivity, currentTab, editor, baseLspConnector))
    }

    abstract fun action(context: LspActionContext)

    final override fun onLongClick(context: EditorActionContext): Boolean {
        val currentTab = context.editorTab
        val editor = context.editor
        val baseLspConnector = currentTab.lspConnector ?: return false
        return onLongClick(LspActionContext(context.currentActivity, currentTab, editor, baseLspConnector))
    }

    open fun onLongClick(context: LspActionContext): Boolean = false

    final override fun isSupported(context: EditorNonActionContext): Boolean {
        val currentTab = context.editorTab
        val baseLspConnector = currentTab.lspConnector ?: return false
        return isSupported(LspNonActionContext(currentTab, baseLspConnector))
    }

    final override fun isEnabled(context: EditorNonActionContext): Boolean {
        val currentTab = context.editorTab
        val baseLspConnector = currentTab.lspConnector ?: return false
        return isEnabled(LspNonActionContext(currentTab, baseLspConnector))
    }

    open fun isSupported(context: LspNonActionContext): Boolean = true

    open fun isEnabled(context: LspNonActionContext): Boolean = true
}
