package com.rk.commands.editor

import com.rk.commands.Command
import com.rk.commands.CommandContext
import com.rk.commands.EditorActionContext
import com.rk.commands.EditorCommand
import com.rk.file.FileType
import com.rk.icons.Icon
import com.rk.resources.drawables
import com.rk.resources.getString
import com.rk.resources.strings

class SyntaxHighlightingCommand(commandContext: CommandContext) : EditorCommand(commandContext) {
    override val id: String = "editor.syntax_highlighting"

    override fun getLabel(): String = strings.highlighting.getString()

    override fun action(editorActionContext: EditorActionContext) {}

    override fun getIcon(): Icon = Icon.DrawableRes(drawables.edit_note)

    override val childCommands: List<Command> by lazy {
        FileType.entries
            .filter { it.textmateScope != null }
            .map { fileType ->
                object : EditorCommand(commandContext) {
                    override val id: String = "editor.syntax_highlighting.${fileType.name.lowercase()}"

                    override fun getLabel(): String = fileType.title

                    override fun action(editorActionContext: EditorActionContext) {
                        editorActionContext.editorTab.editorState.textmateScope = fileType.textmateScope!!
                    }

                    override fun getIcon(): Icon = Icon.DrawableRes(fileType.icon ?: drawables.file)
                }
            }
    }

    override fun getChildSearchPlaceholder(): String = strings.select_language.getString()
}
