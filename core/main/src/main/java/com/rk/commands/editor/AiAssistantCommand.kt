package com.rk.commands.editor

import com.rk.commands.CommandContext
import com.rk.commands.EditorActionContext
import com.rk.commands.EditorCommand
import com.rk.commands.EditorNonActionContext
import com.rk.icons.Icon
import com.rk.resources.drawables
import com.rk.resources.getString
import com.rk.resources.strings
import com.rk.settings.app.InbuiltFeatures

class AiAssistantCommand(commandContext: CommandContext) : EditorCommand(commandContext) {
    override val id: String = "editor.ai_assistant"

    override fun getLabel(): String = "AI Assistant"

    override fun action(editorActionContext: EditorActionContext) {
        commandContext.mainViewModel.showAiSheet = true
    }

    override fun isSupported(editorNonActionContext: EditorNonActionContext): Boolean =
        InbuiltFeatures.terminal.state.value

    override fun getIcon(): Icon = Icon.DrawableRes(drawables.auto_fix)
}

class InlineAskCommand(commandContext: CommandContext) : EditorCommand(commandContext) {
    override val id: String = "editor.inline_ask"

    override fun getLabel(): String = "Ask AI"

    override fun action(editorActionContext: EditorActionContext) {
        val vm = commandContext.mainViewModel
        vm.showAiSheet = false
        vm.showInlineAgent = !vm.showInlineAgent
    }

    override fun isSupported(editorNonActionContext: EditorNonActionContext): Boolean =
        InbuiltFeatures.terminal.state.value

    override fun getIcon(): Icon = Icon.DrawableRes(drawables.auto_fix)
}

class AiTerminalCommand(commandContext: CommandContext) : EditorCommand(commandContext) {
    override val id: String = "editor.ai_terminal"

    override fun getLabel(): String = "AI Terminal"

    override fun action(editorActionContext: EditorActionContext) {
        commandContext.mainViewModel.showAiTerminalSheet = true
    }

    override fun isSupported(editorNonActionContext: EditorNonActionContext): Boolean =
        InbuiltFeatures.terminal.state.value

    override fun getIcon(): Icon = Icon.DrawableRes(drawables.auto_fix)
}
