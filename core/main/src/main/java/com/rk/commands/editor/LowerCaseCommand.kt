package com.rk.commands.editor

import com.rk.commands.CommandContext
import com.rk.commands.EditorActionContext
import com.rk.commands.EditorCommand
import com.rk.icons.Icon
import com.rk.resources.drawables
import com.rk.resources.getString
import com.rk.resources.strings

class LowerCaseCommand(commandContext: CommandContext) : EditorCommand(commandContext) {
    override val id: String = "editor.lowercase"

    override fun getLabel(): String = strings.transform_lowercase.getString()

    override fun action(editorActionContext: EditorActionContext) {
        val editor = editorActionContext.editor
        if (editor.isTextSelected) {
            val selectionStart = editor.cursorRange.startIndex
            val selectionEnd = editor.cursorRange.endIndex
            val selectionText = editor.text.substring(selectionStart, selectionEnd)
            editor.text.replace(selectionStart, selectionEnd, selectionText.lowercase())
        }
    }

    override fun getIcon(): Icon = Icon.DrawableRes(drawables.letters)
}
