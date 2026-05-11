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
    override val id: String = "editor.gemini_assistant"

    override fun getLabel(): String = strings.gemini_assistant.getString()

    override fun action(editorActionContext: EditorActionContext) {
        commandContext.mainViewModel.showAiSheet = true
    }

    override fun isSupported(editorNonActionContext: EditorNonActionContext): Boolean =
        InbuiltFeatures.terminal.state.value

    override fun getIcon(): Icon = Icon.DrawableRes(drawables.auto_fix)
}
