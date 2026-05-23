package com.rk.commands.editor

import com.rk.commands.EditorActionContext
import com.rk.commands.EditorCommand
import com.rk.icons.Icon
import com.rk.resources.drawables
import com.rk.resources.getString
import com.rk.resources.strings

class SortLinesAscendingCommand : EditorCommand() {
    override val id = "editor.sort_lines_ascending"

    override fun getLabel() = strings.sort_lines_ascending.getString()

    override fun action(editorActionContext: EditorActionContext) {
        val editor = editorActionContext.editor

        val cursor = editor.cursor

        var startLine: Int
        var endLine: Int
        if (!cursor.isSelected) {
            startLine = 0
            endLine = editor.text.lineCount - 1
        } else {
            startLine = minOf(cursor.leftLine, cursor.rightLine)
            endLine = maxOf(cursor.leftLine, cursor.rightLine)
        }
        val endLineColumn = editor.text.getColumnCount(endLine)

        val lines = editor.text.subContent(startLine, 0, endLine, endLineColumn).lines()
        val ascendingLines = lines.sorted().joinToString("\n")

        editor.text.replace(startLine, 0, endLine, endLineColumn, ascendingLines)
    }

    override fun getIcon() = Icon.ResourceIcon(drawables.sort_by_alphabet)
}
